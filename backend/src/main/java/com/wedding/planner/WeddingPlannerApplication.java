package com.wedding.planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Wedding Planner backend.
 *
 * <p>Phase 1 scope: persistence foundation only (entities, mappings, repositories).
 * Security, services and REST controllers arrive in later phases.
 */
@SpringBootApplication
public class WeddingPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeddingPlannerApplication.class, args);
    }
}
