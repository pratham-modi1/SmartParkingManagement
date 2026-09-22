package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.model.entities.PricingConfig;

public interface PricingConfigDao {

    PricingConfig getCurrentConfig();

    void update(PricingConfig pricingConfig);
}