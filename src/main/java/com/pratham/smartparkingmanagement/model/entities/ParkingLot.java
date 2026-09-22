package com.pratham.smartparkingmanagement.model.entities;

import java.time.LocalDateTime;
import com.pratham.smartparkingmanagement.model.enums.LotStatus;

public class ParkingLot {
    private int parkingLotId;
    private String name;
    private String location;
    private LotStatus status;
    private LocalDateTime createdAt;

    public ParkingLot() {}

    public ParkingLot(int parkingLotId, String name, String location,
                       LotStatus status, LocalDateTime createdAt) {
        this.parkingLotId = parkingLotId;
        this.name = name;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(int parkingLotId) { this.parkingLotId = parkingLotId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LotStatus getStatus() { return status; }
    public void setStatus(LotStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}