package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.UserLetterProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserLetterProgressRepository extends JpaRepository<UserLetterProgress, UserLetterProgress.Pk> {

    List<UserLetterProgress> findByUserId(UUID userId);

    long countByUserId(UUID userId);
}
