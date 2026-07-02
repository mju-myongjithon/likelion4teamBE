package com.myongjithon.syncday.domain.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue
    private UUID userId;

    private String campus;   // "인문캠" / "자연캠"
    private String nickname;

    private Integer currentStreak = 0;
    private LocalDate lastParticipationDate;
}
