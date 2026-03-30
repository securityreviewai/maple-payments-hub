package com.maple.service.stripe;

import com.maple.dto.CardAnalyticsDto;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentListParams;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for credit card payment analytics using Stripe PaymentIntent data.
 *
 * <p>Aggregates metrics from Stripe's PaymentIntent API. No raw card data is processed or
 * returned — only aggregated counts, volumes, and success rates. Card data is never stored or
 * transmitted by this service.
 */
@Service
public class StripeCardAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(StripeCardAnalyticsService.class);

    /** Max PaymentIntents to fetch per analytics run to prevent excessive Stripe API usage. */
    private static final int MAX_FETCH_LIMIT = 500;

    private final String apiKey;
    private final boolean enabled;

    public StripeCardAnalyticsService(
            @Value("${maple.stripe.api-key:}") String apiKey,
            @Value("${maple.stripe.enabled:false}") boolean enabled) {
        this.apiKey = apiKey;
        this.enabled = enabled;
    }

    /**
     * Computes aggregated analytics for card payments in the given period.
     *
     * @param periodStart start of period (inclusive)
     * @param periodEnd end of period (inclusive)
     * @return aggregated analytics; empty metrics if Stripe is disabled or error occurs
     */
    public CardAnalyticsDto getCardAnalytics(OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            logger.debug("Stripe not configured; returning empty card analytics");
            return buildEmptyAnalytics(periodStart, periodEnd);
        }

        try {
            long startTs = periodStart.toInstant().getEpochSecond();
            long endTs = periodEnd.toInstant().getEpochSecond();

            long totalCount = 0;
            long succeededCount = 0;
            long totalVolumeCents = 0;
            Map<String, Long> countByStatus = new HashMap<>();
            Map<String, Long> volumeByCurrency = new LinkedHashMap<>();

            PaymentIntentListParams.Created created =
                    PaymentIntentListParams.Created.builder()
                            .setGte(startTs)
                            .setLte(endTs)
                            .build();

            com.stripe.net.RequestOptions requestOptions =
                    com.stripe.net.RequestOptions.builder().setApiKey(apiKey).build();

            int fetched = 0;
            String lastId = null;

            while (fetched < MAX_FETCH_LIMIT) {
                PaymentIntentListParams.Builder paramBuilder =
                        PaymentIntentListParams.builder()
                                .setCreated(created)
                                .setLimit(100L);
                if (lastId != null) {
                    paramBuilder.setStartingAfter(lastId);
                }
                PaymentIntentListParams listParams = paramBuilder.build();

                com.stripe.model.PaymentIntentCollection collection =
                        PaymentIntent.list(listParams, requestOptions);

                for (PaymentIntent pi : collection.getData()) {
                    totalCount++;
                    String status = pi.getStatus() != null ? pi.getStatus() : "unknown";
                    countByStatus.merge(status, 1L, Long::sum);

                    if ("succeeded".equals(status)) {
                        succeededCount++;
                        long amount = pi.getAmount() != null ? pi.getAmount() : 0;
                        totalVolumeCents += amount;

                        String currency = pi.getCurrency() != null ? pi.getCurrency() : "unknown";
                        volumeByCurrency.merge(currency, amount, Long::sum);
                    }
                }

                fetched += collection.getData().size();
                boolean hasMore = collection.getHasMore();
                if (!hasMore || collection.getData().isEmpty()) {
                    break;
                }
                lastId = collection.getData().get(collection.getData().size() - 1).getId();
            }

            long failedCount = totalCount - succeededCount;
            double successRatePercent =
                    totalCount > 0 ? (succeededCount * 100.0 / totalCount) : 0.0;
            double averageAmountCents = succeededCount > 0 ? (double) totalVolumeCents / succeededCount : 0.0;

            logger.debug(
                    "Card analytics: total={} succeeded={} period={} to {}",
                    totalCount,
                    succeededCount,
                    periodStart,
                    periodEnd);

            return CardAnalyticsDto.builder()
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .totalCount(totalCount)
                    .succeededCount(succeededCount)
                    .failedCount(failedCount)
                    .totalVolumeCents(totalVolumeCents)
                    .averageAmountCents(averageAmountCents)
                    .successRatePercent(successRatePercent)
                    .countByStatus(countByStatus)
                    .volumeByCurrency(volumeByCurrency)
                    .build();

        } catch (StripeException e) {
            logger.warn("Stripe API error computing card analytics: {}", e.getMessage());
            throw new StripeService.StripeIntegrationException(
                    "Failed to compute card analytics: " + e.getMessage(), e);
        }
    }

    private CardAnalyticsDto buildEmptyAnalytics(OffsetDateTime periodStart, OffsetDateTime periodEnd) {
        return CardAnalyticsDto.builder()
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalCount(0)
                .succeededCount(0)
                .failedCount(0)
                .totalVolumeCents(0)
                .averageAmountCents(0.0)
                .successRatePercent(0.0)
                .countByStatus(Map.of())
                .volumeByCurrency(Map.of())
                .build();
    }
}
