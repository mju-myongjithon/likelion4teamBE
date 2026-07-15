package com.myongjithon.syncday.domain.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 데모 시드 계정 표시(F5 자동화 대상). row 존재 = 시드 계정.
 * 온보딩 API로는 등록 불가 — 시딩 스크립트가 INSERT 로 직접 넣는다:
 *   INSERT INTO seed_account (user_id) SELECT user_id FROM app_user WHERE nickname IN (...);
 * 실 유저는 이 테이블에 절대 없어야 하며, 자동 수락·자동 응답은 전부 여기로 분기한다.
 */
@Entity
@Table(name = "seed_account")
@Getter
@NoArgsConstructor
public class SeedAccount {

    @Id
    @Column(name = "user_id")
    private UUID userId;
}