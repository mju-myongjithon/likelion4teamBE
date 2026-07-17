package com.myongjithon.syncday.domain.demo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeedAccountRepository extends JpaRepository<SeedAccount, UUID> {
    // existsById(userId) 를 그대로 사용
}