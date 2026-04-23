package com.purchaseiq.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", length = 500, nullable = false)
    private String productName;

    @Column(name = "category", length = 100, nullable = false)
    private String category;

    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "in_stock")
    private Boolean inStock;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "source_site", length = 100)
    private String sourceSite;

    @Column(name = "scraped_at")
    private LocalDateTime scrapedAt;

    @Column(name = "sales_score")
    private Double salesScore;

    @Column(name = "discount_pct")
    private Double discountPct;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "price_risk", length = 20)
    private String priceRisk;

    @Column(name = "rating_risk", length = 20)
    private String ratingRisk;

    @Column(name = "stock_risk", length = 20)
    private String stockRisk;

    @Column(name = "category_risk", length = 20)
    private String categoryRisk;

    @Column(name = "risk_flags", columnDefinition = "TEXT")
    private String riskFlags;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    public Product() {}

    public Long getId() { return id; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public Double getPrice() { return price; }
    public Double getRating() { return rating; }
    public Boolean getInStock() { return inStock; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceSite() { return sourceSite; }
    public LocalDateTime getScrapedAt() { return scrapedAt; }
    public Double getSalesScore() { return salesScore; }
    public Double getDiscountPct() { return discountPct; }
    public Double getRiskScore() { return riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public String getPriceRisk() { return priceRisk; }
    public String getRatingRisk() { return ratingRisk; }
    public String getStockRisk() { return stockRisk; }
    public String getCategoryRisk() { return categoryRisk; }
    public String getRiskFlags() { return riskFlags; }
    public String getRecommendation() { return recommendation; }

    public void setId(Long id) { this.id = id; }
    public void setProductName(String v) { this.productName = v; }
    public void setCategory(String v) { this.category = v; }
    public void setPrice(Double v) { this.price = v; }
    public void setRating(Double v) { this.rating = v; }
    public void setInStock(Boolean v) { this.inStock = v; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public void setSourceSite(String v) { this.sourceSite = v; }
    public void setScrapedAt(LocalDateTime v) { this.scrapedAt = v; }
    public void setSalesScore(Double v) { this.salesScore = v; }
    public void setDiscountPct(Double v) { this.discountPct = v; }
    public void setRiskScore(Double v) { this.riskScore = v; }
    public void setRiskLevel(String v) { this.riskLevel = v; }
    public void setPriceRisk(String v) { this.priceRisk = v; }
    public void setRatingRisk(String v) { this.ratingRisk = v; }
    public void setStockRisk(String v) { this.stockRisk = v; }
    public void setCategoryRisk(String v) { this.categoryRisk = v; }
    public void setRiskFlags(String v) { this.riskFlags = v; }
    public void setRecommendation(String v) { this.recommendation = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Product p = new Product();
        public Builder productName(String v)     { p.productName = v; return this; }
        public Builder category(String v)        { p.category = v; return this; }
        public Builder price(Double v)           { p.price = v; return this; }
        public Builder rating(Double v)          { p.rating = v; return this; }
        public Builder inStock(Boolean v)        { p.inStock = v; return this; }
        public Builder sourceUrl(String v)       { p.sourceUrl = v; return this; }
        public Builder sourceSite(String v)      { p.sourceSite = v; return this; }
        public Builder scrapedAt(LocalDateTime v){ p.scrapedAt = v; return this; }
        public Builder salesScore(Double v)      { p.salesScore = v; return this; }
        public Builder discountPct(Double v)     { p.discountPct = v; return this; }
        public Product build()                   { return p; }
    }
}
