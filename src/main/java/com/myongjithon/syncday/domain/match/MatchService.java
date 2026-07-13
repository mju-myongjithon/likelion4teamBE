package com.myongjithon.syncday.domain.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.AnalysisResult;
import com.myongjithon.syncday.domain.analysis.AnalysisResultRepository;
import com.myongjithon.syncday.domain.analysis.MatchDecision;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.domain.match.dto.MatchResultResponse;
import com.myongjithon.syncday.domain.match.similarity.AnalysisFeatures;
import com.myongjithon.syncday.domain.match.similarity.SimilarityCalculator;
import com.myongjithon.syncday.domain.match.similarity.SimilarityResult;
import com.myongjithon.syncday.domain.user.AppUser;
import com.myongjithon.syncday.global.exception.MatchErrorCode;
import com.myongjithon.syncday.global.exception.MatchException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final AnalysisResultRepository analysisResultRepository;
    private final MatchRepository matchRepository;
    private final SimilarityCalculator similarityCalculator;
    private final ObjectMapper objectMapper;

    /**
     * 대상 유저가 매칭을 <b>수락</b>하고(게이트1 opt-in) 매칭을 시도한다. 이미 매칭돼 있으면 기존 결과를 그대로 반환한다(멱등).
     * 후보군은 같은 날짜 · 반대 캠퍼스 · <b>매칭을 수락한</b> · 아직 매칭되지 않은 유저이며, 그 중 유사도가 가장 높은 상대를 고른다.
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

        // 멱등성: 오늘 이미 매칭됐다면 재계산 없이 기존 매칭 반환.
        Optional<Match> existing = matchRepository.findByDateAndParticipant(date, userId);
        if (existing.isPresent()) {
            return MatchResultResponse.fromMatch(existing.get(), userId);
        }

        AppUser targetUser = target.getUser();

        // 이미 매칭이 성사된 유저는 후보에서 제외 (1:1 배타).
        Set<UUID> alreadyMatched = matchRepository.findByDate(date).stream()
                .flatMap(m -> Stream.of(m.getUserA().getUserId(), m.getUserB().getUserId()))
                .collect(Collectors.toSet());

        // 후보는 "매칭을 수락(ACCEPTED)한" 반대 캠퍼스 유저만. 미수락(NONE)·거부(DECLINED)는 상대로 잡히지 않는다.
        List<AnalysisResult> candidates = analysisResultRepository
                .findByAnalysisDateAndUser_CampusNotAndMatchDecision(date, targetUser.getCampus(), MatchDecision.ACCEPTED)
                .stream()
                .filter(candidate -> !alreadyMatched.contains(candidate.getUser().getUserId()))
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

        return MatchResultResponse.fromMatch(saved, userId);
    }

    /**
     * 대상 유저가 매칭을 <b>거부</b>한다(게이트1). 아직 매칭 전일 때만 유효하며, 후보 풀에서 빠진다.
     * 이미 매칭이 성사된 뒤라면 거부하기엔 늦었으므로 기존 매칭(MATCHED)을 그대로 돌려준다.
     */
    @Transactional
    public MatchResultResponse declineMatch(UUID userId, LocalDate date) {
        AnalysisResult target = analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, date)
                .orElseThrow(() -> new MatchException(MatchErrorCode.ANALYSIS_NOT_FOUND));

        Optional<Match> existing = matchRepository.findByDateAndParticipant(date, userId);
        if (existing.isPresent()) {
            return MatchResultResponse.fromMatch(existing.get(), userId);
        }

        target.declineMatching();
        return MatchResultResponse.declined();
    }

    /**
     * 게이트2: 뷰어가 채팅 참여를 수락/거부한다. 매칭이 성사된 뒤에만 유효하다.
     * 양쪽이 모두 수락하면 매칭이 연결(CONNECTED)되고 connectedAt 이 기록되어 F5가 채팅방을 연다.
     * 관리 엔티티라 커밋 시 변경이 자동 반영된다(멱등: 이미 연결/종료면 변화 없음).
     */
    @Transactional
    public MatchResultResponse applyChatDecision(UUID userId, LocalDate date, Gate2Decision decision) {
        Match match = matchRepository.findByDateAndParticipant(date, userId)
                .orElseThrow(() -> new MatchException(MatchErrorCode.MATCH_NOT_FOUND));
        match.applyChatDecision(userId, decision);
        return MatchResultResponse.fromMatch(match, userId);
    }

    /**
     * 유저의 해당 날짜 매칭 상태 조회(읽기 전용, 매칭을 새로 시도하지 않음).
     * 매칭이 없으면 유저의 수락/거부 상태에 따라 NOT_REQUESTED / PENDING / DECLINED 로 내려준다.
     * FE가 화면 재진입 시 어느 단계인지 판단하고, "매칭중..." 화면에서 MATCHED로 바뀌는지 폴링하는 데 쓴다.
     */
    @Transactional(readOnly = true)
    public MatchResultResponse getMatch(UUID userId, LocalDate date) {
        Optional<Match> match = matchRepository.findByDateAndParticipant(date, userId);
        if (match.isPresent()) {
            return MatchResultResponse.fromMatch(match.get(), userId);
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

    /** F2가 통짜 JSON으로 저장한 features를 유사도 계산 입력으로 되돌린다. */
    private AnalysisFeatures toFeatures(AnalysisResult analysis) {
        try {
            FeaturesDto features = objectMapper.readValue(analysis.getFeaturesJson(), FeaturesDto.class);
            return AnalysisFeatures.from(features);
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
