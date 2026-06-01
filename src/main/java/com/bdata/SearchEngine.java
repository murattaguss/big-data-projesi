package com.bdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * In-memory Search Engine. Loads the TF-IDF inverted index from HDFS
 * and performs fast ranked search using a HashMap.
 * Also provides a book title lookup map loaded from bundled resource.
 */
public class SearchEngine {

    private final Map<String, String> index = new HashMap<>();
    private final Map<String, String> bookTitles = new HashMap<>();

    public SearchEngine() {
        loadBookTitles();
    }

    /**
     * Loads book titles from the bundled book_titles.json resource file.
     * Format (one entry per line): "id": "Title",
     * Uses fast line-by-line parsing – no regex overhead on the full file.
     */
    private void loadBookTitles() {
        try (InputStream is = SearchEngine.class.getResourceAsStream("/book_titles.json")) {
            if (is == null) {
                System.err.println("SearchEngine: book_titles.json not found in classpath.");
                return;
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    // Each line looks like: "12345": "Some Title",
                    if (!line.startsWith("\"")) continue;
                    int firstQuoteEnd = line.indexOf('"', 1);
                    if (firstQuoteEnd == -1) continue;
                    String id = line.substring(1, firstQuoteEnd);
                    int colonIdx = line.indexOf(':', firstQuoteEnd + 1);
                    if (colonIdx == -1) continue;
                    String rest = line.substring(colonIdx + 1).trim();
                    if (!rest.startsWith("\"")) continue;
                    int titleStart = 1;
                    int titleEnd = rest.lastIndexOf('"');
                    if (titleEnd <= titleStart) continue;
                    String title = rest.substring(titleStart, titleEnd);
                    bookTitles.put(id, title);
                }
            }
            System.out.println("SearchEngine: Loaded " + bookTitles.size() + " book titles.");
        } catch (Exception e) {
            System.err.println("SearchEngine: Failed to load book titles: " + e.getMessage());
        }
    }

    /**
     * Returns the human-readable title for a given book ID, or the ID if not found.
     */
    public String getBookTitle(String docId) {
        return bookTitles.getOrDefault(docId, "Book #" + docId);
    }

    /**
     * Caches a dynamically resolved book title.
     */
    public void cacheBookTitle(String docId, String title) {
        bookTitles.put(docId, title);
    }

    public static class SearchResult {
        private final String docId;
        private final double score;

        public SearchResult(String docId, double score) {
            this.docId = docId;
            this.score = score;
        }

        public String getDocId() {
            return docId;
        }

        public double getScore() {
            return score;
        }
    }

    /**
     * Loads the TF-IDF inverted index from the specified HDFS path.
     */
    public void loadIndex(String hdfsIndexPathStr) throws IOException {
        index.clear();
        try (BufferedReader br = WebHDFSClient.open(hdfsIndexPathStr)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tabIdx = line.indexOf('\t');
                if (tabIdx == -1) {
                    continue;
                }
                index.put(line.substring(0, tabIdx), line.substring(tabIdx + 1));
            }
        }
        System.out.println("SearchEngine: Loaded " + index.size() + " unique terms into memory.");
    }

    /**
     * Performs a ranked search for the given query.
     * Returns results sorted by score in descending order.
     * Implements strict intersection (AND search): only documents containing
     * all non-empty query terms are returned.
     */
    public List<SearchResult> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Clean and tokenize search query exactly like the indexing job
        String[] rawTerms = query.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9]+");
        List<String> termsList = new ArrayList<>();
        for (String t : rawTerms) {
            if (!t.isEmpty()) {
                termsList.add(t);
            }
        }
        String[] terms = termsList.toArray(new String[0]);

        Set<String> intersectedDocs = null;
        List<String> activeTerms = new ArrayList<>();
        Map<String, Map<String, Double>> termPostings = new HashMap<>();

        for (String term : terms) {
            term = term.trim();
            if (term.isEmpty()) {
                continue;
            }

            String postingsStr = index.get(term);
            if (postingsStr == null || postingsStr.isEmpty()) {
                // Skip terms that are not present in the index (e.g. stopwords or unseen words)
                continue;
            }

            Map<String, Double> docScores = parsePostings(postingsStr);
            if (docScores.isEmpty()) {
                continue;
            }
            activeTerms.add(term);
            termPostings.put(term, docScores);

            if (intersectedDocs == null) {
                intersectedDocs = new HashSet<>(docScores.keySet());
            } else {
                intersectedDocs.retainAll(docScores.keySet());
            }
        }

        if (activeTerms.isEmpty() || intersectedDocs == null || intersectedDocs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Double> combinedScores = new HashMap<>();
        for (String docId : intersectedDocs) {
            double totalScore = 0.0;
            for (String term : activeTerms) {
                Map<String, Double> docScores = termPostings.get(term);
                if (docScores != null) {
                    totalScore += docScores.getOrDefault(docId, 0.0);
                }
            }
            combinedScores.put(docId, totalScore);
        }

        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<String, Double> entry : combinedScores.entrySet()) {
            results.add(new SearchResult(entry.getKey(), entry.getValue()));
        }

        // Sort by score in descending order
        results.sort((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()));

        return results;
    }

    private Map<String, Double> parsePostings(String postingsStr) {
        Map<String, Double> docScores = new HashMap<>();
        String[] entries = postingsStr.split(",");
        for (String entry : entries) {
            int colonIdx = entry.lastIndexOf(':');
            if (colonIdx != -1) {
                String docId = entry.substring(0, colonIdx);
                try {
                    double tfidf = Double.parseDouble(entry.substring(colonIdx + 1));
                    docScores.put(docId, tfidf);
                } catch (NumberFormatException e) {
                    // Ignore malformed tfidf scores
                }
            }
        }
        return docScores;
    }

    public int getIndexSize() {
        return index.size();
    }
}
