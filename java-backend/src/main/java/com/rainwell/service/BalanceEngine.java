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

    @Value("${balance.high-threshold:450}")
    private int highThreshold;

    @Value("${balance.low-threshold:150}")
    private int lowThreshold;

    @Value("${balance.diff-threshold:200}")
    private int diffThreshold;

    @Value("${balance.min-action-interval-ms:10000}")
    private long minActionIntervalMs;

    @Value("${balance.stable-count:3}")
    private int stableCountRequired;

    private final Map<String, Boolean> valveStates = new HashMap<>();
    private final Map<Integer, Boolean> pumpStates = new HashMap<>();

    private final Map<String, Long> lastValveActionTime = new HashMap<>();
    private final Map<Integer, Long> lastPumpActionTime = new HashMap<>();

    private final Map<String, Integer> valveOpenStreak = new HashMap<>();
    private final Map<String, Integer> valveCloseStreak = new HashMap<>();
    private final Map<Integer, Integer> pumpOnStreak = new HashMap<>();
    private final Map<Integer, Integer> pumpOffStreak = new HashMap<>();

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
        long now = System.currentTimeMillis();

        checkPair(1, 2, levels, actions, now);
        checkPair(1, 3, levels, actions, now);
        checkPair(2, 3, levels, actions, now);

        if (!actions.isEmpty()) {
            String commandId = UUID.randomUUID().toString();
            mqttPublisher.sendControlCommand(commandId, actions);
            log.info("平衡引擎触发调配指令: commandId={}, actions={}", commandId, actions);
        }

        wsHandler.broadcastValveStatus(valveStates);
        wsHandler.broadcastPumpStatus(pumpStates);
    }

    private void checkPair(int wellA, int wellB, Map<Integer, Integer> levels,
                           List<Map<String, Object>> actions, long now) {
        Integer levelA = levels.get(wellA);
        Integer levelB = levels.get(wellB);
        if (levelA == null || levelB == null) return;

        String valveKey = Math.min(wellA, wellB) + "-" + Math.max(wellA, wellB);
        int diff = levelA - levelB;

        boolean shouldOpenValve = (diff > diffThreshold) || (levelA > highThreshold && levelB < lowThreshold);
        boolean shouldCloseValve = Math.abs(diff) <= diffThreshold / 2;

        int highWell = diff > 0 ? wellA : wellB;
        int lowWell = diff > 0 ? wellB : wellA;

        if (shouldOpenValve) {
            valveOpenStreak.merge(valveKey, 1, Integer::sum);
            valveCloseStreak.put(valveKey, 0);

            boolean currentState = valveStates.getOrDefault(valveKey, false);
            long lastAction = lastValveActionTime.getOrDefault(valveKey, 0L);
            boolean cooldownOk = (now - lastAction) >= minActionIntervalMs;

            if (!currentState && valveOpenStreak.get(valveKey) >= stableCountRequired && cooldownOk) {
                actions.add(Map.of(
                        "type", "valve",
                        "well_a", highWell,
                        "well_b", lowWell,
                        "open", true
                ));
                valveStates.put(valveKey, true);
                lastValveActionTime.put(valveKey, now);
                log.info("趋势稳定后开启 {} 连通阀: streak={}, A={}cm, B={}cm",
                        valveKey, valveOpenStreak.get(valveKey), levelA, levelB);
            }

            handlePump(highWell, true, actions, now);
        } else if (shouldCloseValve) {
            valveCloseStreak.merge(valveKey, 1, Integer::sum);
            valveOpenStreak.put(valveKey, 0);

            boolean currentState = valveStates.getOrDefault(valveKey, false);
            long lastAction = lastValveActionTime.getOrDefault(valveKey, 0L);
            boolean cooldownOk = (now - lastAction) >= minActionIntervalMs;

            if (currentState && valveCloseStreak.get(valveKey) >= stableCountRequired && cooldownOk) {
                actions.add(Map.of(
                        "type", "valve",
                        "well_a", wellA,
                        "well_b", wellB,
                        "open", false
                ));
                valveStates.put(valveKey, false);
                lastValveActionTime.put(valveKey, now);
                log.info("水位稳定配平后关闭 {} 连通阀: streak={}",
                        valveKey, valveCloseStreak.get(valveKey));
            }
        } else {
            valveOpenStreak.put(valveKey, 0);
            valveCloseStreak.put(valveKey, 0);
        }

        if (levelA <= highThreshold && pumpStates.getOrDefault(wellA, false)) {
            handlePump(wellA, false, actions, now);
        }
        if (levelB <= highThreshold && pumpStates.getOrDefault(wellB, false)) {
            handlePump(wellB, false, actions, now);
        }
    }

    private void handlePump(int wellId, boolean shouldRun,
                            List<Map<String, Object>> actions, long now) {
        boolean currentState = pumpStates.getOrDefault(wellId, false);
        long lastAction = lastPumpActionTime.getOrDefault(wellId, 0L);
        boolean cooldownOk = (now - lastAction) >= minActionIntervalMs;

        if (shouldRun != currentState && cooldownOk) {
            int streakKey = shouldRun ? pumpOnStreak.merge(wellId, 1, Integer::sum)
                    : pumpOffStreak.merge(wellId, 1, Integer::sum);

            if (shouldRun) {
                pumpOffStreak.put(wellId, 0);
            } else {
                pumpOnStreak.put(wellId, 0);
            }

            int streak = shouldRun ? pumpOnStreak.getOrDefault(wellId, 0)
                    : pumpOffStreak.getOrDefault(wellId, 0);

            if (streak >= stableCountRequired) {
                actions.add(Map.of(
                        "type", "pump",
                        "well_id", wellId,
                        "running", shouldRun
                ));
                pumpStates.put(wellId, shouldRun);
                lastPumpActionTime.put(wellId, now);
                log.info("井 {} 抽水泵 {}: streak={}", wellId, shouldRun ? "开启" : "关闭", streak);
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
