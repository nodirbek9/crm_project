package uz.ithunter.crm.shared.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StreamUtils;

/**
 * Buffers the whole request body once so it can be read twice: once by
 * {@link IdempotencyFilter} to compute the request hash, and once more, normally, by the
 * controller's message converter. Spring's built-in {@code ContentCachingRequestWrapper} cannot do
 * this - it only caches bytes as something else reads them, so whichever reader goes first leaves
 * nothing for the second (a well-known gotcha, not a bug in this class's design).
 */
class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        private CachedBodyServletInputStream(byte[] body) {
            this.buffer = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Only relevant for async servlet I/O, which this synchronous MVC stack never uses.
        }

        @Override
        public int read() {
            return buffer.read();
        }
    }
}
