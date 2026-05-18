package quicklink;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * DatabaseManager.java
 * Handles all persistent storage using a plain CSV/text file.
 *
 * Design decisions:
 *  - Pipe-delimited CSV ("|") avoids conflicts with URLs containing commas
 *  - On every save the entire file is rewritten (safe for small datasets)
 *  - File location is configurable via DB_FILE_PATH constant
 *  - For Colab/local compatibility, stores data in the current working directory
 *
 * Concepts demonstrated:
 *  - File I/O (BufferedReader / BufferedWriter)
 *  - try-with-resources for safe resource management
 *  - IOException handling
 *  - Separation of concerns (storage ≠ business logic)
 */
public class DatabaseManager {

    // ── Configuration ────────────────────────────────────────────────────────

    /** Path to the data file, relative to the working directory. */
    public static final String DB_FILE_PATH = "data/quicklink_data.csv";

    /** Header line written at the top of the CSV so humans can read it. */
    private static final String CSV_HEADER =
            "# QuickLink Data File  |  Format: shortCode|originalUrl|createdAt|expiresAt|clickCount|active";

    // Prevent direct instantiation
    private DatabaseManager() {}

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Loads all URLEntry records from the CSV file.
     *
     * @return a mutable List of URLEntry objects; empty list if file does not exist
     */
    public static List<URLEntry> loadAll() {
        List<URLEntry> entries = new ArrayList<>();
        Path path = Paths.get(DB_FILE_PATH);

        if (!Files.exists(path)) {
            return entries; // first run – no file yet
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue; // skip comments/blanks

                URLEntry entry = URLEntry.fromCsvLine(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Could not read data file: " + e.getMessage());
        }

        return entries;
    }

    /**
     * Persists all URLEntry records to the CSV file, overwriting any existing content.
     * Creates parent directories automatically if they do not exist.
     *
     * @param entries the complete list of entries to save
     */
    public static void saveAll(List<URLEntry> entries) {
        Path path = Paths.get(DB_FILE_PATH);

        try {
            // Ensure the 'data/' directory exists
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                writer.write(CSV_HEADER);
                writer.newLine();

                for (URLEntry entry : entries) {
                    writer.write(entry.toCsvLine());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Could not save data file: " + e.getMessage());
        }
    }

    /**
     * Returns the absolute path of the data file for display purposes.
     */
    public static String getAbsolutePath() {
        return Paths.get(DB_FILE_PATH).toAbsolutePath().toString();
    }
}