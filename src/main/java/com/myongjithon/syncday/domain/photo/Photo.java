package com.myongjithon.syncday.domain.photo;

import com.myongjithon.syncday.domain.user.AppUser;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "photo")
@Getter
@NoArgsConstructor
public class Photo {

    @Id
    @GeneratedValue
    private UUID photoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "image_url", length = 500, nullable = false)
    private String imageUrl;

    @Column(name = "is_privacy_mode", nullable = false)
    private Boolean isPrivacyMode = true;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "analysis_id")
    private UUID analysisId;

    @Builder
    public Photo(AppUser user, String imageUrl, Boolean isPrivacyMode) {
        this.user = user;
        this.imageUrl = imageUrl;
        this.isPrivacyMode = isPrivacyMode != null ? isPrivacyMode : true;
        this.uploadedAt = LocalDateTime.now();
    }
}