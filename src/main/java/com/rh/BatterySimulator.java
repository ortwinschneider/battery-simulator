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
    private ConcurrentHashMap<Integer, Double> currentLoad;
    private ConcurrentHashMap<Integer, Double> currentDrivingDistance;
    private ConcurrentHashMap<Integer, Double> currentBatteryVoltage;
    private ConcurrentHashMap<Integer, Double> currentAmbientTemperature;
    private ConcurrentHashMap<Integer, Double> currentBatteryTemperature;
    private ConcurrentHashMap<Integer, Double> currentSpeed;
    private NavigableMap<Double, Double> speedEnergylookupTable;
    private ConcurrentHashMap<Integer, Boolean> anomalyBatteryTempEnabled;


    public ConcurrentHashMap<Integer, Boolean> getAnomalyBatteryTempEnabled() {
        return anomalyBatteryTempEnabled;
    }

    public void restartBatterySimulation(int batteryId) {
        this.initializeBatterySimulationData(batteryId);
        batteryDataSimulation.initializeSimulationData();
    }

    public String getBatteryDataForId(int batteryId) {
        return String.format(
            "{\"batteryId\":%d,\"stateOfCharge\":%.4f,\"stateOfHealth\":%.4f,\"batteryVoltage\":%.2f,\"kmh\":%.2f,\"distance\":%.2f,\"batteryTemp\":%.2f,\"ambientTemp\":%.2f,\"currentLoad\":%.2f}",
            batteryId, 
            currentBatteryCapacity.get(batteryId) / batteryCapacity, 
            batteryDataSimulation.getStateOfHealth(), 
            currentBatteryVoltage.get(batteryId), 
            currentSpeed.get(batteryId) * 3.6, 
            currentDrivingDistance.get(batteryId), 
            batteryDataSimulation.getBatteryTemperature(), 
            currentAmbientTemperature.get(batteryId), 
            currentLoad.get(batteryId)
        );
    }

    private void initializeApp() throws MqttException {

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(300);

        mqttClient = new MqttClient(mqttBrokerUrl, MqttClient.generateClientId());
        mqttClient.connect(options);

        currentBatteryCapacity = new ConcurrentHashMap<>();
        currentLoad = new ConcurrentHashMap<>();
        currentDrivingDistance = new ConcurrentHashMap<>();
        currentSpeed = new ConcurrentHashMap<>();
        currentBatteryVoltage = new ConcurrentHashMap<>();
        currentAmbientTemperature = new ConcurrentHashMap<>();
        currentBatteryTemperature = new ConcurrentHashMap<>();
        anomalyBatteryTempEnabled = new ConcurrentHashMap<>();
        speedEnergylookupTable = new TreeMap<>();

        // Lookup table data is from Tesla Model S (km/h : kw)
        speedEnergylookupTable.put(0.0, 0.048);
        speedEnergylookupTable.put(1.0, 0.096);
        speedEnergylookupTable.put(2.0, 0.144);
        speedEnergylookupTable.put(3.0, 0.19);
        speedEnergylookupTable.put(4.0, 0.236);
        speedEnergylookupTable.put(5.0, 0.259);
        speedEnergylookupTable.put(6.0, 0.288);
        speedEnergylookupTable.put(7.0, 0.366);
        speedEnergylookupTable.put(8.0, 0.430);
        speedEnergylookupTable.put(9.0, 0.489);
        speedEnergylookupTable.put(10.0, 0.500);
        speedEnergylookupTable.put(11.0, 0.520);
        speedEnergylookupTable.put(12.0, 0.570);
        speedEnergylookupTable.put(13.0, 0.600);
        speedEnergylookupTable.put(14.0, 0.650);
        speedEnergylookupTable.put(15.0, 0.720);
        speedEnergylookupTable.put(16.0, 0.776);
        speedEnergylookupTable.put(17.0, 0.810);
        speedEnergylookupTable.put(18.0, 0.860);
        speedEnergylookupTable.put(19.0, 0.920);
        speedEnergylookupTable.put(20.0, 0.960);
        speedEnergylookupTable.put(21.0, 1.0);
        speedEnergylookupTable.put(22.0, 1.056);
        speedEnergylookupTable.put(23.0, 1.20);
        speedEnergylookupTable.put(24.0, 1.35);
        speedEnergylookupTable.put(25.0, 1.44);

        for (int i = 0; i < batteryCount; i++) {
            initializeBatterySimulationData(i+1);
        }

        scheduler = Executors.newScheduledThreadPool(batteryCount);
        for (int i = 0; i < batteryCount; i++) {
            final int batteryId = i + 1;
            scheduler.scheduleAtFixedRate(() -> simulateDrivingElectricVehicle(batteryId), 0, dataGenIntervall, TimeUnit.SECONDS);
        }
    }

    public void addSimulationThread(int batteryId) {
        initializeBatterySimulationData(batteryId);
        scheduler.scheduleAtFixedRate(() -> simulateDrivingElectricVehicle(batteryId), 0, dataGenIntervall, TimeUnit.SECONDS);
    }

    private void initializeBatterySimulationData(int batteryId){
        System.out.println("Initialize battery simulation for batteryId: "+batteryId);

        currentBatteryCapacity.put(batteryId, batteryCapacity);
        currentDrivingDistance.put(batteryId, 0.0);
        currentBatteryVoltage.put(batteryId, batteryVoltageMax);
        currentSpeed.put(batteryId, wheelSpeedMax * 0.5);
        currentAmbientTemperature.put(batteryId, 18.3);
        currentBatteryTemperature.put(batteryId,25.4);
        anomalyBatteryTempEnabled.put(batteryId, false);
        currentLoad.put(batteryId, 100.0);
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
            wheelSpeed = currentSpeed.get(batteryId) + random.nextDouble(2); 
            if (wheelSpeed > wheelSpeedMax)
                wheelSpeed = wheelSpeedMax;
            currentSpeed.put(batteryId, wheelSpeed); 
        } else {
            wheelSpeed = currentSpeed.get(batteryId) - random.nextDouble(2);
            if (wheelSpeed < 0)
                wheelSpeed = 0.0; 
            currentSpeed.put(batteryId, wheelSpeed);     
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
        double currentCapacity = currentBatteryCapacity.get(batteryId) - energyConsumption - calculateRollingResistance(wheelSpeed, dataGenIntervall, batteryId) - calculateAirResistance(wheelSpeed, dataGenIntervall);
        currentBatteryCapacity.put(batteryId, currentCapacity);

        // Update the SOC in percentage
        double currentStateOfCharge = currentCapacity / batteryCapacity;

        // calculate the current ampere based on the needed energy in Watt and current voltage
        double batteryCurrent = ((calculateEnergyConsumption(kmh) * 1000) + calculateRollingResistanceInWatt(wheelSpeed, dataGenIntervall, batteryId))/ currentBatteryVoltage.get(batteryId);

        // generate the battery temperature, voltage, degradation, 
        batteryDataSimulation.simulateBatteryData(batteryCurrent, 1, currentStateOfCharge, anomalyBatteryTempEnabled.get(batteryId));

        // get the state of health
        double stateOfHealth = batteryDataSimulation.getStateOfHealth();

        // get the battery temperature
        double batteryTemperature = batteryDataSimulation.getBatteryTemperature();

        currentBatteryVoltage.put(batteryId, batteryDataSimulation.getVoltage());

        // create the JSON string (payload)
        String payload = String.format(
            "{\"batteryId\":%d,\"stateOfCharge\":%.4f,\"stateOfHealth\":%.4f,\"batteryCurrent\":%.2f,\"batteryVoltage\":%.2f,\"kmh\":%.2f,\"distance\":%.2f,\"batteryTemp\":%.2f,\"ambientTemp\":%.2f,\"currentLoad\":%.2f}",
            batteryId, currentStateOfCharge, stateOfHealth, batteryCurrent, currentBatteryVoltage.get(batteryId), kmh, distance, batteryTemperature, ambientTemperature, currentLoad.get(batteryId)
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
    private double calculateRollingResistance(double speed, int timeInterval, int batteryId) {
        // weight * base gravity * tire cr value
        double rr = (carWeight + currentLoad.get(batteryId)) * 9.81 * carTireCr;
        double rr_kwh = (rr * speed * timeInterval ) / 3600000;
        return rr_kwh;
    }

    private double calculateRollingResistanceInWatt(double speed, int timeInterval, int batteryId) {
        // weight * base gravity * tire cr value
        double rr = (carWeight + currentLoad.get(batteryId)) * 9.81 * carTireCr;
        double rr_w = (rr * speed);
        return rr_w;
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
