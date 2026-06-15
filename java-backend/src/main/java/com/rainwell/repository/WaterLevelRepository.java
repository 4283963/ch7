package com.rainwell.repository;

import com.rainwell.model.WaterLevelRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WaterLevelRepository extends JpaRepository<WaterLevelRecord, Long> {

    List<WaterLevelRecord> findByWellIdOrderByRecordedAtDesc(Integer wellId);

    List<WaterLevelRecord> findByWellIdAndRecordedAtAfterOrderByRecordedAtDesc(Integer wellId, Instant since);

    WaterLevelRecord findTopByWellIdOrderByRecordedAtDesc(Integer wellId);
}
