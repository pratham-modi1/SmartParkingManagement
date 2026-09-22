package com.pratham.smartparkingmanagement.model.entities;

import com.pratham.smartparkingmanagement.model.enums.VehicleType;

public class Vehicle {
    private int vehicleId;
    private String plateNumber;
    private VehicleType vehicleType;
    private Integer ownerId; // nullable — walk-ins have no owner

    public Vehicle() {}

    public Vehicle(int vehicleId, String plateNumber, VehicleType vehicleType, Integer ownerId) {
        this.vehicleId = vehicleId;
        this.plateNumber = plateNumber;
        this.vehicleType = vehicleType;
        this.ownerId = ownerId;
    }

    public int getVehicleId() { return vehicleId; }
    public void setVehicleId(int vehicleId) { this.vehicleId = vehicleId; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public Integer getOwnerId() { return ownerId; }
    public void setOwnerId(Integer ownerId) { this.ownerId = ownerId; }
}