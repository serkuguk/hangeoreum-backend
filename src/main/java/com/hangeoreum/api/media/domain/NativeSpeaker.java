package com.hangeoreum.api.media.domain;

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
@Table(name = "native_speakers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NativeSpeaker {

    @Id
    @GeneratedValue
    @Setter(AccessLevel.NONE)
    private UUID id;

    private String name;

    private String avatarUrl;

    private String bio;

    public static NativeSpeaker create(String name, String avatarUrl, String bio) {
        NativeSpeaker speaker = new NativeSpeaker();
        speaker.name = name;
        speaker.avatarUrl = avatarUrl;
        speaker.bio = bio;
        return speaker;
    }
}
