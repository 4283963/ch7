package com.rainwell.service;

import com.rainwell.mqtt.MqttPublisher;
import com.rainwell.websocket.DashboardWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BalanceEngine {

    private static final Logger log = LoggerFactory.getLogger(BalanceEngine.class);

    private final MqttPublisher mqttPublisher;
    private final DashboardWebSocketHandler wsHandler;

    @Value("${balance.high-threshold}")
    private int highThreshold;

    @Value("${balance.low-threshold}")
    private int lowThreshold;

    @Value("${balance.diff-threshold}")
    private int diffThreshold;

    private final Map<String, Boolean> valveStates = new HashMap<>();
    private final Map<Integer, Boolean> pumpStates = new HashMap<>();

    public BalanceEngine(MqttPublisher mqttPublisher, DashboardWebSocketHandler wsHandler) {
        this.mqttPublisher = mqttPublisher;
        this.wsHandler = wsHandler;

        valveStates.put("1-2", false);
        valveStates.put("1-3", false);
        valveStates.put("2-3", false);
        pumpStates.put(1, false);
        pumpStates.put(2, false);
        pumpStates.put(3, false);
    }

    public void evaluate(Map<Integer, Integer> levels) {
        List<Map<String, Object>> actions = new ArrayList<>();

        checkPair(1, 2, levels, actions);
        checkPair(1, 3, levels, actions);
        checkPair(2, 3, levels, actions);

        if (!actions.isEmpty()) {
            String commandId = UUID.randomUUID().toString();
            mqttPublisher.sendControlCommand(commandId, actions);
            log.info("平衡引擎触发调配指令: commandId={}, actions={}", commandId, actions);
        }

        wsHandler.broadcastValveStatus(valveStates);
        wsHandler.broadcastPumpStatus(pumpStates);
    }

    private void checkPair(int wellA, int wellB, Map<Integer, Integer> levels, List<Map<String, Object>> actions) {
        Integer levelA = levels.get(wellA);
        Integer levelB = levels.get(wellB);
        if (levelA == null || levelB == null) return;

        String valveKey = Math.min(wellA, wellB) + "-" + Math.max(wellA, wellB);
        int diff = levelA - levelB;

        if (diff > diffThreshold || (levelA > highThreshold && levelB < lowThreshold)) {
            int highWell = wellA;
            int lowWell = wellB;
            String vk = Math.min(highWell, lowWell) + "-" + Math.max(highWell, lowWell);

            if (!valveStates.getOrDefault(vk, false)) {
                actions.add(Map.of(
                        "type", "valve",
                        "well_a", highWell,
                        "well_b", lowWell,
                        "open", true
                ));
                valveStates.put(vk, true);
                log.info("井 {} 水位 {}cm 过高，井 {} 水位 {}cm 偏低，开启 {} 连通阀",
                        highWell, levelA, lowWell, levelB, vk);
            }

            if (!pumpStates.getOrDefault(highWell, false)) {
                actions.add(Map.of(
                        "type", "pump",
                        "well_id", highWell,
                        "running", true
                ));
                pumpStates.put(highWell, true);
                log.info("井 {} 抽水泵开启低功耗运转", highWell);
            }
        } else if (Math.abs(diff) <= diffThreshold / 2) {
            String vk = Math.min(wellA, wellB) + "-" + Math.max(wellA, wellB);
            if (valveStates.getOrDefault(vk, false)) {
                actions.add(Map.of(
                        "type", "valve",
                        "well_a", wellA,
                        "well_b", wellB,
                        "open", false
                ));
                valveStates.put(vk, false);
                log.info("井 {}-{} 水位已配平，关闭连通阀", wellA, wellB);
            }

            if (levelA <= highThreshold && pumpStates.getOrDefault(wellA, false)) {
                actions.add(Map.of(
                        "type", "pump",
                        "well_id", wellA,
                        "running", false
                ));
                pumpStates.put(wellA, false);
            }
            if (levelB <= highThreshold && pumpStates.getOrDefault(wellB, false)) {
                actions.add(Map.of(
                        "type", "pump",
                        "well_id", wellB,
                        "running", false
                ));
                pumpStates.put(wellB, false);
            }
        }
    }

    public Map<String, Boolean> getValveStates() {
        return Collections.unmodifiableMap(valveStates);
    }

    public Map<Integer, Boolean> getPumpStates() {
        return Collections.unmodifiableMap(pumpStates);
    }
}
