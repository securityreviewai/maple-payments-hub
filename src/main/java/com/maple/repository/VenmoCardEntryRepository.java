package com.maple.repository;

import com.maple.model.VenmoCardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface VenmoCardEntryRepository extends JpaRepository<VenmoCardEntry, UUID> {

    List<VenmoCardEntry> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            UUID userId, LocalDate start, LocalDate end);

    @Query(value = "SELECT CAST(v.transaction_date AS text) AS date, COALESCE(SUM(ABS(v.amount_cents)), 0) AS volume " +
            "FROM venmo_card_entries v WHERE v.user_id = :userId " +
            "AND v.transaction_date >= :start AND v.transaction_date <= :end " +
            "GROUP BY v.transaction_date ORDER BY v.transaction_date")
    List<Object[]> findUserVolumeByDay(@Param("userId") UUID userId,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end);
}
