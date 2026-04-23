package com.purchaseiq.service;

import com.purchaseiq.model.Product;
import com.purchaseiq.model.ProductDTO;
import com.purchaseiq.model.ScrapeResult;
import com.purchaseiq.repository.ProductRepository;
import com.purchaseiq.risk.RiskAnalysisService;
import com.purchaseiq.scraper.BooksToScrapeScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ProductService — Business Logic Layer
 *
 * Orchestrates the full pipeline:
 *   1. BooksToScrapeScraper → scrapes live data from books.toscrape.com
 *   2. ProductRepository    → saves products to H2 database
 *   3. RiskAnalysisService  → computes risk scores for every product
 *   4. Analytics methods    → serves processed data to REST API
 *
 * PIPELINE:
 *   HTTP POST /api/v1/scrape
 *       ↓
 *   ProductService.runFullScrape()
 *       ↓
 *   BooksToScrapeScraper.scrapeAllCategories()   [live web scraping]
 *       ↓
 *   ProductRepository.save(products)             [persist to H2]
 *       ↓
 *   RiskAnalysisService.analyzeAllProducts()     [compute risk scores]
 *       ↓
 *   Return ScrapeResult summary to frontend
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired private ProductRepository productRepository;
    @Autowired private BooksToScrapeScraper scraper;
    @Autowired private RiskAnalysisService riskAnalysisService;

    // ─────────────────────────────────────────────────────────────
    // SCRAPING PIPELINE
    // ─────────────────────────────────────────────────────────────

    /**
     * Runs the complete scraping + analysis pipeline.
     * Called by POST /api/v1/scrape
     *
     * Steps:
     *   1. Scrape all 15 genre categories from books.toscrape.com
     *   2. Save new books to DB (skip duplicates)
     *   3. Run risk analysis on all products in DB
     *   4. Return ScrapeResult with summary stats
     */
    public ScrapeResult runFullScrape() {
        long startTime = System.currentTimeMillis();
        log.info("=== Starting full scrape pipeline ===");

        // Step 1: Scrape live data
        List<Product> scraped = scraper.scrapeAllCategories();
        log.info("Scraped {} books from books.toscrape.com", scraped.size());

        // Step 2: Save to DB (deduplication by name + category)
        int saved = 0;
        Set<String> categoriesFound = new LinkedHashSet<>();

        for (Product product : scraped) {
            categoriesFound.add(product.getCategory());
            if (!productRepository.existsByProductNameAndCategory(
                    product.getProductName(), product.getCategory())) {
                productRepository.save(product);
                saved++;
            }
        }
        log.info("Saved {} new books to database (skipped {} duplicates)", saved, scraped.size() - saved);

        // Step 3: Risk analysis on all products
        riskAnalysisService.analyzeAllProducts();
        log.info("Risk analysis complete");

        long duration = System.currentTimeMillis() - startTime;
        log.info("=== Pipeline complete in {}ms ===", duration);

        return ScrapeResult.builder()
                .totalScraped(scraped.size())
                .savedToDb(saved)
                .pagesScraped(categoriesFound.size())
                .durationMs(duration)
                .source("books.toscrape.com")
                .scrapedAt(LocalDateTime.now())
                .categoriesFound(new ArrayList<>(categoriesFound))
                .message(String.format("Scraped %d books, saved %d new, analyzed %d total in DB",
                        scraped.size(), saved, productRepository.count()))
                .build();
    }

    /**
     * Scrapes a single genre category on demand.
     * Called by POST /api/v1/scrape/category?genre=Mystery
     */
    public ScrapeResult scrapeCategory(String genre) {
        long startTime = System.currentTimeMillis();

        // books.toscrape.com category URL pattern
        String categorySlug = genre.toLowerCase().replace(" ", "-");
        // Try known category IDs
        Map<String, String> slugToId = Map.of(
            "mystery", "mystery_3",
            "science-fiction", "science-fiction_16",
            "travel", "travel_2",
            "history", "history_32",
            "romance", "romance_8",
            "fantasy", "fantasy_19",
            "horror", "horror_31"
        );
        String catId = slugToId.getOrDefault(categorySlug, categorySlug + "_1");
        String url = "https://books.toscrape.com/catalogue/category/books/" + catId + "/index.html";

        List<Product> scraped = scraper.scrapeGenreWithPagination(url, genre);

        int saved = 0;
        for (Product p : scraped) {
            if (!productRepository.existsByProductNameAndCategory(p.getProductName(), p.getCategory())) {
                productRepository.save(p);
                saved++;
            }
        }

        // Re-analyze all products to update category stats
        riskAnalysisService.analyzeAllProducts();

        return ScrapeResult.builder()
                .totalScraped(scraped.size())
                .savedToDb(saved)
                .source("books.toscrape.com/" + genre)
                .scrapedAt(LocalDateTime.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .categoriesFound(List.of(genre))
                .message(String.format("Scraped %d '%s' books, saved %d new", scraped.size(), genre, saved))
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // ANALYTICS DATA — served to REST API → frontend charts
    // ─────────────────────────────────────────────────────────────

    public List<ProductDTO> getTopByCategory(String category, int limit) {
        return productRepository
                .findByCategoryOrderByRatingDesc(category)
                .stream()
                .limit(limit)
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> getCategoryProducts(String category) {
        return productRepository.findByCategory(category)
                .stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public Map<String, Long> getCategoryDistribution() {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (Object[] row : productRepository.countByCategory()) {
            dist.put((String) row[0], (Long) row[1]);
        }
        return dist;
    }

    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> getHighRiskProducts(int limit) {
        return productRepository.findTopRiskProducts(limit)
                .stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ProductDTO> getProductsByRiskLevel(String riskLevel) {
        return productRepository.findByRiskLevel(riskLevel.toUpperCase())
                .stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<String> getAllCategories() {
        return productRepository.findAll().stream()
                .map(Product::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Map<String, Object> getRiskSummary() {
        return riskAnalysisService.buildRiskSummary();
    }

    public Map<String, Long> getRiskDistribution() {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (Object[] row : productRepository.countByRiskLevel()) {
            dist.put((String) row[0], (Long) row[1]);
        }
        return dist;
    }

    public List<Map<String, Object>> getRiskHeatmap() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : productRepository.getRiskHeatmapData()) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("category",  row[0]);
            cell.put("riskLevel", row[1]);
            cell.put("count",     row[2]);
            result.add(cell);
        }
        return result;
    }

    public List<Map<String, Object>> getPriceVsRiskData() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : productRepository.getPriceVsRiskData()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("name",      row[0]);
            point.put("price",     row[1]);
            point.put("riskScore", row[2]);
            point.put("riskLevel", row[3]);
            point.put("category",  row[4]);
            result.add(point);
        }
        return result;
    }

    public List<Map<String, Object>> getCategoryAverages() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : productRepository.getCategoryAverages()) {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("category",   row[0]);
            cat.put("avgPrice",   row[1]);
            cat.put("avgRating",  row[2]);
            cat.put("count",      row[3]);
            result.add(cat);
        }
        return result;
    }

    public List<Map<String, Object>> getAvgRiskByCategory() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : productRepository.getAvgRiskByCategory()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("category",     row[0]);
            item.put("avgRiskScore", row[1]);
            item.put("count",        row[2]);
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getDashboardStats() {
        long total = productRepository.count();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProducts", total);
        stats.put("categories",    getAllCategories().size());
        stats.put("riskSummary",   riskAnalysisService.buildRiskSummary());
        stats.put("lastUpdated",   LocalDateTime.now().toString());
        return stats;
    }
}
