package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.model.entities.PricingConfig;

import java.sql.*;

public class PricingConfigDaoImpl implements PricingConfigDao {

    // V1 assumption: exactly one row exists in PricingConfig — fetch whichever row is present.
    @Override
    public PricingConfig getCurrentConfig() {
        String sql = "SELECT * FROM PricingConfig LIMIT 1";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            if (rs.next()) {
                return new PricingConfig(
                        rs.getInt("config_id"),
                        rs.getBigDecimal("base_fee"),
                        rs.getInt("base_hours"),
                        rs.getBigDecimal("extra_hour_fee"),
                        rs.getBigDecimal("lost_ticket_penalty")
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch pricing config", e);
        }
        throw new IllegalStateException("No PricingConfig row found — did you seed it?");
    }

    @Override
    public void update(PricingConfig config) {
        String sql = "UPDATE PricingConfig SET base_fee=?, base_hours=?, extra_hour_fee=?, lost_ticket_penalty=? " +
                     "WHERE config_id=?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setBigDecimal(1, config.getBaseFee());
            statement.setInt(2, config.getBaseHours());
            statement.setBigDecimal(3, config.getExtraHourFee());
            statement.setBigDecimal(4, config.getLostTicketPenalty());
            statement.setInt(5, config.getConfigId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update pricing config", e);
        }
    }
}