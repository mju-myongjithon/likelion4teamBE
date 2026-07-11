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

    public static final int REQUIRED_PHOTO_COUNT = 3;
    public static final int MAX_PHOTO_COUNT = 10;

    @Id
    @GeneratedValue
    private UUID photoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "is_privacy_mode", nullable = false)
    private Boolean isPrivacyMode;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "analysis_id")
    private UUID analysisId;

    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Builder
    public Photo(AppUser user, String s3Key, Boolean isPrivacyMode) {
        this.user = user;
        this.s3Key = s3Key;
        this.isPrivacyMode = isPrivacyMode != null ? isPrivacyMode : true;
        this.uploadedAt = LocalDateTime.now();
    }
}