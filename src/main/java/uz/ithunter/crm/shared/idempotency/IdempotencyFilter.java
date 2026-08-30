package uz.ithunter.crm.shared.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import uz.ithunter.crm.auth.CustomUserPrincipal;
import uz.ithunter.crm.shared.exception.ErrorResponseWriter;

/**
 * Implements the {@code command_log} contract documented in V11's own header comment: a mutating
 * request may carry an {@code Idempotency-Key} header. First call runs the request normally and,
 * on a successful (2xx) response, stores it. A replay with the same key AND the same request body
 * returns the stored response without re-running the business logic. A replay with the same key
 * but a DIFFERENT body is a client bug -> {@code 409 IDEMPOTENCY_KEY_REUSED}.
 *
 * <p>Runs after {@link uz.ithunter.crm.auth.JwtAuthenticationFilter} in the security filter chain
 * (wired in {@code SecurityConfig}) so the authenticated principal is available to attribute the
 * command_log row to a user. Only engages when the header is present, so every other endpoint is
 * completely unaffected.
 *
 * <p>This is a best-effort claim-then-store, not a single atomic transaction spanning the whole
 * request: {@code command_log}'s own unique constraint is the real backstop against a genuine
 * simultaneous double-submit, exactly as WORKFLOW_ENGINE_DESIGN.md 11 frames it ("domain-level
 * idempotency already comes from state checks + unique constraints ... this table closes the
 * remaining hole"). TEST_MATRIX.md C-05 is a sequential replay, not a race, so this is sufficient.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final Pattern CASE_ID_PATTERN =
            Pattern.compile("/cases/([0-9a-fA-F]{8}-[0-9a-fA-F-]{27})");
    private static final String NULL_BODY_SENTINEL = "null";

    private final CommandLogRepository commandLogRepository;
    private final ErrorResponseWriter errorResponseWriter;

    public IdempotencyFilter(CommandLogRepository commandLogRepository, ErrorResponseWriter errorResponseWriter) {
        this.commandLogRepository = commandLogRepository;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idempotencyKey = request.getHeader(HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank() || !MUTATING_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = sha256Hex(cachedRequest);

        Optional<CommandLog> existing = commandLogRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            replay(existing.get(), requestHash, response, request.getRequestURI());
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        chain.doFilter(cachedRequest, wrappedResponse);
        recordIfSuccessful(idempotencyKey, request, wrappedResponse, requestHash);
        wrappedResponse.copyBodyToResponse();
    }

    private void replay(CommandLog stored, String requestHash, HttpServletResponse response, String path)
            throws IOException {
        if (!stored.getRequestHash().equals(requestHash)) {
            errorResponseWriter.write(response, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request", path);
            return;
        }
        response.setStatus(stored.getResponseStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (!NULL_BODY_SENTINEL.equals(stored.getResponseBody())) {
            response.getWriter().write(stored.getResponseBody());
        }
    }

    // No @Transactional here: by the time chain.doFilter() has returned, the controller's own
    // service-layer transaction has already committed (filters wrap OUTSIDE DispatcherServlet), so
    // this insert is unavoidably a separate transaction regardless. SimpleJpaRepository.save()
    // already opens its own transaction for a lone call, and a self-invoked @Transactional method
    // on this same bean would silently bypass the AOP proxy anyway - not worth the false safety.
    private void recordIfSuccessful(String idempotencyKey, HttpServletRequest request,
            ContentCachingResponseWrapper wrappedResponse, String requestHash) {
        int status = wrappedResponse.getStatus();
        if (status < 200 || status >= 300) {
            return;
        }
        byte[] body = wrappedResponse.getContentAsByteArray();
        CommandLog entry = new CommandLog();
        entry.setIdempotencyKey(idempotencyKey);
        entry.setEndpoint(request.getMethod() + " " + request.getRequestURI());
        entry.setUserId(currentUserId());
        entry.setCaseId(extractCaseId(request.getRequestURI()));
        entry.setRequestHash(requestHash);
        entry.setResponseStatus(status);
        entry.setResponseBody(body.length == 0 ? NULL_BODY_SENTINEL : new String(body, StandardCharsets.UTF_8));
        try {
            commandLogRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException raceOnSameKey) {
            // Another request claimed this key first (genuine simultaneous double-submit, not
            // covered by C-05). Our own response was already computed and will still be sent to
            // this caller; only the bookkeeping row is skipped.
            log.debug("command_log insert lost a race on idempotency_key={}", idempotencyKey);
        }
    }

    private UUID currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private UUID extractCaseId(String uri) {
        Matcher matcher = CASE_ID_PATTERN.matcher(uri);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException notAUuid) {
                return null;
            }
        }
        return null;
    }

    private String sha256Hex(CachedBodyHttpServletRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(request.getCachedBody());
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available on any standard JVM", impossible);
        }
    }
}
