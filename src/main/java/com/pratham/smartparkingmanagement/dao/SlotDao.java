package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.model.entities.Slot;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;

import java.sql.Connection;
import java.util.List;

public interface SlotDao {

    void create(Slot slot);

    Slot findById(int slotId);

    List<Slot> findAll();

    void update(Slot slot);

    void update(Connection conn, Slot slot) throws java.sql.SQLException;

    void delete(int slotId);

    List<Slot> findAvailableByLotAndType(int lotId, VehicleType type);

    Slot findFirstAvailableForUpdate(
            Connection conn,
            int lotId,
            VehicleType type
    ) throws java.sql.SQLException;
}