package uz.ithunter.crm.user;

/** Mirrors {@code ck_app_user_status}. {@code BLOCKED}/{@code DISABLED} are rejected at the JWT filter (spec 16.3). */
public enum UserStatus {
    ACTIVE, BLOCKED, DISABLED
}
