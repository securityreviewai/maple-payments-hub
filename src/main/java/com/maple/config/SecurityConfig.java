package com.maple.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security configuration for the Maple Payments Hub.
 * 
 * Configures OAuth2 JWT authentication with role-based access control:
 * - ROLE_TREASURY_OPS: Basic payment operations
 * - ROLE_TREASURY_MANAGER: Payment approval and management
 * - ROLE_CLEARING: Submit payments to clearing network
 * - ROLE_AUDITOR: Read-only access to audit data
 * - ROLE_INTEGRATION: External system integration access
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${maple.jwt.issuer-uri:http://localhost:8080/auth/realms/maple}")
    private String issuerUri;

    @Value("${maple.jwt.jwk-set-uri:http://localhost:8080/auth/realms/maple/protocol/openid-connect/certs}")
    private String jwkSetUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/docs/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
                
                // Public health endpoints
                .requestMatchers("/public/**").permitAll()
                
                // Admin UI (requires authentication but role check done in controllers)
                .requestMatchers("/admin/**").authenticated()
                
                // API endpoints with role-based access
                .requestMatchers(HttpMethod.GET, "/api/v1/payments/**").hasAnyAuthority("SCOPE_payments:read", "ROLE_TREASURY_OPS", "ROLE_TREASURY_MANAGER", "ROLE_AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasAnyAuthority("SCOPE_payments:write", "ROLE_TREASURY_OPS")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/approve").hasAnyAuthority("SCOPE_approval:write", "ROLE_TREASURY_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/submit-to-clearing").hasAuthority("ROLE_CLEARING")
                
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").hasAnyAuthority("ROLE_TREASURY_MANAGER", "ROLE_CLEARING")
                
                // Audit endpoints
                .requestMatchers("/api/v1/audit/**").hasAuthority("ROLE_AUDITOR")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri);
        
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new JwtAudienceValidator("maple-payments-api");
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
        
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("");
        authoritiesConverter.setAuthoritiesClaimName("authorities");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        authenticationConverter.setPrincipalClaimName("sub");
        
        return authenticationConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*")); // broad access for partner integration testing
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(Arrays.asList("*")); // expose all headers for debugging
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Custom JWT audience validator for the Maple Payments API.
     */
    private static class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedAudience;

        public JwtAudienceValidator(String expectedAudience) {
            this.expectedAudience = expectedAudience;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (token.getAudience() != null && token.getAudience().contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure("The required audience is missing");
        }
    }
}
