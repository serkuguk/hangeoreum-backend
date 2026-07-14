package com.hangeoreum.api.media.infrastructure;

import com.hangeoreum.api.media.domain.NativeSpeaker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NativeSpeakerRepository extends JpaRepository<NativeSpeaker, UUID> {
}
