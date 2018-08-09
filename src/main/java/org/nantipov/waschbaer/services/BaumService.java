package org.nantipov.waschbaer.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.aeonbits.owner.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.nantipov.waschbaer.AppProperties;
import org.nantipov.waschbaer.domain.ActionCommitDTO;
import org.nantipov.waschbaer.domain.ActionDTO;
import org.nantipov.waschbaer.domain.ContainerDTO;
import org.nantipov.waschbaer.domain.EventDTO;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
public class BaumService {


    private static final BaumService INSTANCE = new BaumService();

    private final AppProperties appProperties = ConfigFactory.create(AppProperties.class);
    private final ObjectMapper objectMapper;

    private BaumService() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static BaumService getInstance() {
        return INSTANCE;
    }

    public List<ActionDTO> readActions() {
        try {
            log.debug("Baum: Reading uncommitted actions");
            InputStream inputStream =
                    Unirest.get(appProperties.baseBaumUrl() + appProperties.baumActionsEndpoint())
                           .asBinary()
                           .getBody();

            ContainerDTO<ActionDTO> actions = objectMapper.readValue(
                    inputStream,
                    TypeFactory.defaultInstance().constructParametricType(ContainerDTO.class, ActionDTO.class)
            );

            log.debug("Uncommitted actions received: {}", actions);

            return actions.getItems();
        } catch (UnirestException | IOException e) {
            throw new IllegalStateException("Could not read actions from baum service", e);
        }
    }

    public void commitAction(UUID actionId) {
        try {
            log.debug("Baum: Committing of action {}", actionId);
            int status =
                    Unirest.post(appProperties.baseBaumUrl() + appProperties.baumCommitsEndpoint())
                           .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                           .body(objectMapper.writeValueAsString(new ActionCommitDTO(actionId)))
                           .asBinary()
                           .getStatus();

            if (status != 201) {
                throw new IllegalStateException("Could not commit action " + actionId + ", status code " + status);
            }

            log.debug("Action {} committed", actionId);

        } catch (UnirestException | IOException e) {
            throw new IllegalStateException("Could not read actions from baum service", e);
        }
    }

    public void postEvent(EventDTO eventDTO) {
        try {
            log.debug("Baum: Posting event {}", eventDTO.getType());
            int status =
                    Unirest.post(appProperties.baseBaumUrl() + appProperties.baumEventsEndpoint())
                           .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())
                           .body(objectMapper.writeValueAsString(eventDTO))
                           .asString()
                           .getStatus();

            if (status != 201) {
                throw new IllegalStateException("Could not post event " + eventDTO + ", status code " + status);
            }

            log.debug("Event {} posted: {}", eventDTO.getId(), eventDTO);

        } catch (UnirestException | IOException e) {
            throw new IllegalStateException("Could not post events to baum service", e);
        }
    }


}
