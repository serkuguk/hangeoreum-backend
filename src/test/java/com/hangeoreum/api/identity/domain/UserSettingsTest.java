package com.hangeoreum.api.identity.domain;

import com.hangeoreum.api.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UserSettingsTest {
    @Test
    void bothWritePathsEnforceNumericDailyGoals() {
        UserSettings settings = UserSettings.defaults(UUID.randomUUID());
        assertEquals(20, settings.getDailyGoalXp());
        for (short goal : new short[]{10, 20, 50}) {
            settings.completeOnboarding(goal, false, null);
            assertEquals(goal, settings.getDailyGoalXp());
            settings.update(goal, false, null, true, true, true, 1.0, null);
            assertEquals(goal, settings.getDailyGoalXp());
        }
        for (short goal : new short[]{-1, 0, 15, 51, Short.MAX_VALUE}) {
            assertThrows(DomainException.class, () -> settings.completeOnboarding(goal, true, null));
            assertThrows(DomainException.class, () -> settings.update(goal, true, null, false, false, false, 1.0, null));
            assertEquals(50, settings.getDailyGoalXp());
            assertFalse(settings.isRemindersEnabled());
        }
    }
}
