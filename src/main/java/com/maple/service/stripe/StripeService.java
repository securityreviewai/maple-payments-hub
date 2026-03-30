package com.maple.service.stripe;

import com.maple.dto.StripeCustomerCreateRequestDto;
import com.maple.dto.StripeCustomerResponseDto;
import com.maple.dto.StripePaymentIntentRequestDto;
import com.maple.dto.StripePaymentIntentResponseDto;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentCreateParams.CaptureMethod;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for Stripe payment operations.
 *
 * <p>Handles PaymentIntent creation and retrieval. Card data is never processed server-side —
 * the client uses the returned clientSecret with Stripe.js to collect and tokenize card details.
 */
@Service
public class StripeService {

    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    private final String apiKey;
    private final boolean enabled;

    public StripeService(
            @Value("${maple.stripe.api-key:}") String apiKey,
            @Value("${maple.stripe.enabled:false}") boolean enabled) {
        this.apiKey = apiKey;
        this.enabled = enabled;
    }

    /**
     * Creates a Stripe PaymentIntent for card payments.
     *
     * @param request the payment intent request
     * @return the PaymentIntent response with client secret for client-side completion
     */
    public StripePaymentIntentResponseDto createPaymentIntent(StripePaymentIntentRequestDto request) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            throw new StripeIntegrationException("Stripe integration is not configured or enabled");
        }

        try {
            PaymentIntentCreateParams.Builder paramsBuilder =
                    PaymentIntentCreateParams.builder()
                            .setAmount(request.getAmountCents())
                            .setCurrency(request.getCurrency().toLowerCase())
                            .setPaymentMethodTypes(Collections.singletonList("card"));

            if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
                paramsBuilder.setIdempotencyKey(request.getIdempotencyKey());
            }

            if (Boolean.FALSE.equals(request.getCaptureMethod())) {
                paramsBuilder.setCaptureMethod(CaptureMethod.MANUAL);
            }

            if (request.getReceiptEmail() != null && !request.getReceiptEmail().isBlank()) {
                paramsBuilder.setReceiptEmail(request.getReceiptEmail());
            }

            if (request.getPaymentReference() != null && !request.getPaymentReference().isBlank()) {
                paramsBuilder.putMetadata("payment_reference", request.getPaymentReference());
            }

            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                request.getMetadata().forEach(paramsBuilder::putMetadata);
            }

            PaymentIntent intent = PaymentIntent.create(paramsBuilder.build(), makeRequestOptions());

            logger.info(
                    "Created Stripe PaymentIntent id={} amount={} currency={}",
                    intent.getId(),
                    intent.getAmount(),
                    intent.getCurrency());

            return StripePaymentIntentResponseDto.builder()
                    .id(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .amountCents(intent.getAmount())
                    .currency(intent.getCurrency())
                    .status(intent.getStatus())
                    .paymentReference(request.getPaymentReference())
                    .build();

        } catch (StripeException e) {
            logger.warn("Stripe API error creating PaymentIntent: {}", e.getMessage());
            throw new StripeIntegrationException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Stripe Customer (user) for associating payments and subscriptions.
     *
     * @param request the customer create request (email required; name, description, metadata optional)
     * @param idempotencyKey optional idempotency key (header or request body) for duplicate prevention
     * @return the created customer response with Stripe customer id and details
     */
    public StripeCustomerResponseDto createCustomer(
            StripeCustomerCreateRequestDto request, String idempotencyKey) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            throw new StripeIntegrationException("Stripe integration is not configured or enabled");
        }

        try {
            CustomerCreateParams.Builder paramsBuilder =
                    CustomerCreateParams.builder().setEmail(request.getEmail().trim());

            if (request.getName() != null && !request.getName().isBlank()) {
                paramsBuilder.setName(request.getName().trim());
            }
            if (request.getDescription() != null && !request.getDescription().isBlank()) {
                paramsBuilder.setDescription(request.getDescription().trim());
            }
            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                request.getMetadata().forEach(paramsBuilder::putMetadata);
            }

            String key =
                    (idempotencyKey != null && !idempotencyKey.isBlank())
                            ? idempotencyKey
                            : request.getIdempotencyKey();
            com.stripe.net.RequestOptions requestOptions = makeRequestOptions(key);

            Customer customer = Customer.create(paramsBuilder.build(), requestOptions);

            logger.info(
                    "Created Stripe Customer id={} emailPresent={}",
                    customer.getId(),
                    (customer.getEmail() != null && !customer.getEmail().isBlank()));

            return StripeCustomerResponseDto.builder()
                    .id(customer.getId())
                    .email(customer.getEmail())
                    .name(customer.getName())
                    .description(customer.getDescription())
                    .created(customer.getCreated())
                    .build();

        } catch (StripeException e) {
            logger.warn("Stripe API error creating Customer: {}", e.getMessage());
            throw new StripeIntegrationException("Failed to create customer: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a PaymentIntent by ID.
     */
    public StripePaymentIntentResponseDto getPaymentIntent(String paymentIntentId) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            throw new StripeIntegrationException("Stripe integration is not configured or enabled");
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, makeRequestOptions());

            return StripePaymentIntentResponseDto.builder()
                    .id(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .amountCents(intent.getAmount())
                    .currency(intent.getCurrency())
                    .status(intent.getStatus())
                    .paymentReference(
                            intent.getMetadata() != null ? intent.getMetadata().get("payment_reference") : null)
                    .build();

        } catch (StripeException e) {
            logger.warn("Stripe API error retrieving PaymentIntent {}: {}", paymentIntentId, e.getMessage());
            throw new StripeIntegrationException("Failed to retrieve payment intent: " + e.getMessage(), e);
        }
    }

    private com.stripe.net.RequestOptions makeRequestOptions() {
        return makeRequestOptions(null);
    }

    private com.stripe.net.RequestOptions makeRequestOptions(String idempotencyKey) {
        com.stripe.net.RequestOptions.RequestOptionsBuilder builder =
                com.stripe.net.RequestOptions.builder().setApiKey(apiKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        return builder.build();
    }

    /** Thrown when Stripe operations fail. */
    public static class StripeIntegrationException extends RuntimeException {
        public StripeIntegrationException(String message) {
            super(message);
        }

        public StripeIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
