package com.myongjithon.syncday.domain.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.AnalysisResult;
import com.myongjithon.syncday.domain.analysis.AnalysisResultRepository;
import com.myongjithon.syncday.domain.analysis.dto.FeaturesDto;
import com.myongjithon.syncday.domain.match.dto.MatchResponse;
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
     * 대상 유저의 해당 날짜 매칭을 시도한다. 이미 매칭돼 있으면 기존 결과를 그대로 반환한다(멱등).
     * 후보군은 같은 날짜 · 반대 캠퍼스 · 아직 매칭되지 않은 유저이며, 그 중 유사도가 가장 높은 상대를 고른다.
     *
     * <p>하루 매칭 특성상, 먼저 분석을 끝낸 유저는 반대 캠퍼스 상대가 아직 아무도 없을 수 있다.
     * 이는 에러가 아니라 대기 상태이므로 {@link MatchResultResponse#pending()} 를 반환한다.
     * FE는 이 응답을 받는 동안 "매칭중..." 화면을 유지하며 폴링한다.
     */
    @Transactional
    public MatchResultResponse createMatchForUser(UUID userId, LocalDate date) {
        AnalysisResult target = analysisResultRepository.findByUser_UserIdAndAnalysisDate(userId, date)
                .orElseThrow(() -> new MatchException(MatchErrorCode.ANALYSIS_NOT_FOUND));

        // 멱등성: 오늘 이미 매칭됐다면 재계산 없이 기존 매칭 반환.
        Optional<Match> existing = matchRepository.findByDateAndParticipant(date, userId);
        if (existing.isPresent()) {
            return MatchResultResponse.matched(MatchResponse.of(existing.get(), userId));
        }

        AppUser targetUser = target.getUser();

        // 이미 매칭이 성사된 유저는 후보에서 제외 (1:1 배타).
        Set<UUID> alreadyMatched = matchRepository.findByDate(date).stream()
                .flatMap(m -> Stream.of(m.getUserA().getUserId(), m.getUserB().getUserId()))
                .collect(Collectors.toSet());

        List<AnalysisResult> candidates = analysisResultRepository
                .findByAnalysisDateAndUser_CampusNot(date, targetUser.getCampus()).stream()
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
        Match saved = matchRepository.save(match);

        return MatchResultResponse.matched(MatchResponse.of(saved, userId));
    }

    /**
     * 유저의 해당 날짜 매칭 조회. 매칭이 없으면 에러가 아니라 대기(PENDING)로 본다.
     * FE가 결과 탭을 열어두고 이 엔드포인트를 폴링하다가 MATCHED로 바뀌면 결과 카드를 띄운다.
     */
    @Transactional(readOnly = true)
    public MatchResultResponse getMatch(UUID userId, LocalDate date) {
        return matchRepository.findByDateAndParticipant(date, userId)
                .map(match -> MatchResultResponse.matched(MatchResponse.of(match, userId)))
                .orElseGet(MatchResultResponse::pending);
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
