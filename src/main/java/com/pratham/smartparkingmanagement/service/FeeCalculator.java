package com.pratham.smartparkingmanagement.service;

import com.pratham.smartparkingmanagement.model.entities.PricingConfig;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public class FeeCalculator {

    public static BigDecimal calculateFee(
            LocalDateTime entryTime,
            LocalDateTime exitTime,
            PricingConfig config,
            boolean lostTicket) {

        // 1. Calculate total parking duration in minutes
        long minutes = Duration.between(entryTime, exitTime).toMinutes();

        // 2. Base parking duration in minutes
        long baseMinutes = config.getBaseHours() * 60L;

        // 3. Start with the base fee
        BigDecimal fee = config.getBaseFee();

        // 4. If parking exceeds the base duration,
        //    calculate the extra started hours
        if (minutes > baseMinutes) {

            long extraMinutes = minutes - baseMinutes;

            // Round UP because every started hour is charged completely
            long extraHours = (extraMinutes + 59) / 60;

            BigDecimal extraFee = config.getExtraHourFee()
                    .multiply(BigDecimal.valueOf(extraHours));

            fee = fee.add(extraFee);
        }

        // 5. Add lost-ticket penalty if applicable
        if (lostTicket) {
            fee = fee.add(config.getLostTicketPenalty());
        }

        return fee;
    }
}