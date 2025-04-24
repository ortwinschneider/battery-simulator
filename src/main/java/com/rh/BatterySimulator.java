package com.rh;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class BatterySimulator {

    @ConfigProperty(name = "mqtt.broker.url")
    String mqttBrokerUrl;

    @ConfigProperty(name = "mqtt.topic")
    String mqttTopic;

    @ConfigProperty(name = "data.gen.interval")
    int dataGenIntervall;

    @ConfigProperty(name = "battery.count")
    int batteryCount;

    @ConfigProperty(name = "battery.voltage.max")
    double batteryVoltageMax;

    @ConfigProperty(name = "battery.capacity")
    double batteryCapacity;

    @ConfigProperty(name = "wheel.speed.max")
    double wheelSpeedMax;

    @ConfigProperty(name = "car.weight")
    double carWeight;

    @ConfigProperty(name = "car.tire.cr")
    double carTireCr;

    @ConfigProperty(name = "car.width")
    double carWidth;

    @ConfigProperty(name = "car.height")
    double carHeight;

    @ConfigProperty(name = "car.drag.cw")
    double carDragCw;

    @Inject
    BatteryDataSimulation batteryDataSimulation;

    private MqttClient mqttClient;
    private ScheduledExecutorService scheduler;
    private ConcurrentHashMap<Integer, Double> currentBatteryCapacity;
    private ConcurrentHashMap<Integer, Double> currentDrivingDistance;
    private ConcurrentHashMap<Integer, Double> currentBatteryVoltage;
    private ConcurrentHashMap<Integer, Double> currentAmbientTemperature;
    private ConcurrentHashMap<Integer, Double> currentBatteryTemperature;
    private ConcurrentHashMap<Integer, Double> currentSpeed;
    private NavigableMap<Double, Double> speedEnergylookupTable;
    private ConcurrentHashMap<Integer, Boolean> anomalyVoltageDropEnabled;


    public ConcurrentHashMap<Integer, Boolean> getAnomalyVoltageDropEnabled() {
        return anomalyVoltageDropEnabled;
    }

    private void initializeApp() throws MqttException {

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(300);

        mqttClient = new MqttClient(mqttBrokerUrl, MqttClient.generateClientId());
        mqttClient.connect(options);

        currentBatteryCapacity = new ConcurrentHashMap<>();
        currentDrivingDistance = new ConcurrentHashMap<>();
        currentSpeed = new ConcurrentHashMap<>();
        currentBatteryVoltage = new ConcurrentHashMap<>();
        currentAmbientTemperature = new ConcurrentHashMap<>();
        currentBatteryTemperature = new ConcurrentHashMap<>();
        anomalyVoltageDropEnabled = new ConcurrentHashMap<>();
        speedEnergylookupTable = new TreeMap<>();

        // Lookup table data is from Tesla Model S (km/h : kwh)
        speedEnergylookupTable.put(0.0, 0.0);
        speedEnergylookupTable.put(10.0, 2.0);
        speedEnergylookupTable.put(20.0, 3.0);
        speedEnergylookupTable.put(30.0, 4.1);
        speedEnergylookupTable.put(40.0, 5.0);
        speedEnergylookupTable.put(50.0, 6.3);
        speedEnergylookupTable.put(60.0, 7.8);
        speedEnergylookupTable.put(70.0, 10.0);
        speedEnergylookupTable.put(80.0, 12.5);
        speedEnergylookupTable.put(90.0, 15.0);
        speedEnergylookupTable.put(100.0, 18.0);
        speedEnergylookupTable.put(110.0, 23.0);
        speedEnergylookupTable.put(120.0, 27.5);
        speedEnergylookupTable.put(130.0, 32.0);
        speedEnergylookupTable.put(140.0, 38.0);
        speedEnergylookupTable.put(150.0, 45.0);
        speedEnergylookupTable.put(160.0, 52.0);
        speedEnergylookupTable.put(170.0, 60.0);
        speedEnergylookupTable.put(180.0, 70.0);
        speedEnergylookupTable.put(190.0, 81.0);
        speedEnergylookupTable.put(200.0, 92.5);
        speedEnergylookupTable.put(210.0, 104.0);
        speedEnergylookupTable.put(220.0, 117.0);
        speedEnergylookupTable.put(230.0, 133.0);
        speedEnergylookupTable.put(240.0, 148.0);
        speedEnergylookupTable.put(250.0, 162.0);

        for (int i = 0; i < batteryCount; i++) {
            initializeBatterySimulationData(i+1);
        }

        scheduler = Executors.newScheduledThreadPool(batteryCount);
        for (int i = 0; i < batteryCount; i++) {
            final int batteryId = i + 1;
            scheduler.scheduleAtFixedRate(() -> simulateDrivingElectricVehicle(batteryId), 0, dataGenIntervall, TimeUnit.SECONDS);
        }
    }

    private void initializeBatterySimulationData(int batteryId){
        System.out.println("Initialize battery simulation for batteryId: "+batteryId);

        currentBatteryCapacity.put(batteryId, batteryCapacity);
        currentDrivingDistance.put(batteryId, 0.0);
        currentBatteryVoltage.put(batteryId, batteryVoltageMax);
        currentSpeed.put(batteryId, wheelSpeedMax * 0.5);
        currentAmbientTemperature.put(batteryId, 18.3);
        currentBatteryTemperature.put(batteryId,25.4);
        anomalyVoltageDropEnabled.put(batteryId, false);
    }

    void onStart(@Observes StartupEvent event) {
        try {
            this.initializeApp();
        } catch (MqttException e) {
            e.printStackTrace();
            while(!this.mqttClient.isConnected()){
                try {
                    System.out.println("Waiting 5 s and trying to connect to MQTT broker...");
                    Thread.sleep(5000);
                    this.initializeApp();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void simulateDrivingElectricVehicle(int batteryId) {
        
        Random random = new Random();
        double ambientTemperature;
        double wheelSpeed = currentSpeed.get(batteryId);

        // generate the speed in meters per second and also use km/h
        if (random.nextBoolean()) { 
            wheelSpeed = currentSpeed.put(batteryId, currentSpeed.get(batteryId) + random.nextDouble(3));
            if (wheelSpeed > wheelSpeedMax)
                wheelSpeed = wheelSpeedMax;
        } else {
            wheelSpeed = currentSpeed.put(batteryId, currentSpeed.get(batteryId) - random.nextDouble(3));
            if (wheelSpeed < 0)
            wheelSpeed = 0.0; 
        }
        
        // speed in km/h 
        double kmh = wheelSpeed * 3.6;
        
        // generate the ambient temperature 
        if (random.nextBoolean()) { 
            ambientTemperature = currentAmbientTemperature.put(batteryId, currentAmbientTemperature.get(batteryId) + random.nextDouble(0.5));
        } else {
            ambientTemperature = currentAmbientTemperature.put(batteryId, currentAmbientTemperature.get(batteryId) - random.nextDouble(0.5));
        }   

        // calculate the total driving distance in km
        double distance = currentDrivingDistance.get(batteryId) + ((wheelSpeed * dataGenIntervall) / 1000);
        currentDrivingDistance.put(batteryId, distance);

        // calculate the energy consumption in kwh for the given speed and the given time intervall
        double energyConsumption = (calculateEnergyConsumption(kmh) / 3600) * dataGenIntervall;

        // Update the current battery capacity by subtracting the current energy consumption, rolling resistance and air resistance for the given time intervall
        double current = currentBatteryCapacity.get(batteryId) - energyConsumption - calculateAirResistance(wheelSpeed, dataGenIntervall) - calculateRollingResistance(wheelSpeed, dataGenIntervall);
        currentBatteryCapacity.put(batteryId, current);

        // Update the SOC in percentage
        double currentStateOfCharge = current / batteryCapacity;

        // calculate the current ampere based on the needed energy in Watt and current voltage
        double batteryCurrent = (calculateEnergyConsumption(kmh) * 1000) / currentBatteryVoltage.get(batteryId);

        // generate the battery temperature, voltage, degradation, 
        batteryDataSimulation.simulateBatteryData(batteryCurrent, 1, currentStateOfCharge);

        // get the state of health
        double stateOfHealth = batteryDataSimulation.getStateOfHealth();

        // get the battery temperature
        double batteryTemperature = batteryDataSimulation.getBatteryTemperature();

        // get the batteryVoltage
        if (anomalyVoltageDropEnabled.get(batteryId)) {
            //TODO: Implement anomaly behavior
            currentBatteryVoltage.put(batteryId, currentBatteryVoltage.get(batteryId) - 2.83);
        } else {
            currentBatteryVoltage.put(batteryId, batteryDataSimulation.getVoltage());
        }

        // create the JSON string (payload)
        String payload = String.format(
            "{\"batteryId\":%d,\"stateOfCharge\":%.4f,\"stateOfHealth\":%.4f,\"batteryCurrent\":%.2f,\"batteryVoltage\":%.2f,\"kmh\":%.2f,\"distance\":%.2f,\"batteryTemp\":%.2f,\"ambientTemp\":%.2f}",
            batteryId, currentStateOfCharge, stateOfHealth, batteryCurrent, currentBatteryVoltage.get(batteryId), kmh, distance, batteryTemperature, ambientTemperature
        );

        System.out.println(payload);

        // Send the JSON paylod over MQTT
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            mqttClient.publish(mqttTopic + batteryId, message);
        } catch (MqttException e) {
            e.printStackTrace();
        }

        if(stateOfHealth <= 0.5 || currentStateOfCharge <= 0.05){
            System.out.println("INITIALIZING SIMULATION DATA...");
            System.out.println("stateOfHealth: "+stateOfHealth);
            System.out.println("currentStateOfCharge: "+currentStateOfCharge);
            this.initializeBatterySimulationData(batteryId);
            batteryDataSimulation.initializeSimulationData();
        }

    }

    // The function is returning kwh
    private double calculateAirResistance(double speed, int timeInterval) {
        // Example: FLuft = 1,2 kg/m3 /2 * 0,3 * 2,4m2 * (30m/s)2= 388,8 N
        double ar = (1.2 / 2) * carDragCw * (carWidth * carHeight) * (speed * speed);
        double ar_kwh = (ar * (speed * timeInterval )) / 3600000;
        return ar_kwh;
    }

    // The function is returning kwh
    private double calculateRollingResistance(double speed, int timeInterval) {
        // weight * base gravity * tire cr value
        double rr = carWeight * 9.81 * carTireCr;
        double rr_kwh = (rr * (speed * timeInterval )) / 3600000;
        return rr_kwh;
    }

    // The function is returning kwh
    private double calculateEnergyConsumption(double speed) {

        // Check if the speed is directly in the table
        if (speedEnergylookupTable.containsKey(speed)) {
            return speedEnergylookupTable.get(speed);
        }

        // Find the closest lower and higher keys
        Double lowerKey = speedEnergylookupTable.floorKey(speed);
        Double higherKey = speedEnergylookupTable.ceilingKey(speed);

        if (lowerKey == null) {
            return speedEnergylookupTable.get(higherKey); // Only higher key exists
        }
        if (higherKey == null) {
            return speedEnergylookupTable.get(lowerKey); // Only lower key exists
        }

        // Interpolate between lower and higher values
        double lowerValue = speedEnergylookupTable.get(lowerKey);
        double higherValue = speedEnergylookupTable.get(higherKey);
        return interpolate(lowerKey, lowerValue, higherKey, higherValue, speed);

    }

    private double interpolate(double x1, double y1, double x2, double y2, double x) {
        return y1 + ((y2 - y1) / (x2 - x1)) * (x - x1);
    }

    void onStop(@Observes io.quarkus.runtime.ShutdownEvent event) {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
