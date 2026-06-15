package com.rainwell.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "water_level_records")
public class WaterLevelRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer wellId;

    @Column(nullable = false)
    private Integer levelCm;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    public WaterLevelRecord() {}

    public WaterLevelRecord(Integer wellId, Integer levelCm, Instant recordedAt) {
        this.wellId = wellId;
        this.levelCm = levelCm;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getWellId() { return wellId; }
    public void setWellId(Integer wellId) { this.wellId = wellId; }

    public Integer getLevelCm() { return levelCm; }
    public void setLevelCm(Integer levelCm) { this.levelCm = levelCm; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
