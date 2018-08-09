package org.nantipov.waschbaer.services;

import com.google.common.util.concurrent.AtomicDouble;
import com.pi4j.gpio.extension.ads.ADS1115GpioProvider;
import com.pi4j.gpio.extension.ads.ADS1115Pin;
import com.pi4j.gpio.extension.ads.ADS1x15GpioProvider;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinAnalogInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinAnalogValueChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import lombok.extern.slf4j.Slf4j;
import org.aeonbits.owner.ConfigFactory;
import org.nantipov.waschbaer.AppProperties;
import org.nantipov.waschbaer.domain.EventDTO;
import org.nantipov.waschbaer.domain.EventType;
import org.nantipov.waschbaer.domain.SensorDTO;
import org.nantipov.waschbaer.domain.SensorsDTO;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class PlantsService {

    private static final PlantsService INSTANCE = new PlantsService();

    private final BaumService baumService = BaumService.getInstance();

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final AppProperties appProperties = ConfigFactory.create(AppProperties.class);

    private final GpioController gpio = GpioFactory.getInstance();
    private ADS1115GpioProvider adsGpioProvider;

    private final ConcurrentMap<String, AtomicDouble> currentMoistureValues = new ConcurrentHashMap<>();

    private final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private final DecimalFormat percentDecimalFormat = new DecimalFormat("###.#");

    private GpioPinDigitalOutput pumpTriggerPin;

    private PlantsService() {
        try {
            initializeHardware();
        } catch (IOException | I2CFactory.UnsupportedBusNumberException e) {
            throw new IllegalStateException("Could not initialize moisture sensors", e);
        }
    }

    public static PlantsService getInstance() {
        return INSTANCE;
    }

    private void initializeHardware() throws IOException, I2CFactory.UnsupportedBusNumberException {
        pumpTriggerPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_04);

        adsGpioProvider =
                new ADS1115GpioProvider(I2CBus.BUS_1, ADS1115GpioProvider.ADS1115_ADDRESS_0x48);

        GpioPinAnalogInput moistureSensorsInputs[] = {
                gpio.provisionAnalogInputPin(adsGpioProvider, ADS1115Pin.INPUT_A0, "MoistureSensor-A0"),
                gpio.provisionAnalogInputPin(adsGpioProvider, ADS1115Pin.INPUT_A1, "MoistureSensor-A1"),
                gpio.provisionAnalogInputPin(adsGpioProvider, ADS1115Pin.INPUT_A2, "MoistureSensor-A2"),
                gpio.provisionAnalogInputPin(adsGpioProvider, ADS1115Pin.INPUT_A3, "MoistureSensor-A3"),
        };

        adsGpioProvider.setProgrammableGainAmplifier(ADS1x15GpioProvider.ProgrammableGainAmplifierValue.PGA_2_048V,
                                                     ADS1115Pin.ALL);

        adsGpioProvider.setEventThreshold(100, ADS1115Pin.ALL);
        adsGpioProvider.setMonitorInterval(100);
        GpioPinListenerAnalog listener = event -> onSensorValue(event, adsGpioProvider);

        Stream.of(moistureSensorsInputs)
              .forEach(input -> input.addListener(listener));
    }

    public void shutdown() {
        adsGpioProvider.shutdown();
        gpio.shutdown();
        executorService.shutdownNow();
    }

    public List<Double> readSoilMoistureLevels() {
        return currentMoistureValues.values()
                                    .stream()
                                    .map(AtomicDouble::get)
                                    .collect(Collectors.toList());
    }

    public void doPumpIteration() {
        try {
            log.debug("Turning pump on");
            pumpTriggerPin.setState(PinState.HIGH);
            postPumpEvent(true);
            Thread.sleep(appProperties.pumpIterationDuration());
        } catch (InterruptedException e) {
            log.warn("Sleep interrupted", e);
        } finally {
            log.debug("Turning pump off");
            pumpTriggerPin.setState(PinState.LOW);
            postPumpEvent(false);
        }
    }

    private void onSensorValue(GpioPinAnalogValueChangeEvent event, ADS1115GpioProvider provider) {
        // RAW value
        double value = event.getValue();

        // percentage
        double percent = ((value * 100) / ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE);

        // approximate voltage ( *scaled based on PGA setting )
        double voltage = provider.getProgrammableGainAmplifier(event.getPin()).getVoltage() * (percent / 100);

        String pinName = event.getPin().getName();


        double moisturePercent = 100 - percent;
        currentMoistureValues.compute(pinName, (k, v) -> {
            if (v == null) {
                return new AtomicDouble(moisturePercent);
            } else {
                v.set(moisturePercent);
                return v;
            }
        });

        postSensorEvent(pinName, moisturePercent);

        log.debug("Moisture Sensor output: (" + pinName + ") : VOLTS=" + decimalFormat.format(voltage) +
                  "  | PERCENT=" + percentDecimalFormat.format(percent) + "% | RAW=" + value +
                  "       , moisturePercent=" + percentDecimalFormat.format(moisturePercent));
    }

    private void postPumpEvent(boolean isPumpTurnedOn) {
        EventDTO eventDTO = new EventDTO();
        eventDTO.setType(EventType.ACTION);
        eventDTO.setOccurredAt(ZonedDateTime.now());
        eventDTO.setData(buildPumpEventData(isPumpTurnedOn));
        postEvent(eventDTO);
    }

    private void postSensorEvent(String pinName, double moisturePercent) {
        EventDTO eventDTO = new EventDTO();
        eventDTO.setType(EventType.READING);
        eventDTO.setOccurredAt(ZonedDateTime.now());
        eventDTO.setData(buildReadingEventData(pinName, moisturePercent));
        postEvent(eventDTO);
    }

    private void postEvent(EventDTO eventDTO) {
        try {
            executorService.submit(() -> baumService.postEvent(eventDTO));
        } catch (IllegalStateException e) {
            log.error("Could not communicate baum service for event posting, working offline", e);
        }
    }

    private Map<String, Object> buildReadingEventData(String pinName, Double value) {
        Map<String, Object> data = new HashMap<>();
        data.put("event_data", SensorsDTO.of(new SensorDTO(pinName, value)));
        return data;
    }

    private Map<String, Object> buildPumpEventData(boolean isPumpTurnedOn) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> actionData = new HashMap<>();
        actionData.put("action_name", "Pump is turned " + (isPumpTurnedOn ? "on" : "off"));
        data.put("event_data", actionData);
        return data;
    }

}
