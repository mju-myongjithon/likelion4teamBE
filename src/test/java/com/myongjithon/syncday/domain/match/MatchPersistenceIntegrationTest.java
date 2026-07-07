package com.myongjithon.syncday.domain.match;

import com.myongjithon.syncday.domain.analysis.DailyAnalysis;
import com.myongjithon.syncday.domain.analysis.DailyAnalysisRepository;
import com.myongjithon.syncday.domain.user.AppUser;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL(Zonky embedded, Docker 불필요) 위에서 JPA 매핑과 제약을 검증한다.
 * text[] / jsonb 컬럼, (user_a,user_b,date) 유니크 제약, 파생 쿼리가 대상.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(
        provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY,
        type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES
)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class MatchPersistenceIntegrationTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private DailyAnalysisRepository dailyAnalysisRepository;
    @Autowired
    private MatchRepository matchRepository;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

    @Test
    @DisplayName("daily_analysis 의 text[] 와 jsonb 컬럼이 왕복 저장된다")
    void textArrayAndJsonbRoundTrip() {
        AppUser user = persistUser("인문", "타깃");
        DailyAnalysis analysis = DailyAnalysis.builder()
                .user(user)
                .date(TODAY)
                .sceneTags(List.of("카페", "야외"))
                .timeOfDay("오후")
                .mood("차분함")
                .dominantColor("블루")
                .activityTags(List.of("공부", "산책"))
                .rawAiResponse("{\"model\":\"gpt-4o\",\"confidence\":0.91}")
                .build();
        em.persist(analysis);
        em.flush();
        em.clear();

        DailyAnalysis found = dailyAnalysisRepository
                .findByUser_UserIdAndDate(user.getUserId(), TODAY)
                .orElseThrow();

        assertThat(found.getSceneTags()).containsExactly("카페", "야외");
        assertThat(found.getActivityTags()).containsExactly("공부", "산책");
        assertThat(found.getRawAiResponse()).contains("gpt-4o");
    }

    @Test
    @DisplayName("findByDateAndUser_CampusNot 은 반대 캠퍼스 분석만 반환한다")
    void findsOppositeCampusOnly() {
        AppUser humanities = persistUser("인문", "인문A");
        AppUser science = persistUser("자연", "자연B");
        AppUser humanities2 = persistUser("인문", "인문C");
        persistAnalysis(humanities);
        persistAnalysis(science);
        persistAnalysis(humanities2);
        em.flush();
        em.clear();

        List<DailyAnalysis> candidates =
                dailyAnalysisRepository.findByDateAndUser_CampusNot(TODAY, "인문");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getUser().getCampus()).isEqualTo("자연");
    }

    @Test
    @DisplayName("같은 (쌍, 날짜) 매칭은 순서를 뒤집어도 유니크 제약에 걸린다")
    void duplicatePairViolatesUniqueConstraint() {
        AppUser a = persistUser("인문", "A");
        AppUser b = persistUser("자연", "B");
        em.flush();

        matchRepository.saveAndFlush(Match.create(a, b, TODAY, 80, "{\"totalScore\":80}"));

        // (b, a) 로 만들어도 UUID 정규화로 같은 (user_a,user_b) 가 되어 충돌해야 한다.
        Match reversed = Match.create(b, a, TODAY, 55, "{\"totalScore\":55}");
        assertThatThrownBy(() -> matchRepository.saveAndFlush(reversed))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByDateAndParticipant 는 A/B 어느 쪽 유저로도 매칭을 찾는다")
    void findsMatchByEitherParticipant() {
        AppUser a = persistUser("인문", "A");
        AppUser b = persistUser("자연", "B");
        em.flush();
        Match saved = matchRepository.saveAndFlush(Match.create(a, b, TODAY, 80, "{\"totalScore\":80}"));
        em.clear();

        Optional<Match> byA = matchRepository.findByDateAndParticipant(TODAY, a.getUserId());
        Optional<Match> byB = matchRepository.findByDateAndParticipant(TODAY, b.getUserId());

        assertThat(byA).isPresent().get().extracting(Match::getMatchId).isEqualTo(saved.getMatchId());
        assertThat(byB).isPresent().get().extracting(Match::getMatchId).isEqualTo(saved.getMatchId());
        assertThat(byA.get().getScoreBreakdown()).contains("totalScore");
    }

    // ---- helpers ----

    private AppUser persistUser(String campus, String nickname) {
        AppUser user = AppUser.builder().campus(campus).nickname(nickname).build();
        return em.persist(user);
    }

    private void persistAnalysis(AppUser user) {
        em.persist(DailyAnalysis.builder()
                .user(user)
                .date(TODAY)
                .sceneTags(List.of("카페"))
                .timeOfDay("오후")
                .mood("차분함")
                .dominantColor("블루")
                .activityTags(List.of("공부"))
                .rawAiResponse("{}")
                .build());
    }
}
