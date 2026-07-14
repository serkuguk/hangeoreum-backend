package com.hangeoreum.api.vocabulary.infrastructure;

import com.hangeoreum.api.vocabulary.domain.Word;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WordRepository extends JpaRepository<Word, UUID> {

    /** pattern is always bound (use "%" for no search) — null text params break Postgres type inference */
    @Query("""
            select w from Word w
            where (lower(w.hangul) like :pattern or lower(w.translation) like :pattern)
              and (:topicId is null or w.topicId = :topicId)
            order by w.createdAt desc
            """)
    Page<Word> search(@Param("pattern") String pattern, @Param("topicId") UUID topicId, Pageable pageable);
}
