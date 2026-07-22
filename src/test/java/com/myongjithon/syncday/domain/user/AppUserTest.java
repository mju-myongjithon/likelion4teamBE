package com.myongjithon.syncday.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 7, 15);

    @Test
    @DisplayName("최초 참여면 스트릭이 1이 된다")
    void firstParticipationSetsStreakToOne() {
        AppUser user = AppUser.builder().campus(Campus.HUMANITIES).nickname("나").build();

        user.updateStreak(DAY1);

        assertThat(user.getCurrentStreak()).isEqualTo(1);
        assertThat(user.getLastParticipationDate()).isEqualTo(DAY1);
    }

    @Test
    @DisplayName("어제 참여했으면 스트릭이 1 증가한다")
    void consecutiveDayIncrementsStreak() {
        AppUser user = AppUser.builder().campus(Campus.HUMANITIES).nickname("나").build();
        user.updateStreak(DAY1);

        user.updateStreak(DAY1.plusDays(1));

        assertThat(user.getCurrentStreak()).isEqualTo(2);
        assertThat(user.getLastParticipationDate()).isEqualTo(DAY1.plusDays(1));
    }

    @Test
    @DisplayName("오늘 이미 참여했으면 스트릭이 그대로다 (중복 요청 방지)")
    void sameDayDoesNotChangeStreak() {
        AppUser user = AppUser.builder().campus(Campus.HUMANITIES).nickname("나").build();
        user.updateStreak(DAY1);

        user.updateStreak(DAY1);

        assertThat(user.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("하루 이상 건너뛰면 스트릭이 1로 리셋된다")
    void gapResetsStreakToOne() {
        AppUser user = AppUser.builder().campus(Campus.HUMANITIES).nickname("나").build();
        user.updateStreak(DAY1);

        user.updateStreak(DAY1.plusDays(3));

        assertThat(user.getCurrentStreak()).isEqualTo(1);
        assertThat(user.getLastParticipationDate()).isEqualTo(DAY1.plusDays(3));
    }
}
