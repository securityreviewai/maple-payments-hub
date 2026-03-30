package com.maple.repository;

import com.maple.model.HolidayDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface HolidayDateRepository extends JpaRepository<HolidayDate, UUID> {

    List<HolidayDate> findByCalendarIdOrderByHolidayDateAsc(UUID calendarId);

    @Query("SELECT COUNT(h) > 0 FROM HolidayDate h WHERE h.calendarId = :calendarId AND h.holidayDate = :d")
    boolean existsByCalendarIdAndDate(@Param("calendarId") UUID calendarId, @Param("d") LocalDate d);
}
