package com.purchaseiq.scraper;

import com.purchaseiq.model.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BooksToScrapeScraper — LIVE Web Scraper
 *
 * Scrapes real product data from https://books.toscrape.com
 * This site is a LEGAL scraping sandbox — it exists specifically
 * to practice web scraping against realistic HTML structures.
 *
 * ─────────────────────────────────────────────────────────────────
 * WHAT WE SCRAPE:
 *   books.toscrape.com/catalogue/category/books/<genre>/index.html
 *
 *   From each book card:
 *     • Title          → <h3><a title="Book Title">
 *     • Price          → <p class="price_color">£XX.XX</p>
 *     • Rating         → <p class="star-rating Three">  (One/Two/Three/Four/Five)
 *     • Stock Status   → <p class="availability">In stock</p>
 *     • Product URL    → <h3><a href="../../product-url">
 *
 * HOW JSOUP WORKS:
 *   Jsoup.connect(url).get()  → downloads HTML as String, parses into DOM tree
 *   doc.select("css-selector") → like document.querySelectorAll() in JavaScript
 *   element.text()            → extracts visible text (strips HTML tags)
 *   element.attr("href")      → gets a specific HTML attribute value
 * ─────────────────────────────────────────────────────────────────
 */
@Component
public class BooksToScrapeScraper {

    private static final Logger log = LoggerFactory.getLogger(BooksToScrapeScraper.class);

    private static final String BASE_URL = "https://books.toscrape.com";
    private static final String CATALOGUE_BASE = BASE_URL + "/catalogue";

    @Value("${scraper.delay.ms:1000}")
    private int requestDelayMs;

    @Value("${scraper.timeout.ms:15000}")
    private int timeoutMs;

    @Value("${scraper.user-agent}")
    private String userAgent;

    @Value("${scraper.max-pages:5}")
    private int maxPages;

    /**
     * Maps word-based rating to numeric value.
     * books.toscrape.com uses CSS class names: One, Two, Three, Four, Five
     */
    private static final Map<String, Double> RATING_MAP = Map.of(
        "One",   1.0,
        "Two",   2.0,
        "Three", 3.0,
        "Four",  4.0,
        "Five",  5.0
    );

    /**
     * All genre categories available on books.toscrape.com
     * These are the real category slugs from the site's sidebar nav.
     */
    private static final Map<String, String> GENRE_URLS = Map.ofEntries(
        Map.entry("Mystery",          BASE_URL + "/catalogue/category/books/mystery_3/index.html"),
        Map.entry("Science Fiction",  BASE_URL + "/catalogue/category/books/science-fiction_16/index.html"),
        Map.entry("Travel",           BASE_URL + "/catalogue/category/books/travel_2/index.html"),
        Map.entry("History",          BASE_URL + "/catalogue/category/books/history_32/index.html"),
        Map.entry("Business",         BASE_URL + "/catalogue/category/books/business_35/index.html"),
        Map.entry("Self Help",        BASE_URL + "/catalogue/category/books/self-help_41/index.html"),
        Map.entry("Romance",          BASE_URL + "/catalogue/category/books/romance_8/index.html"),
        Map.entry("Fantasy",          BASE_URL + "/catalogue/category/books/fantasy_19/index.html"),
        Map.entry("Horror",           BASE_URL + "/catalogue/category/books/horror_31/index.html"),
        Map.entry("Psychology",       BASE_URL + "/catalogue/category/books/psychology_26/index.html"),
        Map.entry("Science",          BASE_URL + "/catalogue/category/books/science_22/index.html"),
        Map.entry("Sports and Games", BASE_URL + "/catalogue/category/books/sports-and-games_17/index.html"),
        Map.entry("Classics",         BASE_URL + "/catalogue/category/books/classics_6/index.html"),
        Map.entry("Fiction",          BASE_URL + "/catalogue/category/books/fiction_10/index.html"),
        Map.entry("Philosophy",       BASE_URL + "/catalogue/category/books/philosophy_7/index.html")
    );

    // ─────────────────────────────────────────────────────────────
    // PUBLIC METHODS
    // ─────────────────────────────────────────────────────────────

    /**
     * Scrapes ALL categories from books.toscrape.com.
     * Iterates through GENRE_URLS map, scrapes each genre page,
     * handles pagination if the genre has multiple pages.
     *
     * @return List of all scraped Product objects across all genres
     */
    public List<Product> scrapeAllCategories() {
        List<Product> allProducts = new ArrayList<>();
        log.info("Starting full scrape of books.toscrape.com — {} categories", GENRE_URLS.size());

        for (Map.Entry<String, String> entry : GENRE_URLS.entrySet()) {
            String genre = entry.getKey();
            String url   = entry.getValue();

            log.info("Scraping genre: {} from {}", genre, url);
            List<Product> genreProducts = scrapeGenreWithPagination(url, genre);
            allProducts.addAll(genreProducts);

            log.info("Genre '{}': scraped {} books. Total so far: {}", genre, genreProducts.size(), allProducts.size());

            // Ethical delay between genre requests
            sleep(requestDelayMs);
        }

        log.info("Full scrape complete: {} total books scraped", allProducts.size());
        return allProducts;
    }

    /**
     * Scrapes a single genre, handling pagination (Next → page-2.html, etc.)
     *
     * books.toscrape.com pagination pattern:
     *   Page 1: /catalogue/category/books/mystery_3/index.html
     *   Page 2: /catalogue/category/books/mystery_3/page-2.html
     *   Page 3: /catalogue/category/books/mystery_3/page-3.html
     *
     * @param startUrl URL of first page of genre
     * @param genre    Genre name string
     * @return All books found across all pages for this genre
     */
    public List<Product> scrapeGenreWithPagination(String startUrl, String genre) {
        List<Product> allBooks = new ArrayList<>();
        String currentUrl = startUrl;
        int pageNum = 1;

        while (currentUrl != null && pageNum <= maxPages) {
            log.debug("  Scraping page {}: {}", pageNum, currentUrl);

            List<Product> pageBooks = scrapeSinglePage(currentUrl, genre);
            allBooks.addAll(pageBooks);

            // Check for "next" button — books.toscrape.com uses <li class="next"><a href="page-2.html">
            currentUrl = getNextPageUrl(currentUrl);
            pageNum++;

            if (currentUrl != null) {
                sleep(requestDelayMs); // polite delay between pages
            }
        }

        return allBooks;
    }

    /**
     * Scrapes a single listing page and returns all books found on it.
     *
     * HTML structure on books.toscrape.com:
     * <article class="product_pod">
     *   <div class="image_container">
     *     <a href="../../a-light-in-the-attic_1000/index.html">
     *       <img class="thumbnail" src="..." alt="A Light in the Attic">
     *     </a>
     *   </div>
     *   <p class="star-rating Three"></p>     ← rating is CSS class
     *   <h3><a href="...relative-url..." title="A Light in the Attic">A Light ...</a></h3>
     *   <div class="product_price">
     *     <p class="price_color">£51.77</p>
     *     <p class="availability">In stock</p>
     *   </div>
     * </article>
     */
    public List<Product> scrapeSinglePage(String url, String genre) {
        List<Product> books = new ArrayList<>();

        try {
            // ── Fetch HTML ───────────────────────────────────────
            Document doc = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .referrer("https://www.google.com")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get();

            // ── Find all book cards ──────────────────────────────
            // Each book is inside <article class="product_pod">
            Elements bookCards = doc.select("article.product_pod");
            log.debug("  Found {} books on page: {}", bookCards.size(), url);

            // ── Extract data from each card ──────────────────────
            for (Element card : bookCards) {
                try {
                    Product product = extractBookFromCard(card, genre, url);
                    if (product != null) {
                        books.add(product);
                    }
                } catch (Exception e) {
                    log.warn("  Failed to extract book from card: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("  Failed to scrape page {}: {}", url, e.getMessage());
        }

        return books;
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE EXTRACTION HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Extracts all fields from a single book card element.
     *
     * CSS Selector reference:
     *   h3 a                → selects <a> inside <h3> — gets title + link
     *   p.price_color       → <p> with class="price_color" — gets price
     *   p.star-rating       → <p> with class="star-rating ..." — gets rating word
     *   p.availability      → <p> with class="availability" — gets stock status
     */
    private Product extractBookFromCard(Element card, String genre, String pageUrl) {

        // ── Title ────────────────────────────────────────────────
        // <h3><a href="..." title="Full Book Title Here">Short t...</a></h3>
        // We use .attr("title") NOT .text() because titles are often truncated
        Element titleEl = card.selectFirst("h3 a");
        if (titleEl == null) return null;
        String title = titleEl.attr("title").trim();
        if (title.isEmpty()) title = titleEl.text().trim();
        if (title.isEmpty()) return null;

        // ── Price ────────────────────────────────────────────────
        // <p class="price_color">£51.77</p>
        String priceText = card.select("p.price_color").text();
        double price = parsePrice(priceText);

        // ── Rating ───────────────────────────────────────────────
        // <p class="star-rating Three"></p>
        // Rating is encoded in the CSS class, NOT in visible text
        // card.select("p.star-rating") returns element with class like "star-rating Three"
        Element ratingEl = card.selectFirst("p.star-rating");
        double rating = 0.0;
        if (ratingEl != null) {
            // Get all CSS classes: ["star-rating", "Three"]
            // The second class is the rating word
            String classes = ratingEl.className(); // "star-rating Three"
            String[] parts = classes.split("\\s+");
            for (String part : parts) {
                if (RATING_MAP.containsKey(part)) {
                    rating = RATING_MAP.get(part);
                    break;
                }
            }
        }

        // ── Stock Status ─────────────────────────────────────────
        // <p class="availability">In stock</p>  (or "Out of stock")
        String availability = card.select("p.availability").text().trim();
        boolean inStock = availability.toLowerCase().contains("in stock");

        // ── Product URL ──────────────────────────────────────────
        // <a href="../../a-light-in-the-attic_1000/index.html"> — relative URL
        // We need to resolve it against the catalogue base
        String relativeHref = titleEl.attr("href"); // e.g. "../../a-light.../index.html"
        String productUrl = resolveProductUrl(relativeHref, pageUrl);

        // ── Sales Score ──────────────────────────────────────────
        // Heuristic: high rating + low price = more popular
        // Range: 0–5 (rating) normalized against typical price range
        double salesScore = computeSalesScore(rating, price, inStock);

        return Product.builder()
                .productName(title)
                .category(genre)
                .price(price)
                .rating(rating)
                .inStock(inStock)
                .sourceUrl(productUrl)
                .sourceSite("books.toscrape.com")
                .scrapedAt(LocalDateTime.now())
                .salesScore(salesScore)
                .build();
    }

    /**
     * Gets the URL of the next pagination page.
     * Fetches the page briefly just to check for "next" link.
     *
     * books.toscrape.com next-page HTML:
     *   <ul class="pager">
     *     <li class="next"><a href="page-2.html">next</a></li>
     *   </ul>
     */
    private String getNextPageUrl(String currentUrl) {
        try {
            Document doc = Jsoup.connect(currentUrl)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .get();

            Element nextBtn = doc.selectFirst("li.next a");
            if (nextBtn == null) return null;

            // href is relative: "page-2.html"
            // We need to combine with current page's directory
            String nextHref = nextBtn.attr("href");
            String baseDir = currentUrl.substring(0, currentUrl.lastIndexOf('/') + 1);
            return baseDir + nextHref;

        } catch (Exception e) {
            return null; // no next page or failed to fetch
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DATA PARSING UTILITIES
    // ─────────────────────────────────────────────────────────────

    /**
     * Parses price string to double.
     * Input:  "£51.77" or "£12.34"
     * Output: 51.77 or 12.34
     */
    private double parsePrice(String raw) {
        if (raw == null || raw.isBlank()) return 0.0;
        // Strip currency symbols, whitespace, commas
        String cleaned = raw.replaceAll("[^0-9.]", "");
        try {
            return cleaned.isEmpty() ? 0.0 : Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Resolves a relative URL from a product card to an absolute URL.
     *
     * Example:
     *   relativeHref = "../../a-light-in-the-attic_1000/index.html"
     *   pageUrl      = "https://books.toscrape.com/catalogue/category/books/mystery_3/index.html"
     *   result       = "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html"
     */
    private String resolveProductUrl(String relativeHref, String pageUrl) {
        if (relativeHref == null || relativeHref.isEmpty()) return pageUrl;
        if (relativeHref.startsWith("http")) return relativeHref;

        try {
            // Remove leading ../../ and prepend catalogue base
            String cleanHref = relativeHref.replaceAll("^\\.\\./\\.\\./", "");
            return CATALOGUE_BASE + "/" + cleanHref;
        } catch (Exception e) {
            return pageUrl;
        }
    }

    /**
     * Computes a sales score proxy (0.0–10.0).
     * Higher rating + lower price + in stock = higher score.
     *
     * Since books.toscrape.com has no review count, we approximate:
     *   - Rating contributes 60% (max 5.0 → normalized to 6.0)
     *   - Price inverse contributes 30% (lower price = higher score)
     *   - Stock status contributes 10%
     */
    private double computeSalesScore(double rating, double price, boolean inStock) {
        double ratingScore = rating * 1.2;      // 0–6.0
        // Normalize price: prices on this site range £10–£60 typically
        double priceScore = Math.max(0, (60.0 - price) / 60.0 * 3.0);  // 0–3.0
        double stockScore = inStock ? 1.0 : 0.0;                         // 0 or 1.0
        return Math.min(10.0, ratingScore + priceScore + stockScore);
    }

    /**
     * Sleeps for the given number of milliseconds.
     * Used to be polite between requests — don't overload the server.
     */
    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
