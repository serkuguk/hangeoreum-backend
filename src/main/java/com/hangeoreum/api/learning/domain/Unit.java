package com.hangeoreum.api.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "units")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Unit {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID courseId;

    @Setter
    private short position;

    @Setter
    private String title;

    @Setter
    private String description;

    @Setter
    private String color;

    @Setter
    private boolean isPublished = false;

    public static Unit create(UUID courseId, short position, String title, String description, String color) {
        Unit unit = new Unit();
        unit.courseId = courseId;
        unit.position = position;
        unit.title = title;
        unit.description = description;
        unit.color = color;
        return unit;
    }
}
