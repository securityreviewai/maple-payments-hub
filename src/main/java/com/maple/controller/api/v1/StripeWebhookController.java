package com.maple.controller.api.v1;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook controller for Stripe events.
 *
 * <p>Receives payment_intent.succeeded, payment_intent.payment_failed, and other Stripe events.
 * Verifies request signature using Stripe webhook signing secret. This endpoint is public (no JWT)
 * as required by Stripe's webhook delivery.
 */
@RestController
@RequestMapping("/api/v1/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    private static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final String webhookSecret;

    public StripeWebhookController(@Value("${maple.stripe.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload, @RequestHeader(STRIPE_SIGNATURE_HEADER) String sigHeader) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            logger.warn("Stripe webhook received but webhook secret is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> logger.info(
                    "Stripe payment_intent.succeeded: {}", event.getObject());
            case "payment_intent.payment_failed" -> logger.warn(
                    "Stripe payment_intent.payment_failed: {}", event.getObject());
            case "payment_intent.canceled" -> logger.info(
                    "Stripe payment_intent.canceled: {}", event.getObject());
            default -> logger.debug("Stripe webhook event type={} id={}", event.getType(), event.getId());
        }

        return ResponseEntity.ok("ok");
    }
}
