package com.pratham.smartparkingmanagement.model.enums;

public enum VehicleType {
    TWO_WHEELER("2W"),
    FOUR_WHEELER("4W"),
    EV("EV");

    private final String dbCode;

    VehicleType(String dbCode) {
        this.dbCode = dbCode;
    }

    public String getDbCode() {
        return dbCode;
    }

    public static VehicleType fromDbCode(String code) {
        for (VehicleType vt : values()) {
            if (vt.dbCode.equals(code)) return vt;
        }
        throw new IllegalArgumentException("Unknown vehicle type code: " + code);
    }
}