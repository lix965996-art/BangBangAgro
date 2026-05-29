package com.farmland.intel.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlertCreateDTO {
    private Integer farmlandId;
    private String farmlandName;
    private String alertType;
    private String alertLevel;
    private BigDecimal currentValue;
    private BigDecimal thresholdMin;
    private BigDecimal thresholdMax;
    private String message;
    private String suggestion;
    private String processor;
}
