package quicklink;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * URLShortenerService.java
 * The heart of the application: all business logic lives here.
 *
 * Responsibilities:
 *  - Short-code generation (Base62)
 *  - CRUD operations on URLEntry objects
 *  - Search, analytics, expiry management
 *  - Coordinating with DatabaseManager for persistence
 *
 * Concepts demonstrated:
 *  - HashMap for O(1) lookups by short code
 *  - ArrayList for ordered iteration
 *  - Base62 encoding / random code generation
 *  - Java Streams for filtering and aggregation
 *  - Exception handling (custom + standard)
 */
public class URLShortenerService {

    // ── Constants ────────────────────────────────────────────────────────────

    /** The Base62 character set used for generating short codes. */
    private static final String BASE62_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** Default length of an auto-generated short code (5 chars → 62^5 ≈ 916 million combos). */
    private static final int DEFAULT_CODE_LENGTH = 5;

    /** Maximum number of retries when generating a unique code (extremely rare collision). */
    private static final int MAX_GENERATION_RETRIES = 10;

    /** Base URL prefix displayed to the user. */
    public static final String BASE_URL = "http://short.ly/";

    // ── State ────────────────────────────────────────────────────────────────

    /**
     * Primary in-memory store.
     * Key   = shortCode (String)
     * Value = URLEntry
     *
     * HashMap gives O(1) average-case lookup, which is essential for the
     * "resolve short code → original URL" operation (the hot path).
     */
    private final Map<String, URLEntry> codeIndex = new LinkedHashMap<>();

    private final Random random = new Random();

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Loads persisted data from disk on startup.
     * LinkedHashMap preserves insertion order so "View All" lists URLs chronologically.
     */
    public URLShortenerService() {
        List<URLEntry> saved = DatabaseManager.loadAll();
        for (URLEntry entry : saved) {
            codeIndex.put(entry.getShortCode(), entry);
        }
        System.out.println("[INFO] Loaded " + codeIndex.size() + " record(s) from "
                + DatabaseManager.DB_FILE_PATH);
    }

    // ── Short Code Generation ────────────────────────────────────────────────

    /**
     * Generates a unique random Base62 code of DEFAULT_CODE_LENGTH characters.
     *
     * Base62 encoding:
     *   Characters: A-Z (26) + a-z (26) + 0-9 (10) = 62 symbols
     *   For length 5: 62^5 = 916,132,832 unique codes
     *   For length 6: 62^6 = 56,800,235,584 unique codes
     *
     * We pick characters at random (not sequential) to prevent enumeration attacks.
     */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_RETRIES; attempt++) {
            StringBuilder sb = new StringBuilder(DEFAULT_CODE_LENGTH);
            for (int i = 0; i < DEFAULT_CODE_LENGTH; i++) {
                sb.append(BASE62_CHARS.charAt(random.nextInt(BASE62_CHARS.length())));
            }
            String candidate = sb.toString();
            if (!codeIndex.containsKey(candidate)) {
                return candidate; // guaranteed unique
            }
        }
        // Extremely unlikely – fall back to 8-char code
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(BASE62_CHARS.charAt(random.nextInt(BASE62_CHARS.length())));
        }
        return sb.toString();
    }

    // ── Core Operations ──────────────────────────────────────────────────────

    /**
     * Creates a new shortened URL with an auto-generated code.
     *
     * @param originalUrl validated URL to shorten
     * @param expiresAt   expiry time (null = never expires)
     * @return the newly created URLEntry
     * @throws IllegalArgumentException if the URL is already shortened in the system
     */
    public URLEntry shortenUrl(String originalUrl, LocalDateTime expiresAt) {
        // Optional: prevent exact duplicate original URLs
        Optional<URLEntry> existing = findByOriginalUrl(originalUrl);
        if (existing.isPresent() && existing.get().isActive() && !existing.get().isExpired()) {
            System.out.println("[INFO] URL already exists. Returning existing entry.");
            return existing.get();
        }

        String code = generateUniqueCode();
        URLEntry entry = new URLEntry(code, originalUrl, expiresAt);
        codeIndex.put(code, entry);
        persist();
        return entry;
    }

    /**
     * Creates a new shortened URL with a user-supplied custom alias.
     *
     * @param originalUrl validated URL to shorten
     * @param customCode  user's desired short code (pre-validated by Validator)
     * @param expiresAt   expiry time (null = never expires)
     * @return the newly created URLEntry
     * @throws IllegalStateException if the custom code is already taken
     */
    public URLEntry shortenUrlWithCustomCode(String originalUrl,
                                              String customCode,
                                              LocalDateTime expiresAt) {
        if (codeIndex.containsKey(customCode)) {
            throw new IllegalStateException(
                "The alias '" + customCode + "' is already in use. Please choose another.");
        }
        URLEntry entry = new URLEntry(customCode, originalUrl, expiresAt);
        codeIndex.put(customCode, entry);
        persist();
        return entry;
    }

    /**
     * Resolves a short code to its original URL, recording a click.
     *
     * @param code the short code entered by the user
     * @return Optional containing the URLEntry, or empty if not found / inactive / expired
     */
    public Optional<URLEntry> resolve(String code) {
        URLEntry entry = codeIndex.get(code);
        if (entry == null || !entry.isActive()) return Optional.empty();

        if (entry.isExpired()) {
            entry.setActive(false);
            persist();
            return Optional.empty(); // treat as not found
        }

        entry.recordClick();
        persist();
        return Optional.of(entry);
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Soft-deletes a URL by marking it inactive (preserves analytics history).
     *
     * @param code the short code to delete
     * @return true if found and deleted, false if not found
     */
    public boolean delete(String code) {
        URLEntry entry = codeIndex.get(code);
        if (entry == null) return false;
        entry.setActive(false);
        persist();
        return true;
    }

    /** Hard-removes a URL entry entirely from memory and storage. */
    public boolean hardDelete(String code) {
        if (!codeIndex.containsKey(code)) return false;
        codeIndex.remove(code);
        persist();
        return true;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Finds an entry by exact short code (case-sensitive).
     */
    public Optional<URLEntry> findByCode(String code) {
        return Optional.ofNullable(codeIndex.get(code));
    }

    /**
     * Finds the first active entry whose original URL exactly matches.
     */
    public Optional<URLEntry> findByOriginalUrl(String url) {
        return codeIndex.values().stream()
                .filter(e -> e.isActive() && e.getOriginalUrl().equalsIgnoreCase(url))
                .findFirst();
    }

    /**
     * Full-text search: returns all entries whose original URL contains the query string.
     * Case-insensitive. Includes both active and inactive entries so users can see history.
     */
    public List<URLEntry> searchByUrlKeyword(String keyword) {
        String lc = keyword.toLowerCase();
        return codeIndex.values().stream()
                .filter(e -> e.getOriginalUrl().toLowerCase().contains(lc))
                .collect(Collectors.toList());
    }

    /**
     * Prefix search on short codes (case-sensitive).
     */
    public List<URLEntry> searchByCodePrefix(String prefix) {
        return codeIndex.values().stream()
                .filter(e -> e.getShortCode().startsWith(prefix))
                .collect(Collectors.toList());
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    /** Returns all entries (active + inactive + expired), insertion order. */
    public List<URLEntry> getAllEntries() {
        return new ArrayList<>(codeIndex.values());
    }

    /** Returns only entries that are active and not yet expired. */
    public List<URLEntry> getActiveEntries() {
        return codeIndex.values().stream()
                .filter(e -> e.isActive() && !e.isExpired())
                .collect(Collectors.toList());
    }

    /** Returns entries that have passed their expiry date. */
    public List<URLEntry> getExpiredEntries() {
        return codeIndex.values().stream()
                .filter(URLEntry::isExpired)
                .collect(Collectors.toList());
    }

    // ── Maintenance ───────────────────────────────────────────────────────────

    /**
     * Marks all expired URLs as inactive. Useful on startup or on demand.
     * Returns the number of entries updated.
     */
    public int deactivateExpired() {
        int count = 0;
        for (URLEntry entry : codeIndex.values()) {
            if (entry.isActive() && entry.isExpired()) {
                entry.setActive(false);
                count++;
            }
        }
        if (count > 0) persist();
        return count;
    }

    // ── Analytics ────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of aggregated statistics.
     */
    public AnalyticsSnapshot getAnalytics() {
        Collection<URLEntry> all = codeIndex.values();

        long total    = all.size();
        long active   = all.stream().filter(e -> e.isActive() && !e.isExpired()).count();
        long expired  = all.stream().filter(URLEntry::isExpired).count();
        long deleted  = all.stream().filter(e -> !e.isActive() && !e.isExpired()).count();
        long totalClicks = all.stream().mapToLong(URLEntry::getClickCount).sum();

        Optional<URLEntry> mostClicked = all.stream()
                .max(Comparator.comparingInt(URLEntry::getClickCount));

        return new AnalyticsSnapshot(total, active, expired, deleted, totalClicks, mostClicked.orElse(null));
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    /** Writes current in-memory state to disk. Called after every mutation. */
    private void persist() {
        DatabaseManager.saveAll(new ArrayList<>(codeIndex.values()));
    }

    // ── Inner Analytics DTO ───────────────────────────────────────────────────

    /**
     * Simple data transfer object holding one snapshot of analytics data.
     * Using a dedicated class keeps getAnalytics() return type clean.
     */
    public static class AnalyticsSnapshot {
        public final long total;
        public final long active;
        public final long expired;
        public final long deleted;
        public final long totalClicks;
        public final URLEntry mostClicked; // may be null

        AnalyticsSnapshot(long total, long active, long expired,
                          long deleted, long totalClicks, URLEntry mostClicked) {
            this.total       = total;
            this.active      = active;
            this.expired     = expired;
            this.deleted     = deleted;
            this.totalClicks = totalClicks;
            this.mostClicked = mostClicked;
        }
    }
}