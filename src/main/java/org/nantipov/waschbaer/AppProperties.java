package org.nantipov.waschbaer;

import org.aeonbits.owner.Config;

@Config.Sources({"file:~/.waschbaer.config"})
public interface AppProperties extends Config {

    @Key("baum.base-url")
    @DefaultValue("https://nantipov.org:9001")
    String baseBaumUrl();

    @Key("baum.actions-endpoint")
    @DefaultValue("/api/actions")
    String baumActionsEndpoint();

    @Key("baum.commits-endpoint")
    @DefaultValue("/api/commits")
    String baumCommitsEndpoint();

    @Key("baum.events-endpoint")
    @DefaultValue("/api/events")
    String baumEventsEndpoint();

    @Key("spielplatz.pump.iteration-duration")
    @DefaultValue("7000")
    long pumpIterationDuration();

    @Key("spielplatz.pump.iterations-delay")
    @DefaultValue("10000")
    long pumpIterationsDelay();

    @Key("spielplatz.watering.moisture.threshold")
    @DefaultValue("10.0")
    double wateringAvgMoistureThreshold();

    @Key("spielplatz.watering.max-iterations")
    @DefaultValue("3")
    int maxWateringPumpIterations();

    @Key("spielplatz.initializing.delay")
    @DefaultValue("10000")
    int hardwareInitDelay();

}
