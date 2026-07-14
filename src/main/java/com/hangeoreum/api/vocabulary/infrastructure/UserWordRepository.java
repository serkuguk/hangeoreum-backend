package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.UserWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserWordRepository extends JpaRepository<UserWord, UUID> {

    Optional<UserWord> findByUserIdAndWordId(UUID userId, UUID wordId);

    Optional<UserWord> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "word")
    List<UserWord> findByUserIdAndDueDateLessThanEqualOrderByDueDateAsc(UUID userId, LocalDate date, Pageable pageable);

    @EntityGraph(attributePaths = "word")
    List<UserWord> findByUserIdAndIsDifficultTrueOrderByEaseFactorAsc(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "word")
    List<UserWord> findByUserIdAndWordIdIn(UUID userId, Collection<UUID> wordIds);

    long countByUserId(UUID userId);

    long countByUserIdAndDueDateLessThanEqual(UUID userId, LocalDate date);

    long countByUserIdAndIsDifficultTrue(UUID userId);

    long countByUserIdAndLevelGreaterThanEqual(UUID userId, short level);

    boolean existsByWordId(UUID wordId);

    /** pattern is always bound (use "%" for no search) — null text params break Postgres type inference */
    @EntityGraph(attributePaths = "word")
    @Query("""
            select uw from UserWord uw
            where uw.userId = :userId
              and (lower(uw.word.hangul) like :pattern or lower(uw.word.translation) like :pattern)
              and (:level is null or uw.level = :level)
              and (:topicId is null or uw.word.topicId = :topicId)
            """)
    Page<UserWord> search(@Param("userId") UUID userId, @Param("pattern") String pattern,
                          @Param("level") Short level, @Param("topicId") UUID topicId, Pageable pageable);
}
