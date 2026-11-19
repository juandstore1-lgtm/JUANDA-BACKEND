package com.jdqstore.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tiktok_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TikTokAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sede_id")
    private Long sedeId;

    @Column(name = "sede_name", nullable = false)
    private String sedeName;

    @Column(nullable = false)
    private String username; // e.g. @juandstore_vc

    @Column(name = "display_name")
    private String displayName; // e.g. JUAND'STORE Villa Colombia

    @Column(name = "account_id")
    private String accountId; // e.g. 738291049281736

    @Column(name = "open_id")
    private String openId;

    @Column(name = "scope")
    private String scope;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private String status = "CONNECTED"; // CONNECTED, DISCONNECTED

    @Column(name = "permissions")
    @Builder.Default
    private String permissions = "Lectura de perfil y videos públicos";

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (connectedAt == null) {
            connectedAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
