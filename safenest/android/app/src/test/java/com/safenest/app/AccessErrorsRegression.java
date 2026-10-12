package com.safenest.app;

/** Standalone checks; no Android classes. Run with run-core-tests.sh. */
public final class AccessErrorsRegression {
    private AccessErrorsRegression() {}

    private static void same(String expected, String actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + " got " + actual);
    }

    public static void runAll() {
        // Supabase Auth, newer and older shapes.
        same(AccessErrors.WRONG_PASSWORD, AccessErrors.reason(400, "invalid_credentials"), "invalid_credentials");
        same(AccessErrors.WRONG_PASSWORD, AccessErrors.reason(400, "invalid_grant"), "invalid_grant");
        same(AccessErrors.WRONG_PASSWORD, AccessErrors.reason(400, "Invalid login credentials"), "msg text");
        same(AccessErrors.EMAIL_NOT_CONFIRMED, AccessErrors.reason(400, "email_not_confirmed"), "unconfirmed");
        same(AccessErrors.TOO_MANY, AccessErrors.reason(429, "over_request_rate_limit"), "rate limit code");
        same(AccessErrors.TOO_MANY, AccessErrors.reason(429, null), "429 without code");
        // SafeNest functions.
        same(AccessErrors.EMAIL_NOT_CONFIRMED, AccessErrors.reason(403, "confirmed_account_required"), "start-trial unconfirmed");
        same(AccessErrors.INVALID_PLAN, AccessErrors.reason(400, "invalid_plan"), "invalid plan");
        same(AccessErrors.SERVER, AccessErrors.reason(503, "trial_unavailable"), "trial unavailable");
        same(AccessErrors.SIGN_IN_FAILED, AccessErrors.reason(401, "authentication_required"), "auth required");
        // Fallbacks.
        same(AccessErrors.OFFLINE, AccessErrors.reason(-1, null), "no reply");
        same(AccessErrors.SERVER, AccessErrors.reason(500, ""), "500");
        same(AccessErrors.SERVER, AccessErrors.reason(502, "  "), "blank code");
        same(AccessErrors.SIGN_IN_FAILED, AccessErrors.reason(403, "something_new"), "unknown 403");
        same(AccessErrors.SERVER, AccessErrors.reason(404, null), "404");
        same(AccessErrors.WRONG_PASSWORD, AccessErrors.reason(400, " INVALID_CREDENTIALS "), "case and spaces");
    }

    public static void main(String[] args) {
        runAll();
        System.out.println("AccessErrorsRegression passed");
    }
}
