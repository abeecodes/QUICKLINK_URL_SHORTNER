package quicklink;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * ╔══════════════════════════════════════════════════════════╗
 * ║           QuickLink – URL Shortener System               ║
 * ║  A menu-driven Java console application for learning     ║
 * ║  backend development concepts.                           ║
 * ╚══════════════════════════════════════════════════════════╝
 *
 * Main.java  –  Entry point & console UI controller.
 *
 * Run:
 *   javac -d out src/quicklink/*.java
 *   java  -cp out quicklink.Main
 *
 * Or from the src folder (Google Colab / single-dir):
 *   javac quicklink/*.java
 *   java  quicklink.Main
 *
 * Concepts demonstrated:
 *  - Menu-driven console application pattern
 *  - Scanner input handling
 *  - Delegation to service layer
 *  - Input validation before service calls
 *  - try-with-resources (Scanner)
 */
public class Main {

    // ── Shared state ─────────────────────────────────────────────────────────
    private static URLShortenerService service;
    private static Scanner scanner;

    // ── Entry Point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        printBanner();

        service = new URLShortenerService();

        // Silently deactivate any URLs that expired while the app was offline
        int expired = service.deactivateExpired();
        if (expired > 0) {
            System.out.println("[INFO] " + expired + " expired URL(s) marked inactive.");
        }

        try (Scanner sc = new Scanner(System.in)) {
            scanner = sc;
            mainLoop();
        }

        System.out.println("\nGoodbye! Your links are saved in: "
                + DatabaseManager.getAbsolutePath());
    }

    // ── Main Loop ─────────────────────────────────────────────────────────────

    private static void mainLoop() {
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = prompt("Enter choice").trim();

            switch (choice) {
                case "1" -> handleShortenUrl();
                case "2" -> handleOpenShortUrl();
                case "3" -> handleViewAllUrls();
                case "4" -> handleSearchUrl();
                case "5" -> handleDeleteUrl();
                case "6" -> handleAnalytics();
                case "7" -> running = false;
                default  -> printError("Invalid option. Please enter a number from 1 to 7.");
            }
        }
    }

    // ── Menu Handlers ─────────────────────────────────────────────────────────

    /**
     * Option 1 – Shorten a URL.
     * Walks the user through:
     *   (a) entering a valid URL
     *   (b) optionally providing a custom alias
     *   (c) choosing an expiry duration
     */
    private static void handleShortenUrl() {
        printSectionHeader("Shorten a URL");

        // Step 1: Get and validate the long URL
        String originalUrl;
        while (true) {
            originalUrl = prompt("Enter the long URL (e.g. https://example.com/some/long/path)");
            Validator.ValidationResult result = Validator.validateUrl(originalUrl);
            if (result.isValid()) break;
            printError(result.getMessage());
        }

        // Step 2: Custom alias or auto-generate?
        String customCode = null;
        String useCustom = prompt("Use a custom alias? (y/n)").trim().toLowerCase();
        if (useCustom.equals("y") || useCustom.equals("yes")) {
            while (true) {
                customCode = prompt("Enter alias (3-20 chars, A-Z a-z 0-9)");
                Validator.ValidationResult result = Validator.validateCustomCode(customCode);
                if (result.isValid()) break;
                printError(result.getMessage());
            }
        }

        // Step 3: Expiry selection
        LocalDateTime expiresAt = promptExpiry();

        // Step 4: Create the entry
        try {
            URLEntry entry = (customCode != null)
                    ? service.shortenUrlWithCustomCode(originalUrl, customCode, expiresAt)
                    : service.shortenUrl(originalUrl, expiresAt);

            printSuccess("URL shortened successfully!");
            System.out.println();
            System.out.println(entry);
            System.out.println();
            System.out.println("  ✓ Short link: " + URLShortenerService.BASE_URL + entry.getShortCode());
        } catch (IllegalStateException e) {
            printError(e.getMessage());
        }
    }

    /**
     * Option 2 – Resolve a short code to its original URL.
     */
    private static void handleOpenShortUrl() {
        printSectionHeader("Open / Resolve a Short URL");

        String code = prompt("Enter the short code (just the code, not the full URL)").trim();
        if (code.isBlank()) { printError("Short code cannot be empty."); return; }

        // Strip the base URL prefix if the user pasted the full short link
        if (code.startsWith(URLShortenerService.BASE_URL)) {
            code = code.substring(URLShortenerService.BASE_URL.length());
        }

        Optional<URLEntry> result = service.resolve(code);

        if (result.isPresent()) {
            URLEntry entry = result.get();
            printSuccess("Redirect successful!");
            System.out.println();
            System.out.println("  → Redirecting to: " + entry.getOriginalUrl());
            System.out.println("  → Total clicks  : " + entry.getClickCount());
            System.out.println();
        } else {
            // Provide helpful context — check if it exists but is expired/deleted
            Optional<URLEntry> raw = service.findByCode(code);
            if (raw.isPresent()) {
                URLEntry e = raw.get();
                if (e.isExpired()) {
                    printError("This short URL has expired (it was valid until "
                            + e.getExpiresAt().format(URLEntry.DISPLAY_FMT) + ").");
                } else if (!e.isActive()) {
                    printError("This short URL has been deleted.");
                }
            } else {
                printError("Short code '" + code + "' not found. Check for typos.");
            }
        }
    }

    /**
     * Option 3 – Display all stored URLs in a formatted table.
     */
    private static void handleViewAllUrls() {
        printSectionHeader("All URLs");

        List<URLEntry> all = service.getAllEntries();
        if (all.isEmpty()) {
            System.out.println("  No URLs stored yet. Use option 1 to add one!");
            return;
        }

        System.out.printf("  %-8s %-50s %-12s %-8s %-10s%n",
                "Code", "Original URL (truncated)", "Expires", "Clicks", "Status");
        System.out.println("  " + "─".repeat(95));

        for (URLEntry e : all) {
            String url    = truncate(e.getOriginalUrl(), 48);
            String expiry = (e.getExpiresAt() != null)
                    ? e.getExpiresAt().format(URLEntry.DISPLAY_FMT).substring(0, 10)
                    : "Never";
            String status = !e.isActive() ? "Deleted" : e.isExpired() ? "Expired" : "Active";

            System.out.printf("  %-8s %-50s %-12s %-8d %-10s%n",
                    e.getShortCode(), url, expiry, e.getClickCount(), status);
        }
        System.out.println();
        System.out.println("  Total records: " + all.size());
    }

    /**
     * Option 4 – Search URLs by original URL keyword or short code prefix.
     */
    private static void handleSearchUrl() {
        printSectionHeader("Search URLs");

        System.out.println("  [1] Search by keyword in original URL");
        System.out.println("  [2] Search by short code (prefix match)");
        String sub = prompt("Choose search type").trim();

        List<URLEntry> results;

        if (sub.equals("1")) {
            String keyword = prompt("Enter keyword (e.g. 'github', 'youtube')");
            results = service.searchByUrlKeyword(keyword);
            System.out.println("\n  Results for keyword '" + keyword + "':");
        } else if (sub.equals("2")) {
            String prefix = prompt("Enter short code (or prefix)");
            results = service.searchByCodePrefix(prefix.trim());
            System.out.println("\n  Results for code prefix '" + prefix + "':");
        } else {
            printError("Invalid search type.");
            return;
        }

        if (results.isEmpty()) {
            System.out.println("  No matching URLs found.");
        } else {
            System.out.println("  Found " + results.size() + " result(s):\n");
            results.forEach(e -> {
                System.out.println(e);
                System.out.println("  " + "─".repeat(60));
            });
        }
    }

    /**
     * Option 5 – Delete (soft or hard) a URL entry.
     */
    private static void handleDeleteUrl() {
        printSectionHeader("Delete a URL");

        String code = prompt("Enter the short code to delete").trim();
        if (code.isBlank()) { printError("Short code cannot be empty."); return; }

        Optional<URLEntry> found = service.findByCode(code);
        if (found.isEmpty()) {
            printError("No entry found for code '" + code + "'.");
            return;
        }

        System.out.println("\n  Found entry:");
        System.out.println(found.get());
        System.out.println();

        System.out.println("  [1] Soft delete (keeps history, marks as Deleted)");
        System.out.println("  [2] Hard delete (removes permanently)");
        System.out.println("  [3] Cancel");
        String action = prompt("Choose action").trim();

        switch (action) {
            case "1" -> {
                service.delete(code);
                printSuccess("URL soft-deleted. History preserved.");
            }
            case "2" -> {
                service.hardDelete(code);
                printSuccess("URL permanently removed.");
            }
            case "3" -> System.out.println("  Cancelled.");
            default  -> printError("Invalid choice.");
        }
    }

    /**
     * Option 6 – Display analytics dashboard.
     */
    private static void handleAnalytics() {
        printSectionHeader("Analytics Dashboard");

        URLShortenerService.AnalyticsSnapshot snap = service.getAnalytics();

        // Overview panel
        System.out.println("  ┌─────────────────────────────────────┐");
        System.out.printf ("  │  Total URLs shortened  : %-10d │%n", snap.total);
        System.out.printf ("  │  Active URLs           : %-10d │%n", snap.active);
        System.out.printf ("  │  Expired URLs          : %-10d │%n", snap.expired);
        System.out.printf ("  │  Deleted URLs          : %-10d │%n", snap.deleted);
        System.out.printf ("  │  Total clicks (all)    : %-10d │%n", snap.totalClicks);
        System.out.println("  └─────────────────────────────────────┘");

        // Most-clicked URL
        if (snap.mostClicked != null && snap.mostClicked.getClickCount() > 0) {
            System.out.println("\n  🏆 Most Clicked URL:");
            System.out.println(snap.mostClicked);
        } else {
            System.out.println("\n  No clicks recorded yet.");
        }

        // Active list summary
        List<URLEntry> active = service.getActiveEntries();
        System.out.println("\n  Active Links (" + active.size() + "):");
        if (active.isEmpty()) {
            System.out.println("  (none)");
        } else {
            active.forEach(e -> System.out.printf(
                "    %-8s  %d click(s)  →  %s%n",
                e.getShortCode(), e.getClickCount(), truncate(e.getOriginalUrl(), 50)));
        }

        // Expired list
        List<URLEntry> expired = service.getExpiredEntries();
        if (!expired.isEmpty()) {
            System.out.println("\n  ⚠ Expired Links (" + expired.size() + "):");
            expired.forEach(e -> System.out.printf(
                "    %-8s  expired %s%n",
                e.getShortCode(), e.getExpiresAt().format(URLEntry.DISPLAY_FMT)));
        }
        System.out.println();
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    /**
     * Prompts the user for expiry selection and returns the computed LocalDateTime.
     * Returns null if the user chooses "no expiry".
     */
    private static LocalDateTime promptExpiry() {
        System.out.println("\n  Set expiry duration:");
        System.out.println("  [1] No expiry (permanent)");
        System.out.println("  [2] 1 day");
        System.out.println("  [3] 7 days");
        System.out.println("  [4] 30 days");
        System.out.println("  [5] Custom (enter number of days)");

        String choice = prompt("Expiry option").trim();

        return switch (choice) {
            case "1" -> null;
            case "2" -> LocalDateTime.now().plusDays(1);
            case "3" -> LocalDateTime.now().plusDays(7);
            case "4" -> LocalDateTime.now().plusDays(30);
            case "5" -> {
                while (true) {
                    String input = prompt("Enter number of days");
                    Validator.ValidationResult r = Validator.validatePositiveInt(input, "Days");
                    if (r.isValid()) yield LocalDateTime.now().plusDays(r.getIntValue());
                    printError(r.getMessage());
                }
            }
            default -> {
                System.out.println("  Defaulting to no expiry.");
                yield null;
            }
        };
    }

    /** Reads a line from stdin with a formatted prompt. */
    private static String prompt(String label) {
        System.out.print("  » " + label + ": ");
        return scanner.nextLine();
    }

    private static void printError(String msg) {
        System.out.println("\n  ✗ Error: " + msg + "\n");
    }

    private static void printSuccess(String msg) {
        System.out.println("\n  ✓ " + msg);
    }

    private static void printSectionHeader(String title) {
        System.out.println();
        System.out.println("  ══════════════════════════════════════");
        System.out.println("   " + title);
        System.out.println("  ══════════════════════════════════════");
    }

    private static void printMainMenu() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════╗");
        System.out.println("  ║        QuickLink  Menu           ║");
        System.out.println("  ╠══════════════════════════════════╣");
        System.out.println("  ║  1. Shorten URL                  ║");
        System.out.println("  ║  2. Open Short URL               ║");
        System.out.println("  ║  3. View All URLs                ║");
        System.out.println("  ║  4. Search URL                   ║");
        System.out.println("  ║  5. Delete URL                   ║");
        System.out.println("  ║  6. Analytics Dashboard          ║");
        System.out.println("  ║  7. Exit                         ║");
        System.out.println("  ╚══════════════════════════════════╝");
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("  ██████╗ ██╗   ██╗██╗ ██████╗██╗  ██╗██╗     ██╗███╗   ██╗██╗  ██╗");
        System.out.println("  ██╔═══██╗██║   ██║██║██╔════╝██║ ██╔╝██║     ██║████╗  ██║██║ ██╔╝");
        System.out.println("  ██║   ██║██║   ██║██║██║     █████╔╝ ██║     ██║██╔██╗ ██║█████╔╝ ");
        System.out.println("  ██║▄▄ ██║██║   ██║██║██║     ██╔═██╗ ██║     ██║██║╚██╗██║██╔═██╗ ");
        System.out.println("  ╚██████╔╝╚██████╔╝██║╚██████╗██║  ██╗███████╗██║██║ ╚████║██║  ██╗");
        System.out.println("   ╚══▀▀═╝  ╚═════╝ ╚═╝ ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝");
        System.out.println();
        System.out.println("           URL Shortener System  |  Learning Java Backend Dev");
        System.out.println();
    }

    /** Truncates a string to maxLen characters, appending "…" if trimmed. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }
}