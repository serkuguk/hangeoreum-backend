package com.hangeoreum.api.media.infrastructure;

import com.hangeoreum.api.media.domain.ClipKind;
import com.hangeoreum.api.media.domain.MediaClip;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaClipRepository extends JpaRepository<MediaClip, UUID> {

    List<MediaClip> findByKindOrderByCreatedAtDesc(ClipKind kind);

    @Query("""
            select c from MediaClip c
            where c.kind = :kind and c.isPublished = true
              and (cast(:cursorCreatedAt as timestamp) is null
                   or c.createdAt < :cursorCreatedAt
                   or (c.createdAt = :cursorCreatedAt and c.id < :cursorId))
            order by c.createdAt desc, c.id desc
            """)
    List<MediaClip> feed(@Param("kind") ClipKind kind,
                         @Param("cursorCreatedAt") Instant cursorCreatedAt,
                         @Param("cursorId") UUID cursorId,
                         Pageable pageable);

    @Query("""
            select c from MediaClip c
            where c.kind = :kind and c.isPublished = true and c.unitId = :unitId
              and (:cursorPosition is null or c.position > :cursorPosition
                   or (c.position = :cursorPosition and c.id > :cursorId))
            order by c.position asc, c.id asc
            """)
    List<MediaClip> feedByUnit(@Param("kind") ClipKind kind,
                               @Param("unitId") UUID unitId,
                               @Param("cursorPosition") Integer cursorPosition,
                               @Param("cursorId") UUID cursorId,
                               Pageable pageable);
}
