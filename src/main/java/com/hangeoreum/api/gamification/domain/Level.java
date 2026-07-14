package com.hangeoreum.api.gamification.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "levels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Level {

    @Id
    private short level;

    private int minXp;

    private String title;
}
