package com.maple.controller.public;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
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
}
