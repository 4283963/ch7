package com.rainwell.controller;

import com.rainwell.model.WaterLevelRecord;
import com.rainwell.service.BalanceEngine;
import com.rainwell.service.WaterLevelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final WaterLevelService waterLevelService;
    private final BalanceEngine balanceEngine;

    public DashboardController(WaterLevelService waterLevelService, BalanceEngine balanceEngine) {
        this.waterLevelService = waterLevelService;
        this.balanceEngine = balanceEngine;
    }

    @GetMapping("/water-levels/latest")
    public ResponseEntity<Map<Integer, Integer>> getLatestLevels() {
        return ResponseEntity.ok(waterLevelService.getLatestLevels());
    }

    @GetMapping("/water-levels/history/{wellId}")
    public ResponseEntity<List<WaterLevelRecord>> getHistory(
            @PathVariable Integer wellId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(waterLevelService.getRecentHistory(wellId, limit));
    }

    @GetMapping("/status/valves")
    public ResponseEntity<Map<String, Boolean>> getValveStates() {
        return ResponseEntity.ok(balanceEngine.getValveStates());
    }

    @GetMapping("/status/pumps")
    public ResponseEntity<Map<Integer, Boolean>> getPumpStates() {
        return ResponseEntity.ok(balanceEngine.getPumpStates());
    }
}
