package com.hangeoreum.api.gamification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "achievements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Achievement {

    @Id
    @GeneratedValue
    private UUID id;

    @Setter
    private String code;

    @Setter
    private String title;

    @Setter
    private String description;

    @Setter
    private String icon;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    private String condition = "{}";
}
