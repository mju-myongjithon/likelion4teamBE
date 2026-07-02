package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.user.AppUser;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photo")
public class Photo {
    @Id
    @GeneratedValue
    private UUID photoId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    @Column(name = "is_privacy_mode", nullable = false)
    private Boolean isPrivacyMode = true;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "analysis_id")
    private UUID analysisId;   // F2 완료 전엔 null
}