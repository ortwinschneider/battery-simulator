package com.rh;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BatteryDataSimulation {
    private final double INITIAL_TEMPERATURE = 25.0; // °C
    private final double COOLING_RATE = 0.2; // °C/s (Rate at which battery cools)
    private final double HEATING_COEFFICIENT = 0.0005; // Heating effect of current^2
    private final double INTERNAL_RESISTANCE = 0.0461; // Ohms (Simplified model)

    private final double THERMAL_RUNAWAY_THRESHOLD = 70.0; // °C
    private final double RUNAWAY_MULTIPLIER = 1.05; // Exponential heat rise in runaway
    private final double CRITICAL_FAILURE_TEMP = 150.0; // °C (Battery failure)

    private final double BASE_DEGRADATION = 0.0001; // Normal degradation per second
    private final double HIGH_TEMP_DEGRADATION = 0.0005; // Extra degradation above 45°C

    private final double NOMINAL_VOLTAGE = 400.0; // V
    private final double MIN_VOLTAGE = 300.0; // Minimum voltage when fully discharged

    private double batteryTemperature = INITIAL_TEMPERATURE;
    private boolean inThermalRunaway = false;
    private double stateOfHealth = 100.0; // Start at 100% SOH
    private double voltage = NOMINAL_VOLTAGE;

    public void simulateBatteryData(double current, int timeInterval, double stateOfCharge) {

        if (!inThermalRunaway) {
            // Joule Heating: P = I^2 * R (Power loss due to internal resistance)
            double powerDissipation = Math.pow(current, 2) * INTERNAL_RESISTANCE;

            // Temperature change: Heat from power loss - Cooling effect
            double coolingEffect = (COOLING_RATE * timeInterval) * (batteryTemperature - INITIAL_TEMPERATURE);
            double temperatureChange = (HEATING_COEFFICIENT * powerDissipation) - coolingEffect;
            
            batteryTemperature += temperatureChange;

            // State of Health Degradation
            stateOfHealth -= BASE_DEGRADATION * timeInterval; // Normal wear
            if (batteryTemperature > 45) {
                stateOfHealth -= HIGH_TEMP_DEGRADATION * timeInterval; // Faster wear at high temps
            }

            // Prevent SOH from going negative
            if (stateOfHealth < 0) stateOfHealth = 0;

            // Voltage drop due to internal resistance
            double resistance = INTERNAL_RESISTANCE + (0.02 * (1 - stateOfCharge)) + (0.0005 * (batteryTemperature - INITIAL_TEMPERATURE));
            double voltageDrop = current * resistance;
            voltage = NOMINAL_VOLTAGE - voltageDrop;
            // Adjust voltage based on SOC curve (simplified linear model)
            voltage = interpolateSOCVoltage(stateOfCharge) - voltageDrop;

            // Check for thermal runaway trigger
            if (batteryTemperature >= THERMAL_RUNAWAY_THRESHOLD) {
                inThermalRunaway = true;
                System.out.println("⚠️ WARNING: Thermal Runaway Initiated! ⚠️");
            }
        } else {
            // **Thermal Runaway Mode**
            batteryTemperature *= RUNAWAY_MULTIPLIER; // Exponential temperature rise
            stateOfHealth = 0;

            // Voltage drop due to internal resistance
            double voltageDrop = current * INTERNAL_RESISTANCE;
            voltage = NOMINAL_VOLTAGE - voltageDrop;
            // Adjust voltage based on SOC curve (simplified linear model)
            voltage = interpolateSOCVoltage(stateOfCharge) - voltageDrop;

            // Stop simulation if battery reaches failure temp
            if (batteryTemperature >= CRITICAL_FAILURE_TEMP) {
                System.out.println("🔥 BATTERY FAILURE 🔥");
            }
        }
    }

    // Simulate SOC vs Voltage curve (nonlinear)
    private double interpolateSOCVoltage(double soc) {
        if (soc >= 0.9) return 400;
        else if (soc >= 0.8) return 390;
        else if (soc >= 0.7) return 380;
        else if (soc >= 0.6) return 370;
        else if (soc >= 0.5) return 360;
        else if (soc >= 0.4) return 350;
        else if (soc >= 0.3) return 340;
        else if (soc >= 0.2) return 330;
        else if (soc >= 0.1) return 320;
        else return MIN_VOLTAGE; // Fully discharged
    }

    public double getStateOfHealth() {
        return stateOfHealth;
    }

    public double getVoltage() {
        return voltage;
    }

    public double getBatteryTemperature() {
        return batteryTemperature;
    }
    

}
