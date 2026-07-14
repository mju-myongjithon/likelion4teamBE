package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.domain.user.Campus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {
    Optional<AnalysisResult> findByUser_UserIdAndAnalysisDate(UUID userId, LocalDate analysisDate);

    /**
     * F3 매칭 후보군: 같은 날짜의 반대 캠퍼스 유저 중 <b>매칭을 수락(opt-in)한</b> 유저의 분석만.
     * campus 파라미터에 대상 유저의 캠퍼스를 넣으면 본인·같은 캠퍼스는 자동 제외되고,
     * matchDecision 으로 아직 정하지 않았거나(NONE/null) 거부(DECLINED)한 유저도 제외된다.
     */
    List<AnalysisResult> findByAnalysisDateAndUser_CampusNotAndMatchDecision(
            LocalDate analysisDate, Campus campus, MatchDecision matchDecision);
}
