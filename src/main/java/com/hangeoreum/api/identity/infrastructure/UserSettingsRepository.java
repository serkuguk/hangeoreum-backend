package com.hangeoreum.api.identity.infrastructure;

import com.hangeoreum.api.identity.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    List<UserSettings> findByRemindersEnabledTrue();
}
