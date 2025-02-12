package com.rh;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/bms")
public class BMSResource {

    @Inject
    BatterySimulator batterySimulator;

    @Path("/enableVoltageDrop/{batteryId}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String enableVoltageDrop(Integer batteryId) {
        this.batterySimulator.getAnomalyVoltageDropEnabled().put(batteryId, true);
        return "Enabled Voltage drop";
    }

    @Path("/disableVoltageDrop/{batteryId}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String disableVoltageDrop(Integer batteryId) {
        this.batterySimulator.getAnomalyVoltageDropEnabled().put(batteryId, false);
        return "Disabled Voltage drop";
    }
}
