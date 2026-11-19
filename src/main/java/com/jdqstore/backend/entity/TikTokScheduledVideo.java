package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tiktok_scheduled_videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TikTokScheduledVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "sede_name", nullable = false)
    private String sedeName;

    @Column(nullable = false)
    private String title;

    @Column(length = 100)
    private String topic;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(length = 20)
    @Builder.Default
    private String duration = "0:24";

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "hashtags", length = 500)
    private String hashtags;

    @Column(name = "video_file_url", columnDefinition = "TEXT")
    private String videoFileUrl;

    @Column(name = "video_file_path", columnDefinition = "TEXT")
    private String videoFilePath;

    @Column(name = "privacy_level", length = 50)
    @Builder.Default
    private String privacyLevel = "PUBLIC_TO_EVERYONE";

    @Column(name = "allow_comments")
    @Builder.Default
    private Boolean allowComments = true;

    @Column(name = "allow_duet")
    @Builder.Default
    private Boolean allowDuet = true;

    @Column(name = "allow_stitch")
    @Builder.Default
    private Boolean allowStitch = true;

    @Column(name = "is_draft")
    @Builder.Default
    private Boolean isDraft = false;

    @Column(name = "tiktok_publish_id")
    private String tiktokPublishId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(length = 30)
    @Builder.Default
    private String status = "PROGRAMADO"; // PROGRAMADO, PUBLICADO, ERROR

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
