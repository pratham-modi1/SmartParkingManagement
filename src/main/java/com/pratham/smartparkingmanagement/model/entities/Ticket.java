package com.pratham.smartparkingmanagement.model.entities;

import com.pratham.smartparkingmanagement.model.enums.TicketStatus;

import java.time.LocalDateTime;

public class Ticket {

    private int ticketId;
    private int vehicleId;
    private int slotId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private TicketStatus status;
    private int createdByUserId;
    private boolean overstayedFlag;
    private boolean lostTicketFlag;

    public Ticket() {
    }

    public Ticket(int ticketId, int vehicleId, int slotId,
                  LocalDateTime entryTime, LocalDateTime exitTime,
                  TicketStatus status, int createdByUserId,
                  boolean overstayedFlag, boolean lostTicketFlag) {
        this.ticketId = ticketId;
        this.vehicleId = vehicleId;
        this.slotId = slotId;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.status = status;
        this.createdByUserId = createdByUserId;
        this.overstayedFlag = overstayedFlag;
        this.lostTicketFlag = lostTicketFlag;
    }

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    public int getSlotId() {
        return slotId;
    }

    public void setSlotId(int slotId) {
        this.slotId = slotId;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public int getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(int createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public boolean isOverstayedFlag() {
        return overstayedFlag;
    }

    public void setOverstayedFlag(boolean overstayedFlag) {
        this.overstayedFlag = overstayedFlag;
    }

    public boolean isLostTicketFlag() {
        return lostTicketFlag;
    }

    public void setLostTicketFlag(boolean lostTicketFlag) {
        this.lostTicketFlag = lostTicketFlag;
    }
}