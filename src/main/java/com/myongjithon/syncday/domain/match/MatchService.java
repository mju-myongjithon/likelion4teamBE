package com.myongjithon.syncday.domain.match;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myongjithon.syncday.domain.analysis.DailyAnalysis;
import com.myongjithon.syncday.domain.analysis.DailyAnalysisRepository;
import com.myongjithon.syncday.domain.match.dto.MatchResponse;
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

    private final DailyAnalysisRepository dailyAnalysisRepository;
    private final MatchRepository matchRepository;
    private final SimilarityCalculator similarityCalculator;
    private final ObjectMapper objectMapper;

    /**
     * 대상 유저의 해당 날짜 매칭을 생성한다. 이미 매칭돼 있으면 기존 결과를 그대로 반환한다(멱등).
     * 후보군은 같은 날짜 · 반대 캠퍼스 · 아직 매칭되지 않은 유저이며, 그 중 유사도가 가장 높은 상대를 고른다.
     */
    @Transactional
    public MatchResponse createMatchForUser(UUID userId, LocalDate date) {
        DailyAnalysis target = dailyAnalysisRepository.findByUser_UserIdAndDate(userId, date)
                .orElseThrow(() -> new MatchException(MatchErrorCode.ANALYSIS_NOT_FOUND));

        // 멱등성: 오늘 이미 매칭됐다면 재계산 없이 기존 매칭 반환.
        Optional<Match> existing = matchRepository.findByDateAndParticipant(date, userId);
        if (existing.isPresent()) {
            return MatchResponse.of(existing.get(), userId);
        }

        AppUser targetUser = target.getUser();

        // 이미 매칭이 성사된 유저는 후보에서 제외 (1:1 배타).
        Set<UUID> alreadyMatched = matchRepository.findByDate(date).stream()
                .flatMap(m -> Stream.of(m.getUserA().getUserId(), m.getUserB().getUserId()))
                .collect(Collectors.toSet());

        List<DailyAnalysis> candidates = dailyAnalysisRepository
                .findByDateAndUser_CampusNot(date, targetUser.getCampus()).stream()
                .filter(candidate -> !alreadyMatched.contains(candidate.getUser().getUserId()))
                .toList();

        if (candidates.isEmpty()) {
            throw new MatchException(MatchErrorCode.NO_MATCH_CANDIDATE);
        }

        AnalysisFeatures targetFeatures = target.toFeatures();

        DailyAnalysis best = null;
        SimilarityResult bestResult = null;
        for (DailyAnalysis candidate : candidates) {
            SimilarityResult result = similarityCalculator.calculate(targetFeatures, candidate.toFeatures());
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

        return MatchResponse.of(saved, userId);
    }

    /** 유저의 해당 날짜 매칭 조회. 없으면 아직 매칭 상대가 없는 것으로 본다. */
    @Transactional(readOnly = true)
    public MatchResponse getMatch(UUID userId, LocalDate date) {
        Match match = matchRepository.findByDateAndParticipant(date, userId)
                .orElseThrow(() -> new MatchException(MatchErrorCode.NO_MATCH_CANDIDATE));
        return MatchResponse.of(match, userId);
    }

    private String serialize(SimilarityResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new MatchException(MatchErrorCode.SCORE_SERIALIZATION_FAILED);
        }
    }
}
