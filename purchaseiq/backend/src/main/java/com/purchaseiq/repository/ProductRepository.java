package com.purchaseiq.repository;

import com.purchaseiq.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ProductRepository — JPA Data Access Layer
 *
 * Spring Data JPA auto-implements all standard CRUD methods.
 * Custom @Query methods handle complex aggregations for charts
 * and risk analysis views.
 *
 * All data comes from products scraped live from books.toscrape.com.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ── Basic Finders ─────────────────────────────────────────────

    List<Product> findByCategoryOrderByRatingDesc(String category);
    List<Product> findByCategoryOrderByPriceAsc(String category);
    List<Product> findByCategoryOrderBySalesScoreDesc(String category);
    List<Product> findByCategory(String category);
    List<Product> findByRiskLevel(String riskLevel);
    List<Product> findByInStock(Boolean inStock);

    Optional<Product> findByProductNameAndCategory(String productName, String category);
    boolean existsByProductNameAndCategory(String productName, String category);

    // ── Category Aggregations ─────────────────────────────────────

    /**
     * Count of products per category — for pie/bar chart
     */
    @Query("SELECT p.category, COUNT(p) FROM Product p GROUP BY p.category ORDER BY COUNT(p) DESC")
    List<Object[]> countByCategory();

    /**
     * Average price per category — for trend chart
     */
    @Query("SELECT p.category, AVG(p.price), AVG(p.rating), COUNT(p) " +
           "FROM Product p GROUP BY p.category ORDER BY AVG(p.rating) DESC")
    List<Object[]> getCategoryAverages();

    /**
     * Top N products in a category by rating
     */
    @Query("SELECT p FROM Product p WHERE p.category = :category " +
           "ORDER BY p.rating DESC LIMIT :limit")
    List<Product> findTopRatedByCategory(@Param("category") String category,
                                          @Param("limit") int limit);

    // ── Risk Analysis Queries ─────────────────────────────────────

    /**
     * Count products per risk level — for risk donut chart
     */
    @Query("SELECT p.riskLevel, COUNT(p) FROM Product p " +
           "WHERE p.riskLevel IS NOT NULL GROUP BY p.riskLevel")
    List<Object[]> countByRiskLevel();

    /**
     * Average risk score per category — for risk heatmap
     */
    @Query("SELECT p.category, p.riskLevel, COUNT(p) " +
           "FROM Product p WHERE p.riskLevel IS NOT NULL " +
           "GROUP BY p.category, p.riskLevel ORDER BY p.category, p.riskLevel")
    List<Object[]> getRiskHeatmapData();

    /**
     * Top high-risk products sorted by riskScore descending
     */
    @Query("SELECT p FROM Product p WHERE p.riskScore IS NOT NULL " +
           "ORDER BY p.riskScore DESC LIMIT :limit")
    List<Product> findTopRiskProducts(@Param("limit") int limit);

    /**
     * Products with rating below threshold (quality risk)
     */
    @Query("SELECT p FROM Product p WHERE p.rating < :threshold ORDER BY p.rating ASC")
    List<Product> findLowRatedProducts(@Param("threshold") double threshold);

    /**
     * Price vs risk scatter data
     */
    @Query("SELECT p.productName, p.price, p.riskScore, p.riskLevel, p.category " +
           "FROM Product p WHERE p.riskScore IS NOT NULL ORDER BY p.riskScore DESC")
    List<Object[]> getPriceVsRiskData();

    /**
     * Rating distribution across all books (for histogram)
     */
    @Query("SELECT p.rating, COUNT(p) FROM Product p GROUP BY p.rating ORDER BY p.rating")
    List<Object[]> getRatingDistribution();

    /**
     * Average risk score per category
     */
    @Query("SELECT p.category, AVG(p.riskScore), COUNT(p) FROM Product p " +
           "WHERE p.riskScore IS NOT NULL GROUP BY p.category ORDER BY AVG(p.riskScore) DESC")
    List<Object[]> getAvgRiskByCategory();

    /**
     * Count out-of-stock products per category
     */
    @Query("SELECT p.category, COUNT(p) FROM Product p WHERE p.inStock = false GROUP BY p.category")
    List<Object[]> getOutOfStockByCategory();
}
