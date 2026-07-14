package com.hangeoreum.api.vocabulary.domain;

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
@Table(name = "topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    private String code;

    @Setter
    private String title;

    @Setter
    private String icon;

    public static Topic create(String code, String title, String icon) {
        Topic topic = new Topic();
        topic.code = code;
        topic.title = title;
        topic.icon = icon;
        return topic;
    }
}
