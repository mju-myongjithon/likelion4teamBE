package com.myongjithon.syncday.domain.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.AiServiceClient;
import com.myongjithon.syncday.domain.analysis.AnalysisResult;
import com.myongjithon.syncday.domain.analysis.AnalysisResultRepository;
import com.myongjithon.syncday.domain.analysis.MatchDecision;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.domain.match.dto.MatchResultResponse;
import com.myongjithon.syncday.domain.match.similarity.AnalysisFeatures;
import com.myongjithon.syncday.domain.match.similarity.SimilarityCalculator;
import com.myongjithon.syncday.domain.match.similarity.SimilarityResult;
import com.myongjithon.syncday.domain.photo.PhotoService;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.global.exception.MatchErrorCode;
import com.myongjithon.syncday.global.exception.MatchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final AnalysisResultRepository analysisResultRepository;
    private final MatchRepository matchRepository;
    private final SimilarityCalculator similarityCalculator;
    private final ObjectMapper objectMapper;
    private final AiServiceClient aiServiceClient;
    private final PhotoService photoService;

    /**
     * 대상 유저가 매칭을 <b>수락</b>하고(게이트1 opt-in) 매칭을 시도한다. 이미 <b>활성</b>(진행 중이거나
     * 연결된) 매칭이 있으면 기존 결과를 그대로 반환한다(멱등). 채팅방이 열리기 전에 종료(ENDED)된
     * 매칭은 더 이상 이 유저를 막지 않는다 — 새 상대를 찾아 다시 매칭을 시도한다.
     * 후보군은 같은 날짜 · 반대 캠퍼스 · <b>매칭을 수락한</b> · 아직 활성 매칭되지 않은 · 오늘 이 유저와
     * 이미 매칭행(종료 포함)을 가진 적 없는 유저이며, 그 중 유사도가 가장 높은 상대를 고른다.
     *
     * <p>하루 매칭 특성상, 먼저 수락한 유저는 반대 캠퍼스 상대가 아직 아무도 수락하지 않았을 수 있다.
     * 이는 에러가 아니라 대기 상태이므로 {@link MatchResultResponse#pending()} 를 반환한다.
     * FE는 이 응답을 받는 동안 "매칭중..." 화면을 유지하며 이 엔드포인트를 폴링한다.
     */
    @Transactional
    public MatchResultResponse createMatchForUser(UUID userId, LocalDate date) {
        AnalysisResult target = analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, date)
                .orElseThrow(() -> new MatchException(MatchErrorCode.ANALYSIS_NOT_FOUND));

        // 매칭 수락(opt-in): 이 호출 자체가 "수락" 액션이다. 관리 엔티티라 커밋 시 자동 반영된다.
        target.acceptMatching();

        List<Match> todaysMatches = matchRepository.findByDateAndParticipant(date, userId);

        // 멱등성: 오늘 이미 "활성"(연결됐거나 아직 게이트2 결정 전인) 매칭이 있으면 재계산 없이 그대로 반환.
        Optional<Match> active = todaysMatches.stream().filter(m -> !m.isEnded()).findFirst();
        if (active.isPresent()) {
            return toResult(active.get(), userId);
        }

        AppUser targetUser = target.getUser();

        // 활성 매칭이 성사된 유저만 다른 사람들의 후보에서 제외한다 (1:1 배타).
        // 종료(ENDED)된 매칭의 당사자는 그 상대가 아닌 다른 사람들에게는 다시 후보가 될 수 있다.
        Set<UUID> alreadyMatched = matchRepository.findByDate(date).stream()
                .filter(m -> !m.isEnded())
                .flatMap(m -> Stream.of(m.getUserA().getUserId(), m.getUserB().getUserId()))
                .collect(Collectors.toSet());

        // 오늘 나와 이미 매칭행(종료 포함)이 있었던 상대는 나만 다시 후보로 안 잡는다 — 그 상대와는
        // (user_a, user_b, date) unique 제약 때문에 같은 쌍으로 하루에 매칭행을 두 번 만들 수 없다.
        Set<UUID> myPastPartners = todaysMatches.stream()
                .map(m -> m.isUserA(userId) ? m.getUserB().getUserId() : m.getUserA().getUserId())
                .collect(Collectors.toSet());

        // 후보는 "매칭을 수락(ACCEPTED)한" 반대 캠퍼스 유저만. 미수락(NONE)·거부(DECLINED)는 상대로 잡히지 않는다.
        List<AnalysisResult> candidates = analysisResultRepository
                .findByAnalysisDateAndUser_CampusNotAndMatchDecision(date, targetUser.getCampus(), MatchDecision.ACCEPTED)
                .stream()
                .filter(candidate -> !alreadyMatched.contains(candidate.getUser().getUserId()))
                .filter(candidate -> !myPastPartners.contains(candidate.getUser().getUserId()))
                .toList();

        // 상대가 아직 없음 → 에러가 아니라 대기(PENDING). 상대가 분석을 끝내는 순간 다음 폴링에서 성사된다.
        if (candidates.isEmpty()) {
            return MatchResultResponse.pending();
        }

        AnalysisFeatures targetFeatures = toFeatures(target);

        AnalysisResult best = null;
        SimilarityResult bestResult = null;
        for (AnalysisResult candidate : candidates) {
            SimilarityResult result = similarityCalculator.calculate(targetFeatures, toFeatures(candidate));
            if (bestResult == null || result.totalScore() > bestResult.totalScore()) {
                best = candidate;
                bestResult = result;
            }
        }

        Match match = Match.create(
                targetUser,
                best.getUser(),
                date,
                bestResult.totalScore(),
                serialize(bestResult)
        );
        // saveAndFlush: 반대 캠퍼스 상대가 같은 순간에 같은 쌍을 저장하면 유니크 제약에 걸린다.
        // 이 위반을 커밋 지연이 아니라 이 지점에서 DataIntegrityViolationException 으로 즉시 드러내
        // 컨트롤러가 "이미 성사된 매칭 조회"로 복구(멱등)할 수 있게 한다.
        Match saved = matchRepository.saveAndFlush(match);

        return toResult(saved, userId);
    }

    /**
     * 대상 유저가 매칭을 <b>거부</b>한다(게이트1). 아직 매칭 전일 때만 유효하며, 후보 풀에서 빠진다.
     * 이미 활성 매칭이 성사된 뒤라면 거부하기엔 늦었으므로 기존 매칭(MATCHED)을 그대로 돌려준다.
     * (종료된 매칭만 있는 경우는 활성 매칭이 아니므로 정상적으로 거부 처리된다.)
     */
    @Transactional
    public MatchResultResponse declineMatch(UUID userId, LocalDate date) {
        AnalysisResult target = analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, date)
                .orElseThrow(() -> new MatchException(MatchErrorCode.ANALYSIS_NOT_FOUND));

        Optional<Match> active = findActiveMatch(userId, date);
        if (active.isPresent()) {
            return toResult(active.get(), userId);
        }

        target.declineMatching();
        return MatchResultResponse.declined();
    }

    /**
     * 게이트2: 뷰어가 채팅 참여를 수락/거부한다. 활성 매칭이 성사된 뒤에만 유효하다.
     * 양쪽이 모두 수락하면 매칭이 연결(CONNECTED)되고 connectedAt 이 기록되어 F5가 채팅방을 연다.
     * 관리 엔티티라 커밋 시 변경이 자동 반영된다(멱등: 이미 연결/종료면 변화 없음).
     */
    @Transactional
    public MatchResultResponse applyChatDecision(UUID userId, LocalDate date, Gate2Decision decision) {
        Match match = findActiveMatch(userId, date)
                .orElseThrow(() -> new MatchException(MatchErrorCode.MATCH_NOT_FOUND));
        match.applyChatDecision(userId, decision);
        maybeGenerateAiComment(match, date);
        maybeGenerateIcebreaker(match, date);
        return toResult(match, userId);
    }

    /**
     * 유저의 해당 날짜 매칭 상태 조회(읽기 전용, 매칭을 새로 시도하지 않음).
     * 활성(연결됐거나 게이트2 결정 전인) 매칭이 있으면 그걸 보여주고, 없으면 오늘 종료된 매칭 중
     * 아무거나(정보 표시용, "다시 시도" 유도) 보여준다. 매칭행이 하나도 없으면 유저의 수락/거부
     * 상태에 따라 NOT_REQUESTED / PENDING / DECLINED 로 내려준다.
     * FE가 화면 재진입 시 어느 단계인지 판단하고, "매칭중..." 화면에서 MATCHED로 바뀌는지 폴링하는 데 쓴다.
     */
    @Transactional(readOnly = true)
    public MatchResultResponse getMatch(UUID userId, LocalDate date) {
        List<Match> todaysMatches = matchRepository.findByDateAndParticipant(date, userId);
        Optional<Match> current = todaysMatches.stream()
                .filter(m -> !m.isEnded())
                .findFirst()
                .or(() -> todaysMatches.stream().findFirst());
        if (current.isPresent()) {
            return toResult(current.get(), userId);
        }

        // 매칭 전이면 유저의 수락/거부 상태로 화면을 구분해준다.
        return analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, date)
                .map(analysis -> switch (analysis.getMatchDecision() == null
                        ? MatchDecision.NONE : analysis.getMatchDecision()) {
                    case ACCEPTED -> MatchResultResponse.pending();
                    case DECLINED -> MatchResultResponse.declined();
                    case NONE -> MatchResultResponse.notRequested();
                })
                .orElseGet(MatchResultResponse::notRequested);
    }

    /** 오늘 이 유저의 매칭행 중 "활성"(연결됐거나 아직 게이트2 결정 전인, 즉 종료되지 않은) 것 하나. */
    private Optional<Match> findActiveMatch(UUID userId, LocalDate date) {
        return matchRepository.findByDateAndParticipant(date, userId).stream()
                .filter(m -> !m.isEnded())
                .findFirst();
    }

    /**
     * 매칭이 CONNECTED(양쪽 채팅 수락) 되는 순간 F4로 AI 코멘트를 1회 생성한다.
     * AI 호출 실패는 매칭 성사를 막지 않는다 — 코멘트만 null 로 두고 넘어간다(트랜잭션은 그대로 커밋).
     */
    private void maybeGenerateAiComment(Match match, LocalDate date) {
        if (!match.isConnected() || match.getAiComment() != null) {
            return;
        }
        try {
            FeaturesDto a = featuresDtoOf(match.getUserA().getUserId(), date);
            FeaturesDto b = featuresDtoOf(match.getUserB().getUserId(), date);
            match.assignAiComment(aiServiceClient.generateDescription(a, b, match.getSimilarityScore()));
        } catch (Exception e) {
            log.warn("AI 코멘트 생성 실패 (matchId={}) — 매칭은 유지하고 코멘트만 보류", match.getMatchId(), e);
        }
    }

    /**
     * 매칭이 CONNECTED(양쪽 채팅 수락) 되는 순간 F6으로 아이스브레이킹 질문을 1회 생성한다.
     * AI 호출 실패는 매칭 성사를 막지 않는다 — 질문만 null 로 두고 넘어간다(트랜잭션은 그대로 커밋).
     */
    private void maybeGenerateIcebreaker(Match match, LocalDate date) {
        if (!match.isConnected() || match.getIcebreakerQuestion() != null) {
            return;
        }
        try {
            FeaturesDto a = featuresDtoOf(match.getUserA().getUserId(), date);
            FeaturesDto b = featuresDtoOf(match.getUserB().getUserId(), date);
            match.assignIcebreakerQuestion(aiServiceClient.generateIcebreaker(a, b));
        } catch (Exception e) {
            log.warn("아이스브레이킹 질문 생성 실패 (matchId={}) — 매칭은 유지하고 질문만 보류", match.getMatchId(), e);
        }
    }

    private FeaturesDto featuresDtoOf(UUID userId, LocalDate date) {
        AnalysisResult analysis = analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, date)
                .orElseThrow(() -> new MatchException(MatchErrorCode.ANALYSIS_NOT_FOUND));
        return deserialize(analysis);
    }

    /**
     * 매칭 행이 있을 때 상대 사진·태그까지 채워 응답을 조립한다.
     * 상대 신원·사진·태그는 MATCHED부터 공개된다(사진은 업로드 시 이미 얼굴 블러 처리됨).
     * 그 안에서 유사도·근거·AI코멘트는 다시 CONNECTED로 게이팅된다.
     * (공개 시점 소유권은 F5 reveal 열린 항목 — 현재는 MATCHED 공개로 둔다.)
     */
    private MatchResultResponse toResult(Match match, UUID viewerId) {
        UUID partnerId = match.isUserA(viewerId)
                ? match.getUserB().getUserId()
                : match.getUserA().getUserId();
        List<String> partnerPhotoUrls = photoService.getTodayPhotoUrls(partnerId);
        List<String> partnerTags = partnerTags(partnerId, match.getDate());
        return MatchResultResponse.fromMatch(match, viewerId, partnerPhotoUrls, partnerTags);
    }

    /** 상대의 오늘 분석에서 대표 태그(장소 카테고리 · 시간대 · 분위기)를 뽑는다. 분석이 없으면 빈 목록. */
    private List<String> partnerTags(UUID partnerId, LocalDate date) {
        return analysisResultRepository.findByUser_UserIdAndAnalysisDate(partnerId, date)
                .map(this::deserialize)
                .map(this::flattenTags)
                .orElseGet(List::of);
    }

    private List<String> flattenTags(FeaturesDto f) {
        List<String> tags = new ArrayList<>();
        if (f.getScene() != null) {
            f.getScene().forEach(s -> tags.add(s.getCategory()));
        }
        if (f.getTimeOfDay() != null) {
            tags.addAll(f.getTimeOfDay());
        }
        if (f.getMood() != null) {
            tags.addAll(f.getMood());
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .limit(6)
                .toList();
    }

    /** F2가 통짜 JSON으로 저장한 features를 유사도 계산 입력으로 되돌린다. */
    private AnalysisFeatures toFeatures(AnalysisResult analysis) {
        return AnalysisFeatures.from(deserialize(analysis));
    }

    private FeaturesDto deserialize(AnalysisResult analysis) {
        try {
            return objectMapper.readValue(analysis.getFeaturesJson(), FeaturesDto.class);
        } catch (JsonProcessingException e) {
            throw new MatchException(MatchErrorCode.FEATURES_DESERIALIZATION_FAILED);
        }
    }

    private String serialize(SimilarityResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new MatchException(MatchErrorCode.SCORE_SERIALIZATION_FAILED);
        }
    }
}
