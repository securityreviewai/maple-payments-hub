package com.maple.controller.api.v1;

import com.maple.dto.ApprovalAmountTierDto;
import com.maple.dto.HolidayCalendarDto;
import com.maple.dto.HolidayDateDto;
import com.maple.model.ApprovalAmountTier;
import com.maple.model.HolidayCalendar;
import com.maple.model.HolidayDate;
import com.maple.repository.ApprovalAmountTierRepository;
import com.maple.repository.HolidayCalendarRepository;
import com.maple.repository.HolidayDateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only API for approval amount tiers and holiday calendars (rules engine configuration).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Approval rules", description = "Amount thresholds, holiday calendars")
@SecurityRequirement(name = "OAuth2")
@SecurityRequirement(name = "BearerAuth")
public class ApprovalRulesController {

    private final ApprovalAmountTierRepository approvalAmountTierRepository;
    private final HolidayCalendarRepository holidayCalendarRepository;
    private final HolidayDateRepository holidayDateRepository;

    @Autowired
    public ApprovalRulesController(ApprovalAmountTierRepository approvalAmountTierRepository,
                                   HolidayCalendarRepository holidayCalendarRepository,
                                   HolidayDateRepository holidayDateRepository) {
        this.approvalAmountTierRepository = approvalAmountTierRepository;
        this.holidayCalendarRepository = holidayCalendarRepository;
        this.holidayDateRepository = holidayDateRepository;
    }

    @GetMapping("/approval-rules/tiers")
    @Operation(summary = "List amount tiers", description = "Returns configured amount thresholds and required approvers by currency.")
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') "
            + "or hasAuthority('ROLE_TREASURY_MANAGER') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<List<ApprovalAmountTierDto>> listTiers(
            @Parameter(description = "ISO currency filter (default USD)")
            @RequestParam(defaultValue = "USD") String currency) {
        List<ApprovalAmountTier> tiers = approvalAmountTierRepository.findByCurrencyOrderBySortOrderAsc(
                currency.toUpperCase());
        return ResponseEntity.ok(tiers.stream().map(this::toTierDto).collect(Collectors.toList()));
    }

    @GetMapping("/holiday-calendars/{id}")
    @Operation(summary = "Get holiday calendar", description = "Returns calendar metadata and observed holiday dates.")
    @PreAuthorize("hasAuthority('SCOPE_payments:read') or hasAuthority('ROLE_TREASURY_OPS') "
            + "or hasAuthority('ROLE_TREASURY_MANAGER') or hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<HolidayCalendarDto> getCalendar(@PathVariable UUID id) {
        HolidayCalendar cal = holidayCalendarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Calendar not found: " + id));
        List<HolidayDate> dates = holidayDateRepository.findByCalendarIdOrderByHolidayDateAsc(id);
        return ResponseEntity.ok(HolidayCalendarDto.builder()
                .id(cal.getId())
                .name(cal.getName())
                .zoneId(cal.getZoneId())
                .dates(dates.stream().map(this::toHolidayDateDto).collect(Collectors.toList()))
                .build());
    }

    private ApprovalAmountTierDto toTierDto(ApprovalAmountTier t) {
        return ApprovalAmountTierDto.builder()
                .id(t.getId())
                .currency(t.getCurrency())
                .minAmountCents(t.getMinAmountCents())
                .maxAmountCents(t.getMaxAmountCents())
                .requiredApprovers(t.getRequiredApprovers())
                .sortOrder(t.getSortOrder())
                .build();
    }

    private HolidayDateDto toHolidayDateDto(HolidayDate h) {
        return HolidayDateDto.builder()
                .id(h.getId())
                .holidayDate(h.getHolidayDate())
                .label(h.getLabel())
                .build();
    }
}
