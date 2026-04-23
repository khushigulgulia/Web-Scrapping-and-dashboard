package com.purchaseiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PurchaseIQ Application — Entry Point
 *
 * Run this class to start the Spring Boot server on port 8080.
 *
 * QUICK START:
 *   1. cd backend && mvn spring-boot:run
 *   2. POST http://localhost:8080/api/v1/scrape      ← triggers live scrape
 *   3. GET  http://localhost:8080/api/v1/risk/summary ← view risk report
 *   4. Open frontend/dashboard.html in browser
 *
 * H2 Console (view database):
 *   http://localhost:8080/h2-console
 *   JDBC URL: jdbc:h2:file:./purchaseiq_data
 *   User: sa | Password: (empty)
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
