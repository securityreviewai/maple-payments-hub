package com.maple.service.approval;

import com.maple.model.HolidayCalendar;
import com.maple.repository.HolidayCalendarRepository;
import com.maple.repository.HolidayDateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Resolves whether an approval action is allowed on the current calendar day for a holiday calendar.
 */
@Service
public class HolidayCalendarService {

    private final HolidayCalendarRepository holidayCalendarRepository;
    private final HolidayDateRepository holidayDateRepository;

    @Value("${maple.approval.block-weekends:true}")
    private boolean blockWeekends;

    @Autowired
    public HolidayCalendarService(HolidayCalendarRepository holidayCalendarRepository,
                                  HolidayDateRepository holidayDateRepository) {
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.holidayDateRepository = holidayDateRepository;
    }

    /**
     * @throws ApprovalRulesViolationException when approvals must not proceed (holiday or weekend when configured)
     */
    public void assertApprovalAllowed(UUID calendarId, OffsetDateTime decisionTime) {
        if (calendarId == null) {
            return;
        }
        HolidayCalendar cal = holidayCalendarRepository.findById(calendarId)
                .orElseThrow(() -> new ApprovalRulesViolationException("Unknown holiday calendar: " + calendarId));
        ZoneId zone = ZoneId.of(cal.getZoneId());
        LocalDate day = decisionTime.atZoneSameInstant(zone).toLocalDate();
        if (blockWeekends) {
            DayOfWeek dow = day.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                throw new ApprovalRulesViolationException("Approvals are not permitted on weekends");
            }
        }
        if (holidayDateRepository.existsByCalendarIdAndDate(calendarId, day)) {
            throw new ApprovalRulesViolationException("Approvals are not permitted on configured holidays");
        }
    }

    public boolean isBlockedDay(UUID calendarId, OffsetDateTime decisionTime) {
        try {
            assertApprovalAllowed(calendarId, decisionTime);
            return false;
        } catch (ApprovalRulesViolationException e) {
            return true;
        }
    }
}
