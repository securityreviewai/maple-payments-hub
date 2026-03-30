package com.maple.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Amount tier defining required approvers for a currency range")
public class ApprovalAmountTierDto {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("minAmountCents")
    private Long minAmountCents;

    @JsonProperty("maxAmountCents")
    private Long maxAmountCents;

    @JsonProperty("requiredApprovers")
    private Integer requiredApprovers;

    @JsonProperty("sortOrder")
    private Integer sortOrder;
}
