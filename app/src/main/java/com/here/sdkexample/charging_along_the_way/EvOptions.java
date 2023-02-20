package com.here.sdkexample.charging_along_the_way;

import com.here.sdk.routing.ChargingConnectorType;
import com.here.sdk.routing.OptimizationMode;
import com.here.time.Duration;

import java.util.Date;
import java.util.Map;

public class EvOptions {
    Date departureTime;
    Date arrivalTime;
    OptimizationMode routingMode;
    Integer alternatives;
    Map<Integer, Double> freeFlowSpeedTable;
    Map<Integer, Double> trafficSpeedTable;
    Integer ascent;
    Double descent;
    Double auxiliaryConsumption;
    Integer initialCharge;
    Integer maxCharge;
    Map<Double, Double> chargingCurve;
    Double maxChargingVoltage;
    Double maxChargingCurrent;
    Integer maxChargeAfterChargingStation;
    Double minChargeAtChargingStation;
    Double minChargeAtDestination;
    Duration chargingSetupDuration;
    ChargingConnectorType connectorTypes;
}
