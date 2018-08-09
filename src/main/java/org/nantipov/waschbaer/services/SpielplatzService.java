package org.nantipov.waschbaer.services;

import lombok.extern.slf4j.Slf4j;
import org.aeonbits.owner.ConfigFactory;
import org.nantipov.waschbaer.AppProperties;
import org.nantipov.waschbaer.domain.ActionDTO;

@Slf4j
public class SpielplatzService {

    private static final SpielplatzService INSTANCE = new SpielplatzService();

    private final BaumService baumService = BaumService.getInstance();
    private final PlantsService plantsService = PlantsService.getInstance();

    private final AppProperties appProperties = ConfigFactory.create(AppProperties.class);

    private SpielplatzService() {

    }

    public static SpielplatzService getInstance() {
        return INSTANCE;
    }

    public void play() {
        // wait for hardware initialization
        try {
            Thread.sleep(appProperties.hardwareInitDelay());
        } catch (InterruptedException e) {
            log.warn("Initialization sleep has been interrupted", e);
        }
        // execute requested actions
        executeActions();
        // execute regular watering cycle if necessary
        doWatering(false);
        plantsService.shutdown();
    }

    private void executeActions() {
        try {
            baumService.readActions()
                       .forEach(this::executeAction);
        } catch (IllegalStateException e) {
            log.error("Could not communicate baum service, working offline", e);
        }
    }

    private void executeAction(ActionDTO action) {
        switch (action.getType()) {
            case POUR:
                doWatering(true);
                break;
            case MEASURE:
                // readings are posted anyway, consider this action type as reserved
                break;
            default:
                throw new IllegalArgumentException("Unknown action type: " + action);
        }
        baumService.commitAction(action.getId());
    }

    private void doWatering(boolean force) {
        int pumpIterations = 0;
        try {
            boolean isDone = false;
            if (force) {
                log.debug("Triggering forced pump iteration");
                plantsService.doPumpIteration();
                Thread.sleep(appProperties.pumpIterationsDelay());
                pumpIterations++;
            }

            while (!isDone && pumpIterations < appProperties.maxWateringPumpIterations()) {
                double averageMoistureLevel = plantsService.readSoilMoistureLevels()
                                                           .stream()
                                                           .mapToDouble(Double::doubleValue)
                                                           .average()
                                                           .orElse(0.0);

                if (averageMoistureLevel < appProperties.wateringAvgMoistureThreshold()) {
                    log.debug("Triggering pump iteration as moisture level is still low: {}%", averageMoistureLevel);
                    plantsService.doPumpIteration();
                    pumpIterations++;
                    Thread.sleep(appProperties.pumpIterationsDelay());
                } else {
                    log.debug("Finishing watering as moisture level is good: {}%", averageMoistureLevel);
                    isDone = true;
                }
            }
        } catch (InterruptedException e) {
            log.error("Method doWatering sleep has been interrupted", e);
        } finally {
            log.debug("Watering has been finished in {} iteration(s)", pumpIterations);
        }
    }

}
