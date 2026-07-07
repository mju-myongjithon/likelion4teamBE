package com.myongjithon.syncday.domain.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyAnalysisRepository extends JpaRepository<DailyAnalysis, UUID> {

    /** 매칭 대상 유저의 오늘 분석. */
    Optional<DailyAnalysis> findByUser_UserIdAndDate(UUID userId, LocalDate date);

    /**
     * 매칭 후보군: 같은 날짜의 반대 캠퍼스 유저 분석.
     * campus 파라미터에 대상 유저의 캠퍼스를 넣으면 본인·같은 캠퍼스는 자동 제외된다.
     *
     * ⚠ "분석 완료" 판정은 F2 확정 전까지 row 존재로 간주한다. (docs/f3-handoff-from-f2.md §2-C)
     */
    List<DailyAnalysis> findByDateAndUser_CampusNot(LocalDate date, String campus);
}
