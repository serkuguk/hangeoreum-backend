package com.hangeoreum.api.media.infrastructure;

import com.hangeoreum.api.media.domain.Subtitle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubtitleRepository extends JpaRepository<Subtitle, UUID> {

    List<Subtitle> findByClipIdOrderByLangAscPositionAsc(UUID clipId);

    long countByClipId(UUID clipId);

    void deleteByClipId(UUID clipId);
}
