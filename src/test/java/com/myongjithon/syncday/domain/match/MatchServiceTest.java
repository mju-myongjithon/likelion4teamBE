package com.myongjithon.syncday.domain.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.AiServiceClient;
import com.myongjithon.syncday.domain.analysis.AnalysisResult;
import com.myongjithon.syncday.domain.analysis.AnalysisResultRepository;
import com.myongjithon.syncday.domain.analysis.MatchDecision;
import com.myongjithon.syncday.domain.analysis.dto.ActivityEntryDto;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.domain.analysis.dto.SceneEntryDto;
import com.myongjithon.syncday.domain.match.dto.MatchResponse;
import com.myongjithon.syncday.domain.match.dto.MatchResultResponse;
import com.myongjithon.syncday.domain.match.similarity.SimilarityCalculator;
import com.myongjithon.syncday.domain.photo.PhotoService;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.domain.user.Campus;
import com.myongjithon.syncday.global.exception.MatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private AnalysisResultRepository analysisResultRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private AiServiceClient aiServiceClient;
    @Mock
    private PhotoService photoService;

    private final SimilarityCalculator similarityCalculator = new SimilarityCalculator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MatchService matchService;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 7);

    @BeforeEach
    void setUp() {
        matchService = new MatchService(
                analysisResultRepository, matchRepository, similarityCalculator, objectMapper, aiServiceClient, photoService);
    }

    @Test
    @DisplayName("반대 캠퍼스 후보 중 유사도가 가장 높은 상대와 매칭한다")
    void picksHighestSimilarityCandidate() {
        UUID targetId = UUID.randomUUID();
        String targetFeatures = featuresJson("카페", "오후", "공부", "차분함", "파란 계열");
        AnalysisResult target = analysis(targetId, Campus.HUMANITIES, "타깃", targetFeatures);

        AnalysisResult low = analysis(UUID.randomUUID(), Campus.NATURAL, "낮은유사도",
                featuresJson("체육시설", "밤", "운동", "활기참", "주황 계열"));

        UUID highId = UUID.randomUUID();
        AnalysisResult high = analysis(highId, Campus.NATURAL, "높은유사도", targetFeatures); // 타깃과 동일 → 100점

        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId)).thenReturn(Optional.empty());
        when(matchRepository.findByDate(TODAY)).thenReturn(List.of());
        when(analysisResultRepository.findByAnalysisDateAndUser_CampusNotAndMatchDecision(TODAY, Campus.HUMANITIES, MatchDecision.ACCEPTED))
                .thenReturn(List.of(low, high));
        when(matchRepository.saveAndFlush(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchResultResponse result = matchService.createMatchForUser(targetId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.MATCHED);
        MatchResponse response = result.match();
        assertThat(response.partnerId()).isEqualTo(highId);
        assertThat(response.partnerNickname()).isEqualTo("높은유사도");
        // 매칭 성사(2b3) 시점엔 유사도가 아직 가려진다(게이트2 CONNECTED 전)
        assertThat(response.similarityScore()).isNull();
        // 계산된 점수는 엔티티에 저장돼 있어야 한다(동일 features → 100점)
        ArgumentCaptor<Match> saved = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSimilarityScore()).isEqualTo(100);
        verify(target).acceptMatching(); // POST = "매칭 수락"(opt-in) 이므로 대상 유저는 수락 처리된다
    }

    @Test
    @DisplayName("scoreBreakdown 에 다섯 차원의 commonTags 가 담긴다")
    void serializesBreakdownWithCommonTags() {
        UUID targetId = UUID.randomUUID();
        AnalysisResult target = analysis(targetId, Campus.HUMANITIES, "타깃",
                featuresJson("카페", "오후", "공부", "차분함", "파란 계열"));
        AnalysisResult partner = analysis(UUID.randomUUID(), Campus.NATURAL, "상대",
                featuresJson("카페", "저녁", "공부", "활기참", "주황 계열"));

        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId)).thenReturn(Optional.empty());
        when(matchRepository.findByDate(TODAY)).thenReturn(List.of());
        when(analysisResultRepository.findByAnalysisDateAndUser_CampusNotAndMatchDecision(TODAY, Campus.HUMANITIES, MatchDecision.ACCEPTED))
                .thenReturn(List.of(partner));
        when(matchRepository.saveAndFlush(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchResultResponse result = matchService.createMatchForUser(targetId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.MATCHED);
        // 매칭 성사(2b3) 시점엔 유사도·근거가 아직 응답에서 가려진다(게이트2 CONNECTED 전)
        assertThat(result.match().similarityScore()).isNull();
        assertThat(result.match().scoreBreakdown()).isNull();
        // 계산·직렬화 결과는 엔티티에 저장돼 있어야 한다
        ArgumentCaptor<Match> saved = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).saveAndFlush(saved.capture());
        // scene(0.30) + activity(0.20) 만 일치 → 50점
        assertThat(saved.getValue().getSimilarityScore()).isEqualTo(50);
        assertThat(saved.getValue().getScoreBreakdown())
                .contains("\"scene\"", "\"timeOfDay\"", "\"activity\"", "\"mood\"", "\"color\"")
                .contains("commonTags")
                .doesNotContain("valueA");
    }

    @Test
    @DisplayName("이미 오늘 매칭된 유저는 재계산 없이 기존 매칭을 반환한다(멱등)")
    void idempotentWhenAlreadyMatched() {
        UUID targetId = UUID.randomUUID();
        AnalysisResult target = analysis(targetId, Campus.HUMANITIES, "타깃",
                featuresJson("카페", "오후", "공부", "차분함", "파란 계열"));

        AppUser targetUser = target.getUser();
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "기존상대");
        Match existing = Match.create(targetUser, partner, TODAY, 72, "{}");

        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId))
                .thenReturn(Optional.of(existing));

        MatchResultResponse result = matchService.createMatchForUser(targetId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.MATCHED);
        MatchResponse response = result.match();
        assertThat(response.partnerNickname()).isEqualTo("기존상대"); // 기존 매칭을 그대로 반환
        assertThat(response.similarityScore()).isNull();            // 아직 CONNECTED 전이라 점수는 가림
        verify(matchRepository, never()).saveAndFlush(any());
        verify(analysisResultRepository, never()).findByAnalysisDateAndUser_CampusNotAndMatchDecision(any(), any(), any());
    }

    @Test
    @DisplayName("오늘 분석이 없으면 ANALYSIS_NOT_FOUND")
    void throwsWhenNoAnalysis() {
        UUID targetId = UUID.randomUUID();
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(targetId, TODAY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.createMatchForUser(targetId, TODAY))
                .isInstanceOf(MatchException.class)
                .hasMessageContaining("분석");
    }

    @Test
    @DisplayName("반대 캠퍼스 후보가 없으면 에러가 아니라 PENDING(매칭 대기)을 반환한다")
    void returnsPendingWhenNoCandidate() {
        UUID targetId = UUID.randomUUID();
        AnalysisResult target = analysis(targetId, Campus.HUMANITIES, "타깃",
                featuresJson("카페", "오후", "공부", "차분함", "파란 계열"));

        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId)).thenReturn(Optional.empty());
        when(matchRepository.findByDate(TODAY)).thenReturn(List.of());
        when(analysisResultRepository.findByAnalysisDateAndUser_CampusNotAndMatchDecision(TODAY, Campus.HUMANITIES, MatchDecision.ACCEPTED))
                .thenReturn(List.of());

        MatchResultResponse result = matchService.createMatchForUser(targetId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.PENDING);
        assertThat(result.match()).isNull();
        verify(matchRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getMatch: 매칭 없고 아직 수락/거부 전이면 NOT_REQUESTED(수락/거부 화면)")
    void getMatchReturnsNotRequestedWhenUndecided() {
        UUID userId = UUID.randomUUID();
        AnalysisResult analysis = analysisWithDecision(MatchDecision.NONE);
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.empty());
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, TODAY))
                .thenReturn(Optional.of(analysis));

        MatchResultResponse result = matchService.getMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.NOT_REQUESTED);
        assertThat(result.match()).isNull();
    }

    @Test
    @DisplayName("getMatch: 수락했지만 아직 상대가 없으면 PENDING(매칭중)")
    void getMatchReturnsPendingWhenAccepted() {
        UUID userId = UUID.randomUUID();
        AnalysisResult analysis = analysisWithDecision(MatchDecision.ACCEPTED);
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.empty());
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, TODAY))
                .thenReturn(Optional.of(analysis));

        MatchResultResponse result = matchService.getMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.PENDING);
        assertThat(result.match()).isNull();
    }

    @Test
    @DisplayName("getMatch: 매칭을 거부했으면 DECLINED")
    void getMatchReturnsDeclinedWhenDeclined() {
        UUID userId = UUID.randomUUID();
        AnalysisResult analysis = analysisWithDecision(MatchDecision.DECLINED);
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.empty());
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, TODAY))
                .thenReturn(Optional.of(analysis));

        MatchResultResponse result = matchService.getMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.DECLINED);
        assertThat(result.match()).isNull();
    }

    @Test
    @DisplayName("getMatch: 분석 기록조차 없으면 NOT_REQUESTED")
    void getMatchReturnsNotRequestedWhenNoAnalysis() {
        UUID userId = UUID.randomUUID();
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.empty());
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, TODAY))
                .thenReturn(Optional.empty());

        MatchResultResponse result = matchService.getMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.NOT_REQUESTED);
    }

    @Test
    @DisplayName("declineMatch: 매칭 전이면 거부 처리하고 DECLINED를 반환한다")
    void declineMarksDeclined() {
        UUID userId = UUID.randomUUID();
        AnalysisResult target = analysis(userId, Campus.HUMANITIES, "타깃",
                featuresJson("카페", "오후", "공부", "차분함", "파란 계열"));
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.empty());

        MatchResultResponse result = matchService.declineMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.DECLINED);
        verify(target).declineMatching();
    }

    @Test
    @DisplayName("declineMatch: 이미 매칭된 뒤면 거부는 늦었으므로 MATCHED를 반환한다")
    void declineAfterMatchedReturnsMatched() {
        UUID userId = UUID.randomUUID();
        AnalysisResult target = analysis(userId, Campus.HUMANITIES, "타깃",
                featuresJson("카페", "오후", "공부", "차분함", "파란 계열"));
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match existing = Match.create(target.getUser(), partner, TODAY, 90, "{}");
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(existing));

        MatchResultResponse result = matchService.declineMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.MATCHED);
        verify(target, never()).declineMatching();
    }

    @Test
    @DisplayName("getMatch: 오늘 매칭이 있으면 MATCHED와 상대 정보를 반환한다")
    void getMatchReturnsMatchedWhenExists() {
        UUID userId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match existing = Match.create(viewer, partner, TODAY, 88, "{}");

        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(existing));

        MatchResultResponse result = matchService.getMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(result.match().partnerNickname()).isEqualTo("상대"); // 상대 신원은 MATCHED부터 공개
        assertThat(result.match().similarityScore()).isNull();         // 유사도는 CONNECTED부터 공개
    }

    @Test
    @DisplayName("features_json 이 깨져 있으면 FEATURES_DESERIALIZATION_FAILED")
    void throwsWhenFeaturesJsonBroken() {
        UUID targetId = UUID.randomUUID();
        AnalysisResult target = analysis(targetId, Campus.HUMANITIES, "타깃", "{not json");
        AnalysisResult candidate = analysis(UUID.randomUUID(), Campus.NATURAL, "상대",
                featuresJson("카페", "오후", "공부", "차분함", "파란 계열"));

        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(targetId, TODAY))
                .thenReturn(Optional.of(target));
        when(matchRepository.findByDateAndParticipant(TODAY, targetId)).thenReturn(Optional.empty());
        when(matchRepository.findByDate(TODAY)).thenReturn(List.of());
        when(analysisResultRepository.findByAnalysisDateAndUser_CampusNotAndMatchDecision(TODAY, Campus.HUMANITIES, MatchDecision.ACCEPTED))
                .thenReturn(List.of(candidate));

        assertThatThrownBy(() -> matchService.createMatchForUser(targetId, TODAY))
                .isInstanceOf(MatchException.class)
                .hasMessageContaining("분석 결과");
    }

    @Test
    @DisplayName("게이트2: 내가 수락했지만 상대가 아직이면 AWAITING_PARTNER, 유사도는 가려진다")
    void chatAcceptWaitsForPartner() {
        UUID userId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match match = Match.create(viewer, partner, TODAY, 88, "{\"totalScore\":88}");
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(match));

        MatchResultResponse result = matchService.applyChatDecision(userId, TODAY, Gate2Decision.ACCEPTED);

        assertThat(result.status()).isEqualTo(MatchStatus.AWAITING_PARTNER);
        assertThat(result.match().similarityScore()).isNull();
        assertThat(result.match().revealedToMe()).isFalse();
        assertThat(match.isConnected()).isFalse();
    }

    @Test
    @DisplayName("게이트2: 양쪽 다 수락하면 CONNECTED, connectedAt 기록되고 유사도·근거가 공개된다")
    void chatBothAcceptConnects() {
        UUID userId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match match = Match.create(viewer, partner, TODAY, 88, "{\"totalScore\":88}");
        match.applyChatDecision(partner.getUserId(), Gate2Decision.ACCEPTED); // 상대는 이미 수락한 상태
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(match));

        MatchResultResponse result = matchService.applyChatDecision(userId, TODAY, Gate2Decision.ACCEPTED);

        assertThat(result.status()).isEqualTo(MatchStatus.CONNECTED);
        assertThat(result.match().similarityScore()).isEqualTo(88);
        assertThat(result.match().revealedToMe()).isTrue();
        assertThat(result.match().scoreBreakdown()).contains("totalScore");
        assertThat(match.isConnected()).isTrue();
        assertThat(match.getConnectedAt()).isNotNull();
    }

    @Test
    @DisplayName("게이트2: 거부하면 ENDED가 되고 점수는 공개되지 않는다")
    void chatRejectEnds() {
        UUID userId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match match = Match.create(viewer, partner, TODAY, 88, "{}");
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(match));

        MatchResultResponse result = matchService.applyChatDecision(userId, TODAY, Gate2Decision.REJECTED);

        assertThat(result.status()).isEqualTo(MatchStatus.ENDED);
        assertThat(result.match().similarityScore()).isNull();
    }

    @Test
    @DisplayName("게이트2: 이미 연결(CONNECTED)된 뒤 거부는 무시된다(터미널)")
    void chatDecisionAfterConnectedIsIgnored() {
        UUID userId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match match = Match.create(viewer, partner, TODAY, 88, "{}");
        match.applyChatDecision(partner.getUserId(), Gate2Decision.ACCEPTED);
        match.applyChatDecision(userId, Gate2Decision.ACCEPTED); // 이미 연결됨
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(match));

        MatchResultResponse result = matchService.applyChatDecision(userId, TODAY, Gate2Decision.REJECTED);

        assertThat(result.status()).isEqualTo(MatchStatus.CONNECTED); // 되돌아가지 않음
        assertThat(match.isConnected()).isTrue();
    }

    @Test
    @DisplayName("게이트2: 성사된 매칭이 없으면 MATCH_NOT_FOUND")
    void chatWithoutMatchThrows() {
        UUID userId = UUID.randomUUID();
        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.applyChatDecision(userId, TODAY, Gate2Decision.ACCEPTED))
                .isInstanceOf(MatchException.class)
                .hasMessageContaining("매칭");
    }

    @Test
    @DisplayName("매칭 발견(MATCHED): 상대의 오늘 사진 URL과 대표 태그를 응답에 담는다")
    void matchedIncludesPartnerPhotosAndTags() {
        UUID userId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(partnerId, Campus.NATURAL, "상대");
        Match match = Match.create(viewer, partner, TODAY, 80, "{}");

        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(match));
        when(photoService.getTodayPhotoUrls(partnerId))
                .thenReturn(List.of("https://s3/p1.jpg", "https://s3/p2.jpg"));
        AnalysisResult partnerAnalysis = analysis(partnerId, Campus.NATURAL, "상대",
                featuresJson("카페", "오후", "산책", "여유로움", "주황 계열"));
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(partnerId, TODAY))
                .thenReturn(Optional.of(partnerAnalysis));

        MatchResultResponse result = matchService.getMatch(userId, TODAY);

        assertThat(result.status()).isEqualTo(MatchStatus.MATCHED);
        assertThat(result.match().partnerPhotoUrls())
                .containsExactly("https://s3/p1.jpg", "https://s3/p2.jpg");
        assertThat(result.match().partnerTags()).contains("카페", "오후", "여유로움");
        assertThat(result.match().similarityScore()).isNull(); // MATCHED라 점수는 여전히 가림
    }

    @Test
    @DisplayName("게이트2: 양쪽 수락으로 연결되면 F4 AI 코멘트를 생성해 저장·공개한다")
    void chatConnectGeneratesAiComment() {
        UUID userId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match match = Match.create(viewer, partner, TODAY, 88, "{}");
        match.applyChatDecision(partner.getUserId(), Gate2Decision.ACCEPTED); // 상대는 이미 수락

        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(match));
        // featuresDtoOf: 두 유저 분석 조회(어느 id로 오든 동일 features 반환)
        AnalysisResult anyAnalysis = analysis(UUID.randomUUID(), Campus.HUMANITIES, "누구",
                featuresJson("카페", "오후", "산책", "여유로움", "초록 계열"));
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(any(), eq(TODAY)))
                .thenReturn(Optional.of(anyAnalysis));
        when(aiServiceClient.generateDescription(any(), any(), eq(88)))
                .thenReturn("두 분 다 여유로운 하루를 보냈어요");

        MatchResultResponse result = matchService.applyChatDecision(userId, TODAY, Gate2Decision.ACCEPTED);

        assertThat(result.status()).isEqualTo(MatchStatus.CONNECTED);
        assertThat(result.match().aiComment()).isEqualTo("두 분 다 여유로운 하루를 보냈어요");
        assertThat(match.getAiComment()).isEqualTo("두 분 다 여유로운 하루를 보냈어요");
    }

    @Test
    @DisplayName("게이트2: AI 코멘트 생성이 실패해도 매칭은 CONNECTED로 성사된다(코멘트만 null)")
    void chatConnectSurvivesAiFailure() {
        UUID userId = UUID.randomUUID();
        AppUser viewer = user(userId, Campus.HUMANITIES, "나");
        AppUser partner = user(UUID.randomUUID(), Campus.NATURAL, "상대");
        Match match = Match.create(viewer, partner, TODAY, 88, "{}");
        match.applyChatDecision(partner.getUserId(), Gate2Decision.ACCEPTED);

        when(matchRepository.findByDateAndParticipant(TODAY, userId)).thenReturn(Optional.of(match));
        AnalysisResult anyAnalysis = analysis(UUID.randomUUID(), Campus.HUMANITIES, "누구",
                featuresJson("카페", "오후", "산책", "여유로움", "초록 계열"));
        when(analysisResultRepository.findByUser_UserIdAndAnalysisDate(any(), eq(TODAY)))
                .thenReturn(Optional.of(anyAnalysis));
        when(aiServiceClient.generateDescription(any(), any(), eq(88)))
                .thenThrow(new RuntimeException("gemini down"));

        MatchResultResponse result = matchService.applyChatDecision(userId, TODAY, Gate2Decision.ACCEPTED);

        assertThat(result.status()).isEqualTo(MatchStatus.CONNECTED);
        assertThat(result.match().aiComment()).isNull();
        assertThat(match.isConnected()).isTrue();
    }

    // ---- helpers ----

    /** ai-service 가 반환하는 features 를 그대로 직렬화한 JSON. 차원마다 값 하나씩만 둔다. */
    private String featuresJson(String scene, String time, String activity, String mood, String color) {
        FeaturesDto dto = new FeaturesDto(
                List.of(new SceneEntryDto(scene, scene + " 디테일")),
                List.of(time),
                List.of(mood),
                List.of(color),
                List.of(new ActivityEntryDto(activity, activity + " 디테일")),
                "하루 요약");
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private AppUser user(UUID userId, Campus campus, String nickname) {
        AppUser user = mock(AppUser.class);
        lenient().when(user.getUserId()).thenReturn(userId);
        lenient().when(user.getCampus()).thenReturn(campus);
        lenient().when(user.getNickname()).thenReturn(nickname);
        return user;
    }

    private AnalysisResult analysis(UUID userId, Campus campus, String nickname, String featuresJson) {
        AppUser user = user(userId, campus, nickname);
        AnalysisResult analysis = mock(AnalysisResult.class);
        lenient().when(analysis.getUser()).thenReturn(user);
        lenient().when(analysis.getFeaturesJson()).thenReturn(featuresJson);
        return analysis;
    }

    /** getMatch 상태 분기 검증용: 매칭 결정만 스텁한 분석 mock. */
    private AnalysisResult analysisWithDecision(MatchDecision decision) {
        AnalysisResult analysis = mock(AnalysisResult.class);
        lenient().when(analysis.getMatchDecision()).thenReturn(decision);
        return analysis;
    }
}
