package com.hangeoreum.api.learning.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "alphabet_letters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlphabetLetter {

    @Id
    @GeneratedValue
    private UUID id;

    private String jamo;

    private String romanization;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private LetterGroup letterGroup;

    @Setter
    private short position;

    @Setter
    private String audioUrl;
}
