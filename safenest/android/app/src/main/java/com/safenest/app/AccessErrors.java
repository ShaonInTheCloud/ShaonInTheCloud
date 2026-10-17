package com.safenest.app;

import java.util.Locale;

/**
 * Turns a sign-in or access-check failure into one reason key. Pure Java so the
 * mapping is covered by run-core-tests.sh. The screen turns keys into text.
 */
public final class AccessErrors {
    private AccessErrors() {}

    public static final String WRONG_PASSWORD = "wrong_password";
    public static final String EMAIL_NOT_CONFIRMED = "email_not_confirmed";
    public static final String TOO_MANY = "too_many";
    public static final String OFFLINE = "offline";
    public static final String SERVER = "server";
    public static final String INVALID_PLAN = "invalid_plan";
    public static final String SIGN_IN_FAILED = "sign_in_failed";

    /** status: HTTP status, or -1 when the request never got a reply. errorCode: server error field, may be null. */
    public static String reason(int status, String errorCode) {
        if (status < 0) return OFFLINE;
        String code = errorCode == null ? "" : errorCode.trim().toLowerCase(Locale.ROOT);
        switch (code) {
            case "invalid_credentials":
            case "invalid_grant":
            case "invalid login credentials":
                return WRONG_PASSWORD;
            case "email_not_confirmed":
            case "confirmed_account_required":
                return EMAIL_NOT_CONFIRMED;
            case "over_request_rate_limit":
            case "over_email_send_rate_limit":
                return TOO_MANY;
            case "invalid_plan":
                return INVALID_PLAN;
            case "trial_unavailable":
                return SERVER;
            // Google Play verification outcomes the screen explains individually.
            case "purchase_bound_to_other_account":
            case "purchase_not_found":
            case "invalid_purchase_token":
            case "test_purchase_not_allowed":
            case "unknown_play_plan":
                return code;
            default:
                break;
        }
        if (status == 429) return TOO_MANY;
        if (status >= 500) return SERVER;
        if (status == 400 || status == 401 || status == 403) return SIGN_IN_FAILED;
        return SERVER;
    }
}
