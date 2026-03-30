package com.maple.service.analytics;

import com.maple.dto.UserDataAnalyticsDto;
import com.maple.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Service for aggregated user-activity analytics.
 *
 * <p>Exposes only aggregate metrics; does not return per-user data or identifiers.
 */
@Service
@Transactional(readOnly = true)
public class UserDataAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(UserDataAnalyticsService.class);

    private final PaymentRepository paymentRepository;

    public UserDataAnalyticsService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Builds user-activity analytics for the given period.
     *
     * @param start inclusive start
     * @param end inclusive end
     * @return aggregated DTO (no PII)
     */
    public UserDataAnalyticsDto getUserDataAnalytics(OffsetDateTime start, OffsetDateTime end) {
        logger.debug("Computing user data analytics from {} to {}", start, end);

        long distinctInitiators = paymentRepository.countDistinctInitiatorsInPeriod(start, end);
        long totalCreated = paymentRepository.countCreatedInPeriod(start, end);

        double averagePerInitiator =
                distinctInitiators > 0 ? (double) totalCreated / distinctInitiators : 0.0;

        return UserDataAnalyticsDto.builder()
                .periodStart(start)
                .periodEnd(end)
                .distinctInitiatorsCount(distinctInitiators)
                .totalPaymentsCreatedInPeriod(totalCreated)
                .averagePaymentsPerInitiator(averagePerInitiator)
                .build();
    }
}
