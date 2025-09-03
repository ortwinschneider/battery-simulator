package com.rh;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

public class MCPTools {
    

    @Inject
    BatterySimulator batterySimulator;

    @Tool(name="add-battery-simulation", description = "Add a new battery simulation thread for a specific batteryId")
    public String addSimulationThread(@ToolArg(description = "The battery ID as Integer value") Integer batteryId) {
        batterySimulator.addSimulationThread(batteryId);
        return "Added simulation thread for batteryId: "+batteryId;
    }

    @Tool(name="enable-battery-temperature-anomaly", description = "Simulating a battery temperature increase through an anomaly for a battery simulation thread with a specific batteryId")
    public String enableBatteryTempAnomaly(@ToolArg(description = "The battery ID as Integer value") Integer batteryId) {
        this.batterySimulator.getAnomalyBatteryTempEnabled().put(batteryId, true);
        return "Enabled Battery temperature anomaly for batteryId: "+batteryId;
    }
}
