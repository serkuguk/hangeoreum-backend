package com.hangeoreum.api.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "learning_tips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningTip {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID lessonId;

    private String title;

    private String bodyMd;

    @JdbcTypeCode(SqlTypes.JSON)
    private String examples = "[]";

    public static LearningTip create(UUID lessonId, String title, String bodyMd, String examples) {
        LearningTip tip = new LearningTip();
        tip.lessonId = lessonId;
        tip.update(title, bodyMd, examples);
        return tip;
    }

    public void update(String title, String bodyMd, String examples) {
        this.title = title;
        this.bodyMd = bodyMd;
        this.examples = examples == null ? "[]" : examples;
    }
}
