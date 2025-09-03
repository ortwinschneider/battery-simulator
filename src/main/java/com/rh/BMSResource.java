package com.rh;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/simulator")
public class BMSResource {

    @Inject
    BatterySimulator batterySimulator;

    @Path("/enableBatteryTempAnomaly/{batteryId}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String enableBatteryTempAnomaly(Integer batteryId) {
        this.batterySimulator.getAnomalyBatteryTempEnabled().put(batteryId, true);
        return "Enabled Battery temperature anomaly";
    }

    @Path("/disableBatteryTempAnomaly/{batteryId}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String disableBatteryTempAnomaly(Integer batteryId) {
        this.batterySimulator.getAnomalyBatteryTempEnabled().put(batteryId, false);
        return "Disabled Battery temperature anomaly";
    }

}
