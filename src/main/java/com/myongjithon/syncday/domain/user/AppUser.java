package com.myongjithon.syncday.domain.user;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue
    private UUID userId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Campus campus;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "current_streak")
    private Integer currentStreak = 0;

    @Column(name = "last_participation_date")
    private LocalDate lastParticipationDate;

    @Builder
    public AppUser(Campus campus, String nickname) {
        this.campus = campus;
        this.nickname = nickname;
        this.currentStreak = 0;
    }
}