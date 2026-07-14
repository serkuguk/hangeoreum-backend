package com.hangeoreum.api.media.infrastructure;

import com.hangeoreum.api.media.domain.UserClipView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserClipViewRepository extends JpaRepository<UserClipView, UserClipView.Pk> {

    List<UserClipView> findByUserIdAndClipIdIn(UUID userId, List<UUID> clipIds);
}
