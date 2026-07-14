package com.hangeoreum.api.gamification.infrastructure;

import com.hangeoreum.api.gamification.domain.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LevelRepository extends JpaRepository<Level, Short> {

    List<Level> findAllByOrderByMinXpAsc();
}
