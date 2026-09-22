package com.pratham.smartparkingmanagement.dto;

import com.pratham.smartparkingmanagement.model.entities.Ticket;

import java.math.BigDecimal;

public class ExitResponse {

    public Ticket ticket;
    public BigDecimal fee;

    public ExitResponse(Ticket ticket, BigDecimal fee) {
        this.ticket = ticket;
        this.fee = fee;
    }
}