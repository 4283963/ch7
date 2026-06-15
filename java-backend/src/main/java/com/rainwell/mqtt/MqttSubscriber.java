package com.rainwell.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainwell.service.WaterLevelService;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
public class MqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);

    private final MqttClient mqttClient;
    private final WaterLevelService waterLevelService;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.topic-water-level}")
    private String topicWaterLevel;

    public MqttSubscriber(MqttClient mqttClient, WaterLevelService waterLevelService, ObjectMapper objectMapper) {
        this.mqttClient = mqttClient;
        this.waterLevelService = waterLevelService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribe() throws Exception {
        mqttClient.subscribe(topicWaterLevel, 1);
        mqttClient.setCallback(new org.eclipse.paho.mqttv5.client.MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {}

            @Override
            public void mqttErrorOccurred(MqttException exception) {
                log.error("MQTT 错误: {}", exception.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                try {
                    String payload = new String(message.getPayload());
                    log.debug("收到水位数据: {}", payload);
                    JsonNode root = objectMapper.readTree(payload);
                    JsonNode wellsNode = root.get("wells");
                    Map<Integer, Integer> levels = new HashMap<>();
                    wellsNode.fields().forEachRemaining(entry -> {
                        levels.put(Integer.parseInt(entry.getKey()), entry.getValue().asInt());
                    });
                    waterLevelService.processWaterLevels(levels);
                } catch (Exception e) {
                    log.error("处理水位消息异常: {}", e.getMessage());
                }
            }

            @Override
            public void deliveryComplete(IMqttToken token) {}

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                try {
                    mqttClient.subscribe(topicWaterLevel, 1);
                    log.info("重新订阅水位主题: {}", topicWaterLevel);
                } catch (Exception e) {
                    log.error("重新订阅失败: {}", e.getMessage());
                }
            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {}
        });
        log.info("已订阅水位数据主题: {}", topicWaterLevel);
    }
}
