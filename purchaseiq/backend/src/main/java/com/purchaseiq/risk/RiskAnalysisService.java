package com.purchaseiq.risk;

import com.purchaseiq.model.Product;
import com.purchaseiq.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RiskAnalysisService — Purchasing Risk Analysis Engine
 *
 * Computes a composite risk score (0–100) for each scraped product
 * based on four dimensions:
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  RISK DIMENSION        │ WEIGHT │ WHAT IT MEASURES              │
 * ├────────────────────────┼────────┼───────────────────────────────┤
 * │  1. Price Risk         │  30%   │ Is price unusually high/low?  │
 * │  2. Rating Risk        │  30%   │ Low ratings = quality concern  │
 * │  3. Stock Risk         │  20%   │ Out of stock = supply risk     │
 * │  4. Category Risk      │  20%   │ Competitive saturation risk    │
 * └────────────────────────┴────────┴───────────────────────────────┘
 *
 * RISK SCORE → RISK LEVEL:
 *   0–25   → LOW      (safe to purchase/stock)
 *  26–50   → MEDIUM   (acceptable with caution)
 *  51–75   → HIGH     (significant concerns)
 *  76–100  → CRITICAL (avoid / immediate action)
 *
 * Risk flags are human-readable explanations attached to each product.
 * Recommendations are generated based on the risk profile.
 */
@Service
public class RiskAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RiskAnalysisService.class);

    @Autowired
    private ProductRepository productRepository;

    // ── Risk Level Boundaries ────────────────────────────────────
    private static final double LOW_THRESHOLD      = 25.0;
    private static final double MEDIUM_THRESHOLD   = 50.0;
    private static final double HIGH_THRESHOLD     = 75.0;

    // ── Weight coefficients (must sum to 1.0) ────────────────────
    private static final double WEIGHT_PRICE    = 0.30;
    private static final double WEIGHT_RATING   = 0.30;
    private static final double WEIGHT_STOCK    = 0.20;
    private static final double WEIGHT_CATEGORY = 0.20;

    // ─────────────────────────────────────────────────────────────
    // MAIN ENTRY POINT: Analyse all products in DB
    // ─────────────────────────────────────────────────────────────

    /**
     * Runs full risk analysis on every product in the database.
     * Computes all four risk dimensions, sets riskScore, riskLevel,
     * and writes results back to DB.
     *
     * Called automatically after scraping completes.
     * Can also be triggered manually via POST /api/v1/risk/analyze
     */
    public void analyzeAllProducts() {
        List<Product> products = productRepository.findAll();
        log.info("Starting risk analysis on {} products", products.size());

        if (products.isEmpty()) {
            log.warn("No products found for risk analysis");
            return;
        }

        // Pre-compute category-level statistics for comparative risk
        Map<String, CategoryStats> categoryStatsMap = buildCategoryStats(products);

        // Analyse each product
        int analyzed = 0;
        for (Product product : products) {
            try {
                analyzeProduct(product, categoryStatsMap);
                productRepository.save(product);
                analyzed++;
            } catch (Exception e) {
                log.warn("Risk analysis failed for product {}: {}", product.getProductName(), e.getMessage());
            }
        }

        log.info("Risk analysis complete: {}/{} products analyzed", analyzed, products.size());
    }

    /**
     * Runs risk analysis on a single product and saves it.
     * Used for real-time analysis on newly scraped books.
     */
    public Product analyzeProduct(Product product, Map<String, CategoryStats> categoryStatsMap) {
        List<String> flags = new ArrayList<>();

        // ── Dimension 1: Price Risk (0–100) ──────────────────────
        double priceRiskScore = computePriceRisk(product, categoryStatsMap, flags);
        product.setPriceRisk(toRiskLevel(priceRiskScore));

        // ── Dimension 2: Rating Risk (0–100) ─────────────────────
        double ratingRiskScore = computeRatingRisk(product, flags);
        product.setRatingRisk(toRiskLevel(ratingRiskScore));

        // ── Dimension 3: Stock Risk (0–100) ──────────────────────
        double stockRiskScore = computeStockRisk(product, categoryStatsMap, flags);
        product.setStockRisk(toRiskLevel(stockRiskScore));

        // ── Dimension 4: Category Risk (0–100) ───────────────────
        double categoryRiskScore = computeCategoryRisk(product, categoryStatsMap, flags);
        product.setCategoryRisk(toRiskLevel(categoryRiskScore));

        // ── Composite Score (weighted average) ───────────────────
        double compositeScore = (priceRiskScore    * WEIGHT_PRICE)
                              + (ratingRiskScore   * WEIGHT_RATING)
                              + (stockRiskScore    * WEIGHT_STOCK)
                              + (categoryRiskScore * WEIGHT_CATEGORY);

        // Clamp to 0–100
        compositeScore = Math.max(0, Math.min(100, compositeScore));

        product.setRiskScore(Math.round(compositeScore * 10.0) / 10.0);
        product.setRiskLevel(toRiskLevel(compositeScore));
        product.setRiskFlags(String.join("; ", flags));
        product.setRecommendation(generateRecommendation(product, compositeScore, flags));

        return product;
    }

    // ─────────────────────────────────────────────────────────────
    // RISK DIMENSION 1: PRICE RISK
    // ─────────────────────────────────────────────────────────────

    /**
     * Measures how risky the price is relative to the category average.
     *
     * Rules:
     *   • Price > 2× category average         → HIGH risk (overpriced)
     *   • Price 1.5–2× category average        → MEDIUM risk
     *   • Price < 0.5× category average        → MEDIUM risk (suspiciously cheap)
     *   • Price within 50% of average          → LOW risk
     *   • Price = £0 or negative               → CRITICAL (data error)
     */
    private double computePriceRisk(Product product, Map<String, CategoryStats> statsMap,
                                     List<String> flags) {
        double price = product.getPrice();
        CategoryStats stats = statsMap.get(product.getCategory());

        if (price <= 0) {
            flags.add("PRICE_ERROR: Invalid price £" + price);
            return 90.0;
        }

        if (stats == null || stats.count == 0) return 30.0; // unknown category

        double avgPrice = stats.avgPrice;
        double ratio    = price / avgPrice;

        if (ratio > 2.0) {
            flags.add(String.format("OVERPRICED: £%.2f is %.1f× above category avg £%.2f",
                    price, ratio, avgPrice));
            return 80.0;
        } else if (ratio > 1.5) {
            flags.add(String.format("ABOVE_AVERAGE_PRICE: £%.2f vs category avg £%.2f", price, avgPrice));
            return 55.0;
        } else if (ratio < 0.5) {
            flags.add(String.format("SUSPICIOUSLY_CHEAP: £%.2f is well below category avg £%.2f",
                    price, avgPrice));
            return 45.0;
        } else {
            return 15.0; // price is reasonable
        }
    }

    // ─────────────────────────────────────────────────────────────
    // RISK DIMENSION 2: RATING RISK
    // ─────────────────────────────────────────────────────────────

    /**
     * Measures quality risk from the star rating.
     *
     * books.toscrape.com uses 1–5 stars (integer steps).
     *
     * Rules:
     *   • Rating = 1  → CRITICAL risk  (worst rated)
     *   • Rating = 2  → HIGH risk
     *   • Rating = 3  → MEDIUM risk
     *   • Rating = 4  → LOW risk
     *   • Rating = 5  → VERY LOW risk
     *   • Rating = 0  → HIGH risk (no rating data)
     */
    private double computeRatingRisk(Product product, List<String> flags) {
        double rating = product.getRating() != null ? product.getRating() : 0.0;

        if (rating == 0.0) {
            flags.add("NO_RATING: Product has no rating data");
            return 65.0;
        }

        if (rating <= 1.0) {
            flags.add("CRITICAL_RATING: Lowest possible rating (1★)");
            return 95.0;
        } else if (rating <= 2.0) {
            flags.add("LOW_RATING: Poor customer satisfaction (2★)");
            return 70.0;
        } else if (rating <= 3.0) {
            flags.add("AVERAGE_RATING: Below-average customer satisfaction (3★)");
            return 45.0;
        } else if (rating <= 4.0) {
            return 20.0; // good
        } else {
            return 5.0;  // excellent (5★)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // RISK DIMENSION 3: STOCK RISK
    // ─────────────────────────────────────────────────────────────

    /**
     * Measures supply chain / availability risk.
     *
     * Rules:
     *   • Out of stock                             → HIGH risk
     *   • Category has >30% out-of-stock items     → MEDIUM risk added
     *   • Category has >50% out-of-stock items     → HIGH risk added
     *   • In stock with healthy category supply    → LOW risk
     */
    private double computeStockRisk(Product product, Map<String, CategoryStats> statsMap,
                                     List<String> flags) {
        boolean inStock = Boolean.TRUE.equals(product.getInStock());
        CategoryStats stats = statsMap.get(product.getCategory());

        double baseScore = inStock ? 0.0 : 80.0;

        if (!inStock) {
            flags.add("OUT_OF_STOCK: Product currently unavailable");
        }

        if (stats != null && stats.count > 0) {
            double outOfStockRatio = (double) stats.outOfStockCount / stats.count;
            if (outOfStockRatio > 0.5) {
                flags.add(String.format("SUPPLY_SHORTAGE: %.0f%% of '%s' books out of stock",
                        outOfStockRatio * 100, product.getCategory()));
                baseScore = Math.max(baseScore, 60.0);
            } else if (outOfStockRatio > 0.3) {
                flags.add(String.format("SUPPLY_RISK: %.0f%% of '%s' category unavailable",
                        outOfStockRatio * 100, product.getCategory()));
                baseScore = Math.max(baseScore, 40.0);
            }
        }

        return baseScore;
    }

    // ─────────────────────────────────────────────────────────────
    // RISK DIMENSION 4: CATEGORY RISK
    // ─────────────────────────────────────────────────────────────

    /**
     * Measures competitive saturation risk within a category.
     *
     * Rules:
     *   • Category has >50 books (high competition)    → MEDIUM risk
     *   • Category avg rating < 3.0 (poor quality cat) → HIGH risk
     *   • Category has <5 books (niche/limited data)   → MEDIUM risk
     *   • Product is well below category avg rating    → HIGH risk
     */
    private double computeCategoryRisk(Product product, Map<String, CategoryStats> statsMap,
                                        List<String> flags) {
        CategoryStats stats = statsMap.get(product.getCategory());
        if (stats == null) return 30.0;

        double score = 0.0;

        // Competition saturation
        if (stats.count > 50) {
            flags.add(String.format("HIGH_COMPETITION: %d books in '%s' category",
                    stats.count, product.getCategory()));
            score += 30.0;
        } else if (stats.count < 5) {
            flags.add(String.format("NICHE_MARKET: Only %d books in '%s' — limited data",
                    stats.count, product.getCategory()));
            score += 25.0;
        }

        // Category quality signal
        if (stats.avgRating < 2.5) {
            flags.add(String.format("POOR_CATEGORY_QUALITY: '%s' avg rating is %.1f★",
                    product.getCategory(), stats.avgRating));
            score += 40.0;
        } else if (stats.avgRating < 3.5) {
            score += 15.0;
        }

        // Product rating vs category average
        double rating = product.getRating() != null ? product.getRating() : 0.0;
        if (rating > 0 && rating < stats.avgRating - 1.0) {
            flags.add(String.format("BELOW_CATEGORY_AVERAGE: This book %.1f★ vs category avg %.1f★",
                    rating, stats.avgRating));
            score += 20.0;
        }

        return Math.min(score, 100.0);
    }

    // ─────────────────────────────────────────────────────────────
    // RECOMMENDATION ENGINE
    // ─────────────────────────────────────────────────────────────

    /**
     * Generates a human-readable recommendation based on risk profile.
     *
     * Recommendations are actionable and specific to the risk factors found.
     */
    private String generateRecommendation(Product product, double compositeScore, List<String> flags) {
        if (compositeScore <= LOW_THRESHOLD) {
            return String.format(
                "LOW RISK: '%s' is a safe purchase. Good rating (%.0f★) and reasonable price (£%.2f). Recommended for stocking.",
                product.getProductName(), product.getRating(), product.getPrice());
        }

        StringBuilder rec = new StringBuilder();
        String level = toRiskLevel(compositeScore);

        switch (level) {
            case "MEDIUM" -> rec.append(String.format(
                "CAUTION: '%s' shows moderate risk. ", product.getProductName()));
            case "HIGH" -> rec.append(String.format(
                "HIGH RISK: '%s' has significant purchasing concerns. ", product.getProductName()));
            case "CRITICAL" -> rec.append(String.format(
                "DO NOT PURCHASE: '%s' has critical risk factors. ", product.getProductName()));
        }

        // Add specific actionable advice based on which flags fired
        boolean hasFlags = !flags.isEmpty();
        if (hasFlags) {
            String flagStr = flags.stream()
                    .map(f -> f.split(":")[0]) // get flag code only
                    .collect(Collectors.joining(", "));
            rec.append("Issues: ").append(flagStr).append(". ");
        }

        // Specific advice
        if (product.getRating() != null && product.getRating() <= 2.0) {
            rec.append("Consider sourcing higher-rated alternatives. ");
        }
        if (Boolean.FALSE.equals(product.getInStock())) {
            rec.append("Identify alternative suppliers before committing. ");
        }
        if (product.getPrice() > 0) {
            rec.append(String.format("Current price £%.2f — ", product.getPrice()));
            if (compositeScore > HIGH_THRESHOLD) {
                rec.append("negotiate price reduction or seek alternatives.");
            } else {
                rec.append("review pricing against market benchmarks.");
            }
        }

        return rec.toString().trim();
    }

    // ─────────────────────────────────────────────────────────────
    // CATEGORY STATISTICS PRE-COMPUTATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds statistics per category from all products.
     * Used to compare individual products against their category peers.
     *
     * Stats computed:
     *   - count          → total books in category
     *   - avgPrice       → mean price
     *   - avgRating      → mean star rating
     *   - outOfStockCount → how many are out of stock
     */
    public Map<String, CategoryStats> buildCategoryStats(List<Product> products) {
        Map<String, List<Product>> byCategory = products.stream()
                .collect(Collectors.groupingBy(Product::getCategory));

        Map<String, CategoryStats> statsMap = new HashMap<>();

        for (Map.Entry<String, List<Product>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<Product> catProducts = entry.getValue();

            CategoryStats stats = new CategoryStats();
            stats.count = catProducts.size();
            stats.avgPrice = catProducts.stream()
                    .mapToDouble(p -> p.getPrice() != null ? p.getPrice() : 0.0)
                    .average().orElse(0.0);
            stats.avgRating = catProducts.stream()
                    .mapToDouble(p -> p.getRating() != null ? p.getRating() : 0.0)
                    .average().orElse(0.0);
            stats.outOfStockCount = (int) catProducts.stream()
                    .filter(p -> Boolean.FALSE.equals(p.getInStock()))
                    .count();
            stats.minPrice = catProducts.stream()
                    .mapToDouble(p -> p.getPrice() != null ? p.getPrice() : Double.MAX_VALUE)
                    .min().orElse(0.0);
            stats.maxPrice = catProducts.stream()
                    .mapToDouble(p -> p.getPrice() != null ? p.getPrice() : 0.0)
                    .max().orElse(0.0);

            statsMap.put(category, stats);
        }

        log.info("Category stats built for {} categories", statsMap.size());
        return statsMap;
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Converts a numeric score (0–100) to a risk level string.
     */
    public String toRiskLevel(double score) {
        if (score <= LOW_THRESHOLD)    return "LOW";
        if (score <= MEDIUM_THRESHOLD) return "MEDIUM";
        if (score <= HIGH_THRESHOLD)   return "HIGH";
        return "CRITICAL";
    }

    /**
     * Summary statistics for a category — used internally for comparative risk.
     */
    public static class CategoryStats {
        public int    count;
        public double avgPrice;
        public double avgRating;
        public int    outOfStockCount;
        public double minPrice;
        public double maxPrice;
    }

    // ─────────────────────────────────────────────────────────────
    // RISK SUMMARY BUILDER (for dashboard endpoints)
    // ─────────────────────────────────────────────────────────────

    /**
     * Builds a summary map for the risk overview dashboard widget.
     */
    public Map<String, Object> buildRiskSummary() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return Map.of("message", "No data available. Run scrape first.");
        }

        long total       = products.size();
        long lowCount    = products.stream().filter(p -> "LOW".equals(p.getRiskLevel())).count();
        long mediumCount = products.stream().filter(p -> "MEDIUM".equals(p.getRiskLevel())).count();
        long highCount   = products.stream().filter(p -> "HIGH".equals(p.getRiskLevel())).count();
        long critCount   = products.stream().filter(p -> "CRITICAL".equals(p.getRiskLevel())).count();
        long unanalyzed  = products.stream().filter(p -> p.getRiskLevel() == null).count();

        double avgRisk = products.stream()
                .filter(p -> p.getRiskScore() != null)
                .mapToDouble(Product::getRiskScore)
                .average().orElse(0.0);

        OptionalDouble maxRisk = products.stream()
                .filter(p -> p.getRiskScore() != null)
                .mapToDouble(Product::getRiskScore)
                .max();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProducts",    total);
        summary.put("lowRisk",          lowCount);
        summary.put("mediumRisk",       mediumCount);
        summary.put("highRisk",         highCount);
        summary.put("criticalRisk",     critCount);
        summary.put("unanalyzed",       unanalyzed);
        summary.put("avgRiskScore",     Math.round(avgRisk * 10.0) / 10.0);
        summary.put("maxRiskScore",     maxRisk.isPresent() ? Math.round(maxRisk.getAsDouble() * 10.0) / 10.0 : 0);
        summary.put("overallRiskLevel", toRiskLevel(avgRisk));

        return summary;
    }
}
