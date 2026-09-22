package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.model.entities.ParkingLot;
import java.util.List;

public interface ParkingLotDao {
    void create(ParkingLot parkingLot);
    ParkingLot findById(int parkingLotId);
    List<ParkingLot> findAll();
    void update(ParkingLot parkingLot);
    void delete(int parkingLotId);
}