package com.hangeoreum.api.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    private String lang = "ko";

    @Setter
    private String title;

    @Setter
    private String description;

    @Setter
    private boolean isPublished = false;

    private Instant createdAt = Instant.now();

    public static Course create(String title, String description) {
        Course course = new Course();
        course.title = title;
        course.description = description;
        return course;
    }
}
