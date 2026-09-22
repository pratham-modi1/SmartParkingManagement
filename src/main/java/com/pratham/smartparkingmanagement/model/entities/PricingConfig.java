package com.pratham.smartparkingmanagement.model.entities;

import java.math.BigDecimal;

public class PricingConfig {
    private int configId;
    private BigDecimal baseFee;
    private int baseHours;
    private BigDecimal extraHourFee;
    private BigDecimal lostTicketPenalty;

    public PricingConfig() {}

    public PricingConfig(int configId, BigDecimal baseFee, int baseHours,
                          BigDecimal extraHourFee, BigDecimal lostTicketPenalty) {
        this.configId = configId;
        this.baseFee = baseFee;
        this.baseHours = baseHours;
        this.extraHourFee = extraHourFee;
        this.lostTicketPenalty = lostTicketPenalty;
    }

    public int getConfigId() { return configId; }
    public void setConfigId(int configId) { this.configId = configId; }
    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }
    public int getBaseHours() { return baseHours; }
    public void setBaseHours(int baseHours) { this.baseHours = baseHours; }
    public BigDecimal getExtraHourFee() { return extraHourFee; }
    public void setExtraHourFee(BigDecimal extraHourFee) { this.extraHourFee = extraHourFee; }
    public BigDecimal getLostTicketPenalty() { return lostTicketPenalty; }
    public void setLostTicketPenalty(BigDecimal lostTicketPenalty) { this.lostTicketPenalty = lostTicketPenalty; }
}