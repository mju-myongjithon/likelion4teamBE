package com.myongjithon.syncday.domain.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    /**
     * 특정 유저가 해당 날짜에 관여한 모든 매칭 행(A/B 어느 쪽이든). 종료(ENDED)된 뒤에도 다시 매칭을
     * 시도할 수 있게 되면서, 하루에 한 유저가 매칭 행을 여러 개(과거 ENDED + 새로 성사된 것) 가질 수
     * 있다 — 그래서 단일 결과가 아니라 목록으로 반환하고, "지금 유효한(활성) 매칭이 뭔지"는
     * 호출부에서 {@link Match#isEnded()} 로 걸러서 판단한다.
     */
    @Query("""
            select m from Match m
            where m.date = :date
              and (m.userA.userId = :userId or m.userB.userId = :userId)
            """)
    List<Match> findByDateAndParticipant(@Param("date") LocalDate date,
                                         @Param("userId") UUID userId);

    /** 해당 날짜의 모든 매칭. 후보군에서 이미 매칭된 유저를 제외할 때 사용. */
    List<Match> findByDate(LocalDate date);
}
