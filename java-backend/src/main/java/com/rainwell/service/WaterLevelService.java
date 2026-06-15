package com.rainwell.service;

import com.rainwell.model.WaterLevelRecord;
import com.rainwell.repository.WaterLevelRepository;
import com.rainwell.websocket.DashboardWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WaterLevelService {

    private static final Logger log = LoggerFactory.getLogger(WaterLevelService.class);

    private final WaterLevelRepository repository;
    private final BalanceEngine balanceEngine;
    private final DashboardWebSocketHandler wsHandler;
    private final WaterLevelFilter waterLevelFilter;

    public WaterLevelService(WaterLevelRepository repository,
                             BalanceEngine balanceEngine,
                             DashboardWebSocketHandler wsHandler,
                             WaterLevelFilter waterLevelFilter) {
        this.repository = repository;
        this.balanceEngine = balanceEngine;
        this.wsHandler = wsHandler;
        this.waterLevelFilter = waterLevelFilter;
    }

    public void processWaterLevels(Map<Integer, Integer> rawLevels) {
        Instant now = Instant.now();

        Map<Integer, Integer> filteredLevels = waterLevelFilter.filterAll(rawLevels);

        for (Map.Entry<Integer, Integer> entry : rawLevels.entrySet()) {
            if (entry.getValue() < 0) continue;

            WaterLevelRecord record = new WaterLevelRecord(entry.getKey(), entry.getValue(), now);
            repository.save(record);
            log.debug("保存水位: 井{} = {}cm", entry.getKey(), entry.getValue());
        }

        wsHandler.broadcastWaterLevels(filteredLevels);
        balanceEngine.evaluate(filteredLevels);
    }

    public Map<Integer, Integer> getLatestLevels() {
        Map<Integer, Integer> latest = new java.util.HashMap<>();
        for (int i = 1; i <= 3; i++) {
            WaterLevelRecord r = repository.findTopByWellIdOrderByRecordedAtDesc(i);
            if (r != null) {
                latest.put(i, r.getLevelCm());
            }
        }
        return latest;
    }

    public List<WaterLevelRecord> getHistory(Integer wellId, Instant since) {
        return repository.findByWellIdAndRecordedAtAfterOrderByRecordedAtDesc(wellId, since);
    }

    public List<WaterLevelRecord> getRecentHistory(Integer wellId, int limit) {
        List<WaterLevelRecord> all = repository.findByWellIdOrderByRecordedAtDesc(wellId);
        return all.stream().limit(limit).collect(Collectors.toList());
    }
}
