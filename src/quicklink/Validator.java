package quicklink;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Validator.java
 * Centralises all input-validation logic.
 *
 * Concepts demonstrated:
 *  - Static utility class pattern
 *  - Regular expressions
 *  - Java URI parsing
 *  - Single-Responsibility Principle
 */
public class Validator {

    // ── Constants ────────────────────────────────────────────────────────────

    /** Allowed characters in a short code (Base62 alphabet). */
    private static final String BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** Minimum and maximum lengths for a custom alias. */
    public static final int MIN_CODE_LEN = 3;
    public static final int MAX_CODE_LEN = 20;

    /**
     * Simple regex that accepts http(s):// or ftp:// URLs.
     * Allows IPs, localhost, and standard domain names.
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^(https?|ftp)://" +               // scheme
        "([\\w\\-]+\\.)*[\\w\\-]+" +        // host (domain or IP segments)
        "(:\\d{1,5})?" +                   // optional port
        "(/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?" + // optional path/query/fragment
        "$",
        Pattern.CASE_INSENSITIVE
    );

    // Prevent instantiation – this is a pure utility class
    private Validator() {}

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Validates a URL string.
     *
     * Strategy: regex pre-check → Java URI parse → scheme allowlist.
     *
     * @param url the raw string entered by the user
     * @return a ValidationResult explaining success or the reason for failure
     */
    public static ValidationResult validateUrl(String url) {
        if (url == null || url.isBlank()) {
            return ValidationResult.fail("URL cannot be empty.");
        }

        url = url.trim();

        // Quick regex check
        if (!URL_PATTERN.matcher(url).matches()) {
            return ValidationResult.fail(
                "URL format is invalid. Make sure it starts with http://, https://, or ftp://.");
        }

        // Deeper parse via java.net.URI
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http")
                                && !scheme.equalsIgnoreCase("https")
                                && !scheme.equalsIgnoreCase("ftp"))) {
                return ValidationResult.fail("Only http, https, and ftp schemes are supported.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return ValidationResult.fail("URL must contain a valid host/domain.");
            }
        } catch (Exception e) {
            return ValidationResult.fail("URL could not be parsed: " + e.getMessage());
        }

        return ValidationResult.ok();
    }

    /**
     * Validates a custom short-code alias supplied by the user.
     *
     * Rules:
     *  - Length between MIN_CODE_LEN and MAX_CODE_LEN
     *  - Only Base62 characters (A-Z, a-z, 0-9)
     *  - No spaces or special characters
     */
    public static ValidationResult validateCustomCode(String code) {
        if (code == null || code.isBlank()) {
            return ValidationResult.fail("Custom alias cannot be empty.");
        }
        code = code.trim();

        if (code.length() < MIN_CODE_LEN) {
            return ValidationResult.fail(
                "Alias too short – minimum " + MIN_CODE_LEN + " characters.");
        }
        if (code.length() > MAX_CODE_LEN) {
            return ValidationResult.fail(
                "Alias too long – maximum " + MAX_CODE_LEN + " characters.");
        }
        for (char c : code.toCharArray()) {
            if (BASE62.indexOf(c) == -1) {
                return ValidationResult.fail(
                    "Alias contains invalid character '" + c +
                    "'. Only A-Z, a-z, and 0-9 are allowed.");
            }
        }
        return ValidationResult.ok();
    }

    /**
     * Validates that a duration string represents a positive integer.
     *
     * @param input the raw string (e.g. "7" for 7 days)
     * @return a ValidationResult; on success, call result.getIntValue()
     */
    public static ValidationResult validatePositiveInt(String input, String fieldName) {
        if (input == null || input.isBlank()) {
            return ValidationResult.fail(fieldName + " cannot be empty.");
        }
        try {
            int value = Integer.parseInt(input.trim());
            if (value <= 0) {
                return ValidationResult.fail(fieldName + " must be a positive number.");
            }
            return ValidationResult.okWithInt(value);
        } catch (NumberFormatException e) {
            return ValidationResult.fail(fieldName + " must be a whole number (e.g. 7).");
        }
    }

    // ── Inner result class ───────────────────────────────────────────────────

    /**
     * Simple value object returned by every validation method.
     * Avoids throwing exceptions for expected user-input errors.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String  message;
        private final int     intValue; // set only for numeric validations

        private ValidationResult(boolean valid, String message, int intValue) {
            this.valid    = valid;
            this.message  = message;
            this.intValue = intValue;
        }

        public static ValidationResult ok()                    { return new ValidationResult(true,  "",       0); }
        public static ValidationResult okWithInt(int v)        { return new ValidationResult(true,  "",       v); }
        public static ValidationResult fail(String msg)        { return new ValidationResult(false, msg,      0); }

        public boolean isValid()    { return valid; }
        public String  getMessage() { return message; }
        public int     getIntValue(){ return intValue; }
    }
}