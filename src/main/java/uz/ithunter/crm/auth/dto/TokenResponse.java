package uz.ithunter.crm.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserSummary user) {
}
