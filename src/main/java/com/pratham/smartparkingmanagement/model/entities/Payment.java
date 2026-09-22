package com.pratham.smartparkingmanagement.model.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.pratham.smartparkingmanagement.model.enums.PaymentStatus;

public class Payment {
    private int paymentId;
    private int ticketId;
    private BigDecimal amount;
    private PaymentStatus status;
    private LocalDateTime paidAt; // nullable until SUCCESS

    public Payment() {}

    public Payment(int paymentId, int ticketId, BigDecimal amount,
                    PaymentStatus status, LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
    }

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }
    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
}