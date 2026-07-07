package com.myongjithon.syncday.domain.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    /** 특정 유저가 해당 날짜에 이미 매칭됐는지 (A/B 어느 쪽이든). 매칭 멱등성 확보용. */
    @Query("""
            select m from Match m
            where m.date = :date
              and (m.userA.userId = :userId or m.userB.userId = :userId)
            """)
    Optional<Match> findByDateAndParticipant(@Param("date") LocalDate date,
                                             @Param("userId") UUID userId);

    /** 해당 날짜의 모든 매칭. 후보군에서 이미 매칭된 유저를 제외할 때 사용. */
    List<Match> findByDate(LocalDate date);
}
