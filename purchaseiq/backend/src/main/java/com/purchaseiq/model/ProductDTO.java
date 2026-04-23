package com.purchaseiq.model;

public class ProductDTO {

    private Long id;
    private String productName;
    private String category;
    private Double price;
    private Double rating;
    private Boolean inStock;
    private String sourceUrl;
    private String sourceSite;
    private String scrapedAt;
    private Double salesScore;
    private Double discountPct;
    private Double riskScore;
    private String riskLevel;
    private String priceRisk;
    private String ratingRisk;
    private String stockRisk;
    private String categoryRisk;
    private String riskFlags;
    private String recommendation;

    public ProductDTO() {}

    // Getters
    public Long getId() { return id; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public Double getPrice() { return price; }
    public Double getRating() { return rating; }
    public Boolean getInStock() { return inStock; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceSite() { return sourceSite; }
    public String getScrapedAt() { return scrapedAt; }
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

    // Setters
    public void setId(Long v) { this.id = v; }
    public void setProductName(String v) { this.productName = v; }
    public void setCategory(String v) { this.category = v; }
    public void setPrice(Double v) { this.price = v; }
    public void setRating(Double v) { this.rating = v; }
    public void setInStock(Boolean v) { this.inStock = v; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public void setSourceSite(String v) { this.sourceSite = v; }
    public void setScrapedAt(String v) { this.scrapedAt = v; }
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

    public static ProductDTO fromEntity(Product p) {
        ProductDTO dto = new ProductDTO();
        dto.id             = p.getId();
        dto.productName    = p.getProductName();
        dto.category       = p.getCategory();
        dto.price          = p.getPrice();
        dto.rating         = p.getRating();
        dto.inStock        = p.getInStock();
        dto.sourceUrl      = p.getSourceUrl();
        dto.sourceSite     = p.getSourceSite();
        dto.scrapedAt      = p.getScrapedAt() != null ? p.getScrapedAt().toString() : null;
        dto.salesScore     = p.getSalesScore();
        dto.discountPct    = p.getDiscountPct();
        dto.riskScore      = p.getRiskScore();
        dto.riskLevel      = p.getRiskLevel();
        dto.priceRisk      = p.getPriceRisk();
        dto.ratingRisk     = p.getRatingRisk();
        dto.stockRisk      = p.getStockRisk();
        dto.categoryRisk   = p.getCategoryRisk();
        dto.riskFlags      = p.getRiskFlags();
        dto.recommendation = p.getRecommendation();
        return dto;
    }
}
