/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softwarefactory.keycloak.providers.events.mqtt;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.paho.mqttv5.client.IMqttClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSecurityException;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.softwarefactory.keycloak.providers.events.models.MQTTMessageOptions;

/**
 * @author <a href="mailto:mhuin@redhat.com">Matthieu Huin</a>
 */
public class MQTTEventListenerProviderFactory implements EventListenerProviderFactory {
    private static final Logger logger = Logger.getLogger(MQTTEventListenerProviderFactory.class.getName());
    private static final String PUBLISHER_ID = "keycloak";

    private IMqttClient client;
    private Set<EventType> excludedEvents;
    private Set<OperationType> excludedAdminOperations;
    private MQTTMessageOptions messageOptions;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new MQTTEventListenerProvider(excludedEvents, excludedAdminOperations, messageOptions, client);
    }

    @Override
    public void init(Config.Scope config) {
        logger.log(Level.SEVERE, config.get("serverUri", "lolkek"));
        String[] excludes = config.getArray("excludeEvents");
        if (excludes != null) {
            excludedEvents = new HashSet<EventType>();
            for (String e : excludes) {
                excludedEvents.add(EventType.valueOf(e));
            }
        }

        String[] excludesOperations = config.getArray("excludesOperations");
        if (excludesOperations != null) {
            excludedAdminOperations = new HashSet<OperationType>();
            for (String e : excludesOperations) {
                excludedAdminOperations.add(OperationType.valueOf(e));
            }
        }

        MqttConnectionOptions options = new MqttConnectionOptions();
        String serverUri = config.get("serverUri", "tcp://mqtt.local:1883");
        
        MemoryPersistence persistence = null;
        if (config.getBoolean("usePersistence", false)) {
            persistence = new MemoryPersistence();
        }
        
        String username = config.get("username", null);
        String password = config.get("password", null);
        if (username != null && password != null) {
            options.setUserName(username);
            options.setPassword(password.getBytes());
        }
        options.setAutomaticReconnect(true);
        //options.setCleanSession(config.getBoolean("cleanSession", true));
        options.setConnectionTimeout(10);

        messageOptions = new MQTTMessageOptions();
        messageOptions.topic = config.get("topic", "keycloak/events");
        messageOptions.retained = config.getBoolean("retained", true);
        messageOptions.qos = config.getInt("qos", 0);
        
        try {
            client = new MqttClient(serverUri, PUBLISHER_ID, persistence);
            client.connect(options);
        } catch (MqttSecurityException e){
            logger.log(Level.WARNING, "Connection not secure!", e);
        } catch (MqttException e){
            logger.log(Level.WARNING, "Connection could not be established!", e);
        }        
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // not needed
    }

    @Override
    public void close() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            logger.log(Level.SEVERE, "Connection could not be closed!", e);
        }
    }

    @Override
    public String getId() {
        return "mqtt";
    }
}