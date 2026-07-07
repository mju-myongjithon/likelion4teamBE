package com.myongjithon.syncday.domain.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.DailyAnalysis;
import com.myongjithon.syncday.domain.analysis.DailyAnalysisRepository;
import com.myongjithon.syncday.domain.match.dto.MatchResponse;
import com.myongjithon.syncday.domain.match.similarity.AnalysisFeatures;
import com.myongjithon.syncday.domain.match.similarity.SimilarityCalculator;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.global.exception.MatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private DailyAnalysisRepository dailyAnalysisRepository;
    @Mock
    private MatchRepository matchRepository;

    private final SimilarityCalculator similarityCalculator = new SimilarityCalculator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MatchService matchService;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

    @BeforeEach
    void setUp() {
        matchService = new MatchService(
                dailyAnalysisRepository, matchRepository, similarityCalculator, objectMapper);
    }

    @Test
    @DisplayName("반대 캠퍼스 후보 중 유사도가 가장 높은 상대와 매칭한다")
    void picksHighestSimilarityCandidate() {
        UUID targetId = UUID.randomUUID();
        AnalysisFeatures targetFeatures = features("카페", "오후", "차분함", "블루", "공부");
        DailyAnalysis target = analysis(targetId, "인문", "타깃", targetFeatures);

        UUID lowId = UUID.randomUUID();
        DailyAnalysis low = analysis(lowId, "자연", "낮은유사도",
                features("체육관", "밤", "활기참", "레드", "운동"));

        UUID highId = UUID.randomUUID();
        DailyAnalysis high = analysis(highId, "자연", "높은유사도", targetFeatures); // 타깃과 동일 → 100점

        when(dailyAnalysisRepository.findByUser_UserIdAndDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId)).thenReturn(Optional.empty());
        when(matchRepository.findByDate(TODAY)).thenReturn(List.of());
        when(dailyAnalysisRepository.findByDateAndUser_CampusNot(TODAY, "인문"))
                .thenReturn(List.of(low, high));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchResponse response = matchService.createMatchForUser(targetId, TODAY);

        assertThat(response.similarityScore()).isEqualTo(100);
        assertThat(response.partnerId()).isEqualTo(highId);
        assertThat(response.partnerNickname()).isEqualTo("높은유사도");
    }

    @Test
    @DisplayName("이미 오늘 매칭된 유저는 재계산 없이 기존 매칭을 반환한다(멱등)")
    void idempotentWhenAlreadyMatched() {
        UUID targetId = UUID.randomUUID();
        DailyAnalysis target = analysis(targetId, "인문", "타깃",
                features("카페", "오후", "차분함", "블루", "공부"));

        AppUser targetUser = target.getUser();
        AppUser partner = user(UUID.randomUUID(), "자연", "기존상대");
        Match existing = Match.create(targetUser, partner, TODAY, 72, "{}");

        when(dailyAnalysisRepository.findByUser_UserIdAndDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId))
                .thenReturn(Optional.of(existing));

        MatchResponse response = matchService.createMatchForUser(targetId, TODAY);

        assertThat(response.similarityScore()).isEqualTo(72);
        assertThat(response.partnerNickname()).isEqualTo("기존상대");
        verify(matchRepository, never()).save(any());
        verify(dailyAnalysisRepository, never()).findByDateAndUser_CampusNot(any(), any());
    }

    @Test
    @DisplayName("오늘 분석이 없으면 ANALYSIS_NOT_FOUND")
    void throwsWhenNoAnalysis() {
        UUID targetId = UUID.randomUUID();
        when(dailyAnalysisRepository.findByUser_UserIdAndDate(targetId, TODAY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.createMatchForUser(targetId, TODAY))
                .isInstanceOf(MatchException.class)
                .hasMessageContaining("분석");
    }

    @Test
    @DisplayName("반대 캠퍼스 후보가 없으면 NO_MATCH_CANDIDATE")
    void throwsWhenNoCandidate() {
        UUID targetId = UUID.randomUUID();
        DailyAnalysis target = analysis(targetId, "인문", "타깃",
                features("카페", "오후", "차분함", "블루", "공부"));

        when(dailyAnalysisRepository.findByUser_UserIdAndDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId)).thenReturn(Optional.empty());
        when(matchRepository.findByDate(TODAY)).thenReturn(List.of());
        when(dailyAnalysisRepository.findByDateAndUser_CampusNot(TODAY, "인문")).thenReturn(List.of());

        assertThatThrownBy(() -> matchService.createMatchForUser(targetId, TODAY))
                .isInstanceOf(MatchException.class)
                .hasMessageContaining("상대");
    }

    // ---- helpers ----

    private AnalysisFeatures features(String scene, String time, String mood, String color, String activity) {
        return new AnalysisFeatures(List.of(scene), time, mood, color, List.of(activity));
    }

    private AppUser user(UUID userId, String campus, String nickname) {
        AppUser user = mock(AppUser.class);
        lenient().when(user.getUserId()).thenReturn(userId);
        lenient().when(user.getCampus()).thenReturn(campus);
        lenient().when(user.getNickname()).thenReturn(nickname);
        return user;
    }

    private DailyAnalysis analysis(UUID userId, String campus, String nickname, AnalysisFeatures features) {
        AppUser user = user(userId, campus, nickname);
        DailyAnalysis analysis = mock(DailyAnalysis.class);
        lenient().when(analysis.getUser()).thenReturn(user);
        lenient().when(analysis.toFeatures()).thenReturn(features);
        return analysis;
    }
}
