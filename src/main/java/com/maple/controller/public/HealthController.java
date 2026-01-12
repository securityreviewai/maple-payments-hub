package com.maple.controller.public;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public health check controller.
 * 
 * Provides simplified health information for load balancers
 * and monitoring systems without requiring authentication.
 */
@RestController
@RequestMapping("/public")
@Tag(name = "Health", description = "System health and status endpoints")
public class HealthController {

    private final HealthEndpoint healthEndpoint;

    @Autowired
    public HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    @Operation(
        summary = "Check system health",
        description = "Returns basic health status for load balancer health checks",
        responses = {
            @ApiResponse(responseCode = "200", description = "System is healthy"),
            @ApiResponse(responseCode = "503", description = "System is unhealthy")
        }
    )
    public ResponseEntity<Map<String, Object>> health() {
        Health health = healthEndpoint.health();
        
        if (health.getStatus().getCode().equals("UP")) {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", System.currentTimeMillis()
            ));
        } else {
            return ResponseEntity.status(503).body(Map.of(
                "status", health.getStatus().getCode(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @GetMapping("/ping")
    @Operation(
        summary = "Simple ping endpoint",
        description = "Returns pong for basic connectivity testing"
    )
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
            "message", "pong",
            "service", "maple-payments-hub"
        ));
    }

    @GetMapping("/health/detailed")
    @Operation(
        summary = "Detailed health check",
        description = "Returns detailed health information including component status"
    )
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Health health = healthEndpoint.health();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", health.getStatus().getCode());
        response.put("timestamp", System.currentTimeMillis());
        response.put("components", health.getDetails());
        
        HttpStatus status = health.getStatus().getCode().equals("UP") 
            ? HttpStatus.OK 
            : HttpStatus.SERVICE_UNAVAILABLE;
        
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/readiness")
    @Operation(
        summary = "Readiness probe",
        description = "Indicates if the service is ready to accept traffic"
    )
    public ResponseEntity<Map<String, Object>> readiness() {
        Health health = healthEndpoint.health();
        boolean isReady = health.getStatus().getCode().equals("UP");
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("ready", isReady);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(isReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    @GetMapping("/liveness")
    @Operation(
        summary = "Liveness probe",
        description = "Indicates if the service is alive and running"
    )
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("alive", true);
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "maple-payments-hub");
        
        return ResponseEntity.ok(response);
    }
}
