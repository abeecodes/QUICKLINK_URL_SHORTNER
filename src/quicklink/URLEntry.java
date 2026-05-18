package quicklink;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * URLEntry.java
 * Represents a single shortened URL record with all metadata.
 * Demonstrates OOP encapsulation, Java Date/Time API, and clean data modeling.
 */
public class URLEntry {

    // ── Fields ──────────────────────────────────────────────────────────────
    private final String shortCode;       // Unique identifier (e.g., "aB12X")
    private final String originalUrl;     // The long URL the user submitted
    private final LocalDateTime createdAt;// Timestamp of creation
    private LocalDateTime expiresAt;      // Nullable – null means never expires
    private int clickCount;               // How many times the short link was accessed
    private boolean active;              // Soft-delete flag

    public static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Constructor ──────────────────────────────────────────────────────────
    public URLEntry(String shortCode, String originalUrl, LocalDateTime expiresAt) {
        this.shortCode   = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt   = LocalDateTime.now();
        this.expiresAt   = expiresAt;
        this.clickCount  = 0;
        this.active      = true;
    }

    /** Constructor used when rehydrating records from the CSV file. */
    public URLEntry(String shortCode, String originalUrl,
                    LocalDateTime createdAt, LocalDateTime expiresAt,
                    int clickCount, boolean active) {
        this.shortCode   = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt   = createdAt;
        this.expiresAt   = expiresAt;
        this.clickCount  = clickCount;
        this.active      = active;
    }

    // ── Business Logic ───────────────────────────────────────────────────────

    /**
     * Returns true if the URL has passed its expiry date/time.
     * A null expiresAt means the link never expires.
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /** Registers a click: increments counter, marks inactive if now expired. */
    public void recordClick() {
        clickCount++;
        if (isExpired()) {
            active = false;
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public String getShortCode()        { return shortCode; }
    public String getOriginalUrl()      { return originalUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public int getClickCount()          { return clickCount; }
    public boolean isActive()           { return active; }

    public void setActive(boolean active)         { this.active = active; }
    public void setExpiresAt(LocalDateTime expiry){ this.expiresAt = expiry; }

    // ── Serialization helpers ────────────────────────────────────────────────

    /**
     * Serialize this entry to a single CSV line.
     * Format: shortCode|originalUrl|createdAt|expiresAt|clickCount|active
     * We use '|' instead of ',' to avoid conflicts with URLs that contain commas.
     */
    public String toCsvLine() {
        String expiryStr = (expiresAt != null) ? expiresAt.format(DISPLAY_FMT) : "NEVER";
        return String.join("|",
                shortCode,
                originalUrl,
                createdAt.format(DISPLAY_FMT),
                expiryStr,
                String.valueOf(clickCount),
                String.valueOf(active));
    }

    /**
     * Reconstruct a URLEntry from a CSV line produced by toCsvLine().
     * Returns null if the line is malformed (defensive programming).
     */
    public static URLEntry fromCsvLine(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 6) return null;

            String shortCode   = parts[0];
            String originalUrl = parts[1];
            LocalDateTime created = LocalDateTime.parse(parts[2], DISPLAY_FMT);
            LocalDateTime expiry  = parts[3].equals("NEVER")
                    ? null
                    : LocalDateTime.parse(parts[3], DISPLAY_FMT);
            int clicks  = Integer.parseInt(parts[4]);
            boolean act = Boolean.parseBoolean(parts[5]);

            return new URLEntry(shortCode, originalUrl, created, expiry, clicks, act);
        } catch (Exception e) {
            System.err.println("[WARN] Skipping malformed CSV line: " + line);
            return null;
        }
    }

    // ── Display ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        String expiry = (expiresAt != null) ? expiresAt.format(DISPLAY_FMT) : "Never";
        String status = !active ? "DELETED" : isExpired() ? "EXPIRED" : "Active";
        return String.format(
            "  Short Code : http://short.ly/%s%n" +
            "  Original   : %s%n" +
            "  Created    : %s%n" +
            "  Expires    : %s%n" +
            "  Clicks     : %d%n" +
            "  Status     : %s",
            shortCode, originalUrl,
            createdAt.format(DISPLAY_FMT), expiry,
            clickCount, status);
    }
}