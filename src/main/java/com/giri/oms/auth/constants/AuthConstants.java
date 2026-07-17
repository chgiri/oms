package com.giri.oms.auth.constants;

public final class AuthConstants {

    private AuthConstants() {
        // utility class — no instances
    }

    // ---- Exception messages (used with String.format) ----
    public static final String USERNAME_ALREADY_EXISTS_MESSAGE = "Username already taken: %s";
    public static final String EMAIL_ALREADY_EXISTS_MESSAGE = "Email already registered: %s";
    public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid username or password";

    // ---- Bean Validation messages ----
    public static final String USERNAME_REQUIRED_MESSAGE = "Username must not be blank";
    public static final String PASSWORD_REQUIRED_MESSAGE = "Password must not be blank";
    public static final String PASSWORD_TOO_SHORT_MESSAGE = "Password must be at least 8 characters";
    public static final String EMAIL_REQUIRED_MESSAGE = "Email must not be blank";
    public static final String EMAIL_INVALID_MESSAGE = "Email must be a valid email address";
    public static final String ROLE_REQUIRED_MESSAGE = "Role must not be null";

    // ---- Success / log messages ----
    public static final String USER_REGISTERED_LOG = "User registered successfully with id: {}, username: {}";
    public static final String USER_LOGGED_IN_LOG = "User logged in successfully: {}";
}
