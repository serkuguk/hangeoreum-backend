package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.XpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

    @Query("select coalesce(sum(e.amount), 0) from XpEvent e where e.userId = :userId")
    long totalXp(@Param("userId") UUID userId);
}
