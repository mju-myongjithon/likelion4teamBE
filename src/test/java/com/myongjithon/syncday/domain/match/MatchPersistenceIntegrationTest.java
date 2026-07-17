package com.myongjithon.syncday.domain.match;

import com.myongjithon.syncday.domain.analysis.AnalysisResult;
import com.myongjithon.syncday.domain.analysis.AnalysisResultRepository;
import com.myongjithon.syncday.domain.analysis.MatchDecision;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.domain.user.Campus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL(Zonky embedded, Docker 불필요) 위에서 JPA 매핑과 제약을 검증한다.
 * analysis_result 의 features_json, match 의 jsonb 컬럼과 (user_a,user_b,date) 유니크 제약, 파생 쿼리가 대상.
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
    private AnalysisResultRepository analysisResultRepository;
    @Autowired
    private MatchRepository matchRepository;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

    private static final String FEATURES_JSON = """
            {"scene":[{"category":"카페","detail":"홍대 감성 카페"}],
             "timeOfDay":["오후"],"mood":["차분함"],"color":["파란 계열"],
             "activity":[{"category":"공부","detail":"과제"}],
             "summary":"카페에서 과제를 하며 보낸 하루"}
            """;

    @Test
    @DisplayName("analysis_result 의 features_json 이 한글 포함 그대로 왕복 저장된다")
    void featuresJsonRoundTrip() {
        AppUser user = persistUser(Campus.HUMANITIES, "타깃");
        em.persist(AnalysisResult.builder()
                .user(user)
                .analysisDate(TODAY)
                .featuresJson(FEATURES_JSON)
                .build());
        em.flush();
        em.clear();

        AnalysisResult found = analysisResultRepository
                .findByUser_UserIdAndAnalysisDate(user.getUserId(), TODAY)
                .orElseThrow();

        assertThat(found.getFeaturesJson()).contains("홍대 감성 카페", "파란 계열");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("후보 쿼리는 반대 캠퍼스 & 매칭 수락(ACCEPTED)한 유저만 반환한다")
    void findsOppositeCampusAcceptedOnly() {
        AppUser humanities = persistUser(Campus.HUMANITIES, "인문A");        // 같은 캠퍼스 → 제외
        AppUser science = persistUser(Campus.NATURAL, "자연B");            // 반대 & 수락 → 포함
        AppUser scienceUndecided = persistUser(Campus.NATURAL, "자연미정"); // 반대지만 미수락(NONE) → 제외
        AppUser scienceDeclined = persistUser(Campus.NATURAL, "자연거부");  // 반대지만 거부(DECLINED) → 제외
        persistAnalysis(humanities, MatchDecision.ACCEPTED);
        persistAnalysis(science, MatchDecision.ACCEPTED);
        persistAnalysis(scienceUndecided, MatchDecision.NONE);
        persistAnalysis(scienceDeclined, MatchDecision.DECLINED);
        em.flush();
        em.clear();

        List<AnalysisResult> candidates = analysisResultRepository
                .findByAnalysisDateAndUser_CampusNotAndMatchDecision(TODAY, Campus.HUMANITIES, MatchDecision.ACCEPTED);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getUser().getNickname()).isEqualTo("자연B");
    }

    @Test
    @DisplayName("같은 (쌍, 날짜) 매칭은 순서를 뒤집어도 유니크 제약에 걸린다")
    void duplicatePairViolatesUniqueConstraint() {
        AppUser a = persistUser(Campus.HUMANITIES, "A");
        AppUser b = persistUser(Campus.NATURAL, "B");
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
        AppUser a = persistUser(Campus.HUMANITIES, "A");
        AppUser b = persistUser(Campus.NATURAL, "B");
        em.flush();
        Match saved = matchRepository.saveAndFlush(Match.create(a, b, TODAY, 80, "{\"totalScore\":80}"));
        em.clear();

        List<Match> byA = matchRepository.findByDateAndParticipant(TODAY, a.getUserId());
        List<Match> byB = matchRepository.findByDateAndParticipant(TODAY, b.getUserId());

        assertThat(byA).singleElement().extracting(Match::getMatchId).isEqualTo(saved.getMatchId());
        assertThat(byB).singleElement().extracting(Match::getMatchId).isEqualTo(saved.getMatchId());
        assertThat(byA.get(0).getScoreBreakdown()).contains("totalScore");
    }

    // ---- helpers ----

    private AppUser persistUser(Campus campus, String nickname) {
        AppUser user = AppUser.builder().campus(campus).nickname(nickname).build();
        return em.persist(user);
    }

    private void persistAnalysis(AppUser user, MatchDecision decision) {
        AnalysisResult analysis = AnalysisResult.builder()
                .user(user)
                .analysisDate(TODAY)
                .featuresJson(FEATURES_JSON)
                .build();
        if (decision == MatchDecision.ACCEPTED) {
            analysis.acceptMatching();
        } else if (decision == MatchDecision.DECLINED) {
            analysis.declineMatching();
        }
        em.persist(analysis);
    }
}
