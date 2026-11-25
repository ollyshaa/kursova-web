package com.ecoportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_summaries")
public class EventSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(name = "actual_participants", nullable = false)
    private int actualParticipants;

    @Column(name = "waste_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal wasteKg = BigDecimal.ZERO;

    @Column(name = "trees_planted", nullable = false)
    private int treesPlanted;

    @Column(name = "hours_spent", nullable = false, precision = 10, scale = 2)
    private BigDecimal hoursSpent = BigDecimal.ZERO;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventSummary() {
    }

    public EventSummary(Event event) {
        this.event = event;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public int getActualParticipants() {
        return actualParticipants;
    }

    public void setActualParticipants(int actualParticipants) {
        this.actualParticipants = actualParticipants;
    }

    public BigDecimal getWasteKg() {
        return wasteKg;
    }

    public void setWasteKg(BigDecimal wasteKg) {
        this.wasteKg = wasteKg;
    }

    public int getTreesPlanted() {
        return treesPlanted;
    }

    public void setTreesPlanted(int treesPlanted) {
        this.treesPlanted = treesPlanted;
    }

    public BigDecimal getHoursSpent() {
        return hoursSpent;
    }

    public void setHoursSpent(BigDecimal hoursSpent) {
        this.hoursSpent = hoursSpent;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
