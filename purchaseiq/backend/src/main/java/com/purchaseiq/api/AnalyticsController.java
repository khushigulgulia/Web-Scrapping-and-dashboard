package com.purchaseiq.api;

import com.purchaseiq.model.ProductDTO;
import com.purchaseiq.model.ScrapeResult;
import com.purchaseiq.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AnalyticsController — REST API Layer
 *
 * All HTTP endpoints are prefixed with /api/v1/
 *
 * ─── SCRAPING ENDPOINTS ───────────────────────────────────────────
 *   POST /api/v1/scrape                       → scrape all categories from books.toscrape.com
 *   POST /api/v1/scrape/category?genre=Mystery → scrape a single genre
 *
 * ─── DATA ENDPOINTS ───────────────────────────────────────────────
 *   GET  /api/v1/products                     → all products (pageable)
 *   GET  /api/v1/products/category?name=...   → products in a category
 *   GET  /api/v1/products/top?category=...    → top rated in a category
 *   GET  /api/v1/categories                   → list all category names
 *   GET  /api/v1/stats                        → dashboard summary stats
 *   GET  /api/v1/analytics/category-avg       → avg price+rating per category
 *
 * ─── RISK ANALYSIS ENDPOINTS ─────────────────────────────────────
 *   POST /api/v1/risk/analyze                 → re-run risk analysis
 *   GET  /api/v1/risk/summary                 → risk overview stats
 *   GET  /api/v1/risk/distribution            → count per risk level
 *   GET  /api/v1/risk/heatmap                 → category × risk level matrix
 *   GET  /api/v1/risk/top?limit=20            → top N highest risk products
 *   GET  /api/v1/risk/level?level=HIGH        → products at a specific risk level
 *   GET  /api/v1/risk/price-vs-risk           → scatter: price vs risk score
 *   GET  /api/v1/risk/by-category             → avg risk score per category
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")  // Allow all origins for demo; restrict in production
public class AnalyticsController {

    @Autowired
    private ProductService productService;

    // ─────────────────────────────────────────────────────────────
    // SCRAPING ENDPOINTS
    // ─────────────────────────────────────────────────────────────

    /**
     * Triggers a full live scrape of all 15 genre categories from books.toscrape.com.
     * Then runs risk analysis on all scraped products.
     *
     * Typical duration: 30–90 seconds (multiple pages, polite delays)
     *
     * POST /api/v1/scrape
     * Response: ScrapeResult { totalScraped, savedToDb, categories, durationMs, ... }
     */
    @PostMapping("/scrape")
    public ResponseEntity<ScrapeResult> scrapeAll() {
        ScrapeResult result = productService.runFullScrape();
        return ResponseEntity.ok(result);
    }

    /**
     * Scrapes a single genre category.
     * POST /api/v1/scrape/category?genre=Mystery
     */
    @PostMapping("/scrape/category")
    public ResponseEntity<ScrapeResult> scrapeCategory(@RequestParam String genre) {
        ScrapeResult result = productService.scrapeCategory(genre);
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────
    // PRODUCT DATA ENDPOINTS
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns all products in the database.
     * GET /api/v1/products
     */
    @GetMapping("/products")
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    /**
     * Returns all products in a specific category.
     * GET /api/v1/products/category?name=Mystery
     */
    @GetMapping("/products/category")
    public ResponseEntity<List<ProductDTO>> getByCategory(@RequestParam String name) {
        return ResponseEntity.ok(productService.getCategoryProducts(name));
    }

    /**
     * Returns top N products in a category sorted by rating.
     * GET /api/v1/products/top?category=Mystery&limit=10
     */
    @GetMapping("/products/top")
    public ResponseEntity<List<ProductDTO>> getTopByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(productService.getTopByCategory(category, Math.min(limit, 50)));
    }

    /**
     * Returns all distinct category names.
     * GET /api/v1/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(productService.getAllCategories());
    }

    /**
     * Returns category distribution (count per category).
     * GET /api/v1/analytics/category-distribution
     */
    @GetMapping("/analytics/category-distribution")
    public ResponseEntity<Map<String, Long>> getCategoryDistribution() {
        return ResponseEntity.ok(productService.getCategoryDistribution());
    }

    /**
     * Returns avg price + avg rating per category.
     * GET /api/v1/analytics/category-avg
     */
    @GetMapping("/analytics/category-avg")
    public ResponseEntity<List<Map<String, Object>>> getCategoryAverages() {
        return ResponseEntity.ok(productService.getCategoryAverages());
    }

    /**
     * Dashboard summary stats.
     * GET /api/v1/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        return ResponseEntity.ok(productService.getDashboardStats());
    }

    // ─────────────────────────────────────────────────────────────
    // RISK ANALYSIS ENDPOINTS
    // ─────────────────────────────────────────────────────────────

    /**
     * Triggers risk analysis on all products in the database.
     * POST /api/v1/risk/analyze
     */
    @PostMapping("/risk/analyze")
    public ResponseEntity<Map<String, Object>> runRiskAnalysis() {
        productService.getRiskSummary(); // triggers re-analysis
        return ResponseEntity.ok(Map.of(
            "message", "Risk analysis complete",
            "summary", productService.getRiskSummary()
        ));
    }

    /**
     * Returns overall risk summary statistics.
     * GET /api/v1/risk/summary
     *
     * Response: {
     *   "totalProducts": 450,
     *   "lowRisk": 120,
     *   "mediumRisk": 200,
     *   "highRisk": 100,
     *   "criticalRisk": 30,
     *   "avgRiskScore": 42.5,
     *   "overallRiskLevel": "MEDIUM"
     * }
     */
    @GetMapping("/risk/summary")
    public ResponseEntity<Map<String, Object>> getRiskSummary() {
        return ResponseEntity.ok(productService.getRiskSummary());
    }

    /**
     * Returns count of products per risk level (for donut chart).
     * GET /api/v1/risk/distribution
     * Response: { "LOW": 120, "MEDIUM": 200, "HIGH": 100, "CRITICAL": 30 }
     */
    @GetMapping("/risk/distribution")
    public ResponseEntity<Map<String, Long>> getRiskDistribution() {
        return ResponseEntity.ok(productService.getRiskDistribution());
    }

    /**
     * Returns risk heatmap data: category × riskLevel counts.
     * GET /api/v1/risk/heatmap
     */
    @GetMapping("/risk/heatmap")
    public ResponseEntity<List<Map<String, Object>>> getRiskHeatmap() {
        return ResponseEntity.ok(productService.getRiskHeatmap());
    }

    /**
     * Returns the N highest-risk products.
     * GET /api/v1/risk/top?limit=20
     */
    @GetMapping("/risk/top")
    public ResponseEntity<List<ProductDTO>> getTopRiskProducts(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(productService.getHighRiskProducts(Math.min(limit, 100)));
    }

    /**
     * Returns all products at a specific risk level.
     * GET /api/v1/risk/level?level=HIGH
     */
    @GetMapping("/risk/level")
    public ResponseEntity<List<ProductDTO>> getByRiskLevel(@RequestParam String level) {
        return ResponseEntity.ok(productService.getProductsByRiskLevel(level));
    }

    /**
     * Returns scatter plot data: price vs risk score per product.
     * GET /api/v1/risk/price-vs-risk
     */
    @GetMapping("/risk/price-vs-risk")
    public ResponseEntity<List<Map<String, Object>>> getPriceVsRisk() {
        return ResponseEntity.ok(productService.getPriceVsRiskData());
    }

    /**
     * Returns average risk score per category (for bar chart).
     * GET /api/v1/risk/by-category
     */
    @GetMapping("/risk/by-category")
    public ResponseEntity<List<Map<String, Object>>> getAvgRiskByCategory() {
        return ResponseEntity.ok(productService.getAvgRiskByCategory());
    }

    // ─────────────────────────────────────────────────────────────
    // HEALTH CHECK
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "app",    "PurchaseIQ v2.0",
            "source", "books.toscrape.com (live scraping)"
        ));
    }
}
