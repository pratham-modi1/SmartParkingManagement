package com.pratham.smartparkingmanagement.model.entities;

import com.pratham.smartparkingmanagement.model.enums.SlotStatus;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;

public class Slot {
    private int slotId;
    private String slotLabel;
    private VehicleType vehicleType;
    private SlotStatus status;
    private int parkingLotId;

    public Slot() {}

    public Slot(int slotId, String slotLabel, VehicleType vehicleType,
                SlotStatus status, int parkingLotId) {
        this.slotId = slotId;
        this.slotLabel = slotLabel;
        this.vehicleType = vehicleType;
        this.status = status;
        this.parkingLotId = parkingLotId;
    }

    public int getSlotId() { return slotId; }
    public void setSlotId(int slotId) { this.slotId = slotId; }
    public String getSlotLabel() { return slotLabel; }
    public void setSlotLabel(String slotLabel) { this.slotLabel = slotLabel; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
    public int getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(int parkingLotId) { this.parkingLotId = parkingLotId; }
}