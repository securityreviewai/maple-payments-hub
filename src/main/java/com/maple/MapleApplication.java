package com.maple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot application class for Maple Payments Hub.
 * 
 * This application provides a corporate payment initiation system with:
 * - OAuth2 secured REST APIs
 * - Kafka-based event streaming
 * - PostgreSQL data persistence
 * - SFTP integration for payment batches
 * - HSM simulation for cryptographic operations
 * - Comprehensive audit logging
 */
@SpringBootApplication
@EnableKafka
@EnableScheduling
@EnableTransactionManagement
public class MapleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MapleApplication.class, args);
    }
}
