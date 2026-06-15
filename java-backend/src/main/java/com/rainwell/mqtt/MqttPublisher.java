package com.rainwell.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisher.class);

    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.topic-control-cmd}")
    private String topicControlCmd;

    public MqttPublisher(MqttClient mqttClient, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    public void sendControlCommand(String commandId, java.util.List<Map<String, Object>> actions) {
        try {
            Map<String, Object> payload = Map.of(
                    "command_id", commandId,
                    "actions", actions
            );
            String json = objectMapper.writeValueAsString(payload);
            MqttMessage msg = new MqttMessage(json.getBytes());
            msg.setQos(1);
            mqttClient.publish(topicControlCmd, msg);
            log.info("已发送控制指令: {}", json);
        } catch (Exception e) {
            log.error("发送控制指令异常: {}", e.getMessage());
        }
    }
}
