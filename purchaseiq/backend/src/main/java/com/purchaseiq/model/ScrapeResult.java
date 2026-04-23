package com.purchaseiq.model;

import java.time.LocalDateTime;
import java.util.List;

public class ScrapeResult {

    private int totalScraped;
    private int savedToDb;
    private int pagesScraped;
    private long durationMs;
    private String source;
    private LocalDateTime scrapedAt;
    private List<String> categoriesFound;
    private String message;

    public ScrapeResult() {}

    // Getters
    public int getTotalScraped() { return totalScraped; }
    public int getSavedToDb() { return savedToDb; }
    public int getPagesScraped() { return pagesScraped; }
    public long getDurationMs() { return durationMs; }
    public String getSource() { return source; }
    public LocalDateTime getScrapedAt() { return scrapedAt; }
    public List<String> getCategoriesFound() { return categoriesFound; }
    public String getMessage() { return message; }

    // Setters
    public void setTotalScraped(int v) { this.totalScraped = v; }
    public void setSavedToDb(int v) { this.savedToDb = v; }
    public void setPagesScraped(int v) { this.pagesScraped = v; }
    public void setDurationMs(long v) { this.durationMs = v; }
    public void setSource(String v) { this.source = v; }
    public void setScrapedAt(LocalDateTime v) { this.scrapedAt = v; }
    public void setCategoriesFound(List<String> v) { this.categoriesFound = v; }
    public void setMessage(String v) { this.message = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ScrapeResult r = new ScrapeResult();
        public Builder totalScraped(int v)           { r.totalScraped = v; return this; }
        public Builder savedToDb(int v)              { r.savedToDb = v; return this; }
        public Builder pagesScraped(int v)           { r.pagesScraped = v; return this; }
        public Builder durationMs(long v)            { r.durationMs = v; return this; }
        public Builder source(String v)              { r.source = v; return this; }
        public Builder scrapedAt(LocalDateTime v)    { r.scrapedAt = v; return this; }
        public Builder categoriesFound(List<String> v){ r.categoriesFound = v; return this; }
        public Builder message(String v)             { r.message = v; return this; }
        public ScrapeResult build()                  { return r; }
    }
}
