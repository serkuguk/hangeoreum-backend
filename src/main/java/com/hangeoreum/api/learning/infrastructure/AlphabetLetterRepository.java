package com.hangeoreum.api.learning.infrastructure;

import com.hangeoreum.api.learning.domain.AlphabetLetter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlphabetLetterRepository extends JpaRepository<AlphabetLetter, UUID> {

    List<AlphabetLetter> findAllByOrderByLetterGroupAscPositionAsc();
}
