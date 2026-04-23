# 📚 PurchaseIQ v2.0 — Live Web Scraping + Risk Analysis

A Java Spring Boot application that **scrapes real product data live from the web** and applies a multi-dimensional **risk analysis engine** to every scraped product.

## 🌐 Data Source: books.toscrape.com

**books.toscrape.com** is a purpose-built, legal web scraping sandbox that simulates a real e-commerce site. It contains 1000+ books across 50 genres with realistic pricing, ratings, and stock statuses — all freely scrapable for practice and demonstrations.

No data is hardcoded. Every product comes from live HTTP requests.

---

## 🏗️ Architecture

```
Browser (D3.js Dashboard)
        ↕  REST API (JSON)
Spring Boot Backend (port 8080)
    ├── BooksToScrapeScraper    ← Live Jsoup HTTP scraper
    ├── RiskAnalysisService     ← 4-dimension risk engine
    ├── ProductService          ← Business logic orchestrator
    ├── AnalyticsController     ← REST API endpoints
    └── H2 Database             ← Embedded DB (zero setup)
```

---

## ⚠️ Risk Analysis Engine

Every scraped product is evaluated across **4 risk dimensions**:

| Dimension | Weight | What it measures |
|---|---|---|
| **Price Risk** | 30% | Is the price 2× or more above category average? |
| **Rating Risk** | 30% | Low star ratings = quality / satisfaction risk |
| **Stock Risk** | 20% | Out-of-stock = supply chain disruption risk |
| **Category Risk** | 20% | High competition or poor category quality |

### Risk Levels
| Score | Level | Meaning |
|---|---|---|
| 0–25 | **LOW** | Safe to purchase / recommend stocking |
| 26–50 | **MEDIUM** | Proceed with caution, review flags |
| 51–75 | **HIGH** | Significant concerns, seek alternatives |
| 76–100 | **CRITICAL** | Avoid — immediate action required |

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- No database setup needed (H2 embedded)

### 1. Start the backend

```bash
cd backend
mvn spring-boot:run
```

Server starts at http://localhost:8080

### 2. Trigger live scraping

```bash
# Scrape all 15 genre categories
curl -X POST http://localhost:8080/api/v1/scrape

# Scrape a single genre
curl -X POST "http://localhost:8080/api/v1/scrape/category?genre=Mystery"
```

### 3. Open the dashboard

Open `frontend/dashboard.html` in your browser (or use VS Code Live Server).

Click **"Scrape All Categories"** to fetch live data.

---

## 📡 API Endpoints

### Scraping
| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/scrape` | Scrape all 15 categories |
| POST | `/api/v1/scrape/category?genre=Mystery` | Scrape one genre |

### Data
| Method | URL | Description |
|---|---|---|
| GET | `/api/v1/products` | All products |
| GET | `/api/v1/products/category?name=Mystery` | Products by category |
| GET | `/api/v1/products/top?category=Mystery&limit=10` | Top rated |
| GET | `/api/v1/categories` | All category names |
| GET | `/api/v1/analytics/category-distribution` | Count per category |
| GET | `/api/v1/analytics/category-avg` | Avg price + rating per category |
| GET | `/api/v1/stats` | Dashboard summary |

### Risk Analysis
| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/risk/analyze` | Re-run risk analysis |
| GET | `/api/v1/risk/summary` | Overall risk statistics |
| GET | `/api/v1/risk/distribution` | Count per risk level |
| GET | `/api/v1/risk/heatmap` | Category × risk level matrix |
| GET | `/api/v1/risk/top?limit=20` | Highest risk products |
| GET | `/api/v1/risk/level?level=HIGH` | Products at specific risk |
| GET | `/api/v1/risk/price-vs-risk` | Scatter data |
| GET | `/api/v1/risk/by-category` | Avg risk per category |

---

## 🔧 How the Scraper Works

`BooksToScrapeScraper.java` uses **Jsoup** to:

1. Send HTTP GET requests to `books.toscrape.com/catalogue/category/books/<genre>/index.html`
2. Parse HTML into a DOM tree (like browser's `document`)
3. Select elements using CSS selectors (e.g., `article.product_pod`)
4. Extract: title from `h3 a[title]`, price from `p.price_color`, rating from CSS class on `p.star-rating`, stock from `p.availability`
5. Handle pagination via `li.next a` link
6. Apply 1-second polite delay between requests

### Scraped Fields
| Field | HTML Source |
|---|---|
| Title | `<h3><a title="...">` |
| Price | `<p class="price_color">£XX.XX</p>` |
| Rating | CSS class: `star-rating Three` → 3.0 |
| In Stock | `<p class="availability">In stock</p>` |
| URL | `<a href="...">` resolved to absolute |

---

## 🗄️ Database

Uses **H2 embedded database** — no PostgreSQL or any external DB needed.

Data persists in `./purchaseiq_data.mv.db` (created automatically).

**View database:** http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:file:./purchaseiq_data` | User: `sa` | Password: (empty)

---

## 📁 Project Structure

```
purchaseiq/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/purchaseiq/
│       ├── Application.java                   ← Main entry point
│       ├── model/
│       │   ├── Product.java                   ← JPA entity (all fields incl. risk)
│       │   ├── ProductDTO.java                ← API response object
│       │   └── ScrapeResult.java              ← Scrape summary response
│       ├── scraper/
│       │   └── BooksToScrapeScraper.java      ← LIVE Jsoup scraper
│       ├── risk/
│       │   └── RiskAnalysisService.java       ← 4-dimension risk engine
│       ├── service/
│       │   └── ProductService.java            ← Orchestration layer
│       ├── api/
│       │   └── AnalyticsController.java       ← REST endpoints
│       └── repository/
│           └── ProductRepository.java         ← JPA data access
└── frontend/
    └── dashboard.html                         ← D3.js interactive dashboard
```

---

## 🎨 Dashboard Features

- **Overview Tab:** Category distribution, avg ratings, price distribution, stock donut
- **Risk Analysis Tab:** Risk level donut, risk by category bar chart, price vs risk scatter plot, top risk products table  
- **Products Table Tab:** Searchable/filterable full product list with risk scores
- **Category Deep Dive Tab:** Per-genre top books chart, price histogram, detailed product list

---

## Ethical Scraping Notice

This project follows ethical scraping practices:
- 1-second delay between requests
- Realistic browser User-Agent header
- Only scrapes publicly available data
- Target site (books.toscrape.com) is explicitly designed for scraping practice
