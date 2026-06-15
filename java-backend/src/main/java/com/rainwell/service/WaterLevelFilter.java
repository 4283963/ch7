package com.rainwell.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WaterLevelFilter {

    private static final Logger log = LoggerFactory.getLogger(WaterLevelFilter.class);

    private static final int DEFAULT_WINDOW_SIZE = 7;
    private static final double DEFAULT_EMA_ALPHA = 0.3;
    private static final int DEFAULT_MAX_SPIKE_CM = 150;

    @Value("${filter.window-size:7}")
    private int windowSize;

    @Value("${filter.ema-alpha:0.3}")
    private double emaAlpha;

    @Value("${filter.max-spike-cm:150}")
    private int maxSpikeCm;

    private final Map<Integer, Deque<Integer>> rawWindows = new HashMap<>();
    private final Map<Integer, Integer> lastEmaValues = new HashMap<>();
    private final Map<Integer, Long> lastValidTimestamp = new HashMap<>();

    public synchronized int filter(int wellId, int rawLevel, long timestamp) {
        rawWindows.computeIfAbsent(wellId, k -> new ArrayDeque<>());

        Deque<Integer> window = rawWindows.get(wellId);
        window.addLast(rawLevel);

        if (window.size() > windowSize) {
            window.pollFirst();
        }

        if (window.size() < 3) {
            lastEmaValues.put(wellId, rawLevel);
            lastValidTimestamp.put(wellId, timestamp);
            return rawLevel;
        }

        int median = medianValue(new ArrayList<>(window));

        Integer lastEma = lastEmaValues.get(wellId);
        if (lastEma == null) {
            lastEma = median;
        }

        int diff = Math.abs(rawLevel - lastEma);
        if (diff > maxSpikeCm) {
            log.warn("井 {} 检测到毛刺跳变: raw={}cm, last_ema={}cm, 跳变幅度={}cm, 已过滤",
                    wellId, rawLevel, lastEma, diff);
            return lastEma;
        }

        int newEma = (int) (emaAlpha * median + (1 - emaAlpha) * lastEma);
        lastEmaValues.put(wellId, newEma);
        lastValidTimestamp.put(wellId, timestamp);

        log.debug("井 {} 滤波: raw={}cm, median={}cm, ema={}cm", wellId, rawLevel, median, newEma);
        return newEma;
    }

    public synchronized Map<Integer, Integer> filterAll(Map<Integer, Integer> rawLevels) {
        long now = System.currentTimeMillis();
        Map<Integer, Integer> filtered = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : rawLevels.entrySet()) {
            filtered.put(entry.getKey(), filter(entry.getKey(), entry.getValue(), now));
        }
        return filtered;
    }

    public int getFilteredValue(int wellId) {
        Integer val = lastEmaValues.get(wellId);
        return val != null ? val : 0;
    }

    private int medianValue(List<Integer> values) {
        values.sort(Integer::compare);
        int n = values.size();
        if (n % 2 == 1) {
            return values.get(n / 2);
        } else {
            return (values.get(n / 2 - 1) + values.get(n / 2)) / 2;
        }
    }
}
