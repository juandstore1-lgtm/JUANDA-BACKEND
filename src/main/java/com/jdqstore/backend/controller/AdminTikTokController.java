package com.jdqstore.backend.controller;

import com.jdqstore.backend.audit.Auditable;
import com.jdqstore.backend.entity.TikTokAccount;
import com.jdqstore.backend.entity.TikTokDayNote;
import com.jdqstore.backend.entity.TikTokScheduledVideo;
import com.jdqstore.backend.service.TikTokService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tiktok/admin")
@RequiredArgsConstructor
public class AdminTikTokController {

    private final TikTokService tikTokService;

    // --- Configuración e Integración OAuth 2.0 Oficial ---
    @GetMapping("/oauth/config")
    public ResponseEntity<Map<String, Object>> getOAuthConfig() {
        return ResponseEntity.ok(tikTokService.getOAuthConfig());
    }

    @PostMapping("/oauth/config")
    @Auditable(action = "UPDATE_TIKTOK_CONFIG", entity = "TIKTOK_CONFIG")
    public ResponseEntity<Map<String, Object>> saveOAuthConfig(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(tikTokService.saveOAuthConfig(body));
    }

    @GetMapping("/oauth/authorize-url")
    public ResponseEntity<Map<String, String>> getAuthorizeUrl(
            @RequestParam(required = false, defaultValue = "GLOBAL") String sede,
            @RequestParam(required = false) String scope
    ) {
        return ResponseEntity.ok(Map.of("url", tikTokService.getAuthorizationUrl(sede, scope)));
    }

    @PostMapping("/oauth/callback")
    @Auditable(action = "TIKTOK_OAUTH_CONNECT", entity = "TIKTOK_ACCOUNT")
    public ResponseEntity<TikTokAccount> handleOAuthCallback(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String sede = body.getOrDefault("sede", "GLOBAL");
        return ResponseEntity.ok(tikTokService.exchangeOAuthCode(code, sede));
    }

    // --- Cuenta Global (Administrador Global) ---
    @GetMapping("/account")
    public ResponseEntity<Map<String, Object>> getGlobalAccount() {
        return ResponseEntity.ok(tikTokService.getGlobalAccount());
    }

    @PostMapping("/account/disconnect")
    @Auditable(action = "DISCONNECT_TIKTOK_GLOBAL_ACCOUNT", entity = "TIKTOK_ACCOUNT")
    public ResponseEntity<Map<String, String>> disconnectGlobalAccount() {
        tikTokService.disconnectGlobalAccount();
        return ResponseEntity.ok(Map.of("message", "Cuenta de TikTok desconectada exitosamente"));
    }

    // --- Retrocompatibilidad con sedes ---
    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAccounts() {
        return ResponseEntity.ok(tikTokService.getAccountsStatus());
    }

    @PostMapping("/accounts/{id}/disconnect")
    @Auditable(action = "DISCONNECT_TIKTOK_ACCOUNT", entity = "TIKTOK_ACCOUNT")
    public ResponseEntity<Map<String, String>> disconnectAccount(@PathVariable Long id) {
        tikTokService.disconnectGlobalAccount();
        return ResponseEntity.ok(Map.of("message", "Cuenta de TikTok desconectada correctamente"));
    }

    // --- Publicar Video Real (Content Posting API v2) ---
    @PostMapping(value = "/publish", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auditable(action = "PUBLISH_TIKTOK_VIDEO", entity = "TIKTOK_VIDEO")
    public ResponseEntity<Map<String, Object>> publishVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "privacyLevel", required = false, defaultValue = "PUBLIC_TO_EVERYONE") String privacyLevel,
            @RequestParam(value = "allowComments", required = false, defaultValue = "true") Boolean allowComments,
            @RequestParam(value = "allowDuet", required = false, defaultValue = "true") Boolean allowDuet,
            @RequestParam(value = "allowStitch", required = false, defaultValue = "true") Boolean allowStitch,
            @RequestParam(value = "coverTimestampMs", required = false, defaultValue = "1000") Long coverTimestampMs,
            @RequestParam(value = "isDraft", required = false, defaultValue = "false") Boolean isDraft
    ) {
        return ResponseEntity.ok(tikTokService.publishVideo(
                video, title, hashtags, privacyLevel, allowComments, allowDuet, allowStitch, coverTimestampMs, isDraft
        ));
    }

    // --- Programar Video para Publicación Semanal ---
    @PostMapping(value = "/schedule", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Auditable(action = "SCHEDULE_TIKTOK_VIDEO", entity = "TIKTOK_SCHEDULE")
    public ResponseEntity<TikTokScheduledVideo> scheduleVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "hashtags", required = false) String hashtags,
            @RequestParam(value = "privacyLevel", required = false, defaultValue = "PUBLIC_TO_EVERYONE") String privacyLevel,
            @RequestParam(value = "allowComments", required = false, defaultValue = "true") Boolean allowComments,
            @RequestParam(value = "allowDuet", required = false, defaultValue = "true") Boolean allowDuet,
            @RequestParam(value = "allowStitch", required = false, defaultValue = "true") Boolean allowStitch,
            @RequestParam(value = "coverTimestampMs", required = false, defaultValue = "1000") Long coverTimestampMs,
            @RequestParam("scheduledAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
            @RequestParam(value = "isDraft", required = false, defaultValue = "false") Boolean isDraft
    ) {
        return ResponseEntity.ok(tikTokService.scheduleVideo(
                video, title, hashtags, privacyLevel, allowComments, allowDuet, allowStitch, coverTimestampMs, scheduledAt, isDraft
        ));
    }

    // --- Resumen General ---
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false, defaultValue = "GLOBAL") String sede,
            @RequestParam(required = false, defaultValue = "ESTA_SEMANA") String period
    ) {
        return ResponseEntity.ok(tikTokService.getSummary(sede, period));
    }

    // --- Historial de Publicaciones ---
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(required = false, defaultValue = "GLOBAL") String sede,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "TODOS") String contentType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(tikTokService.getHistory(sede, dateFrom, dateTo, search, contentType, page, size));
    }

    @PostMapping("/videos/sync")
    public ResponseEntity<Map<String, Object>> syncVideos() {
        return ResponseEntity.ok(tikTokService.syncVideosNow());
    }

    // --- Programación Semanal ---
    @GetMapping("/scheduled")
    public ResponseEntity<Map<String, Object>> getWeeklySchedule(
            @RequestParam(required = false, defaultValue = "GLOBAL") String sede,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ResponseEntity.ok(tikTokService.getWeeklySchedule(sede, weekStart));
    }

    @PostMapping("/scheduled/notes")
    @Auditable(action = "SAVE_TIKTOK_NOTE", entity = "TIKTOK_SCHEDULE")
    public ResponseEntity<TikTokDayNote> saveDayNote(@RequestBody Map<String, Object> body) {
        String sede = (String) body.getOrDefault("sedeName", "GLOBAL");
        String dateStr = (String) body.get("date");
        String note = (String) body.get("note");
        LocalDate date = LocalDate.parse(dateStr);
        return ResponseEntity.ok(tikTokService.saveDayNote(sede, date, note));
    }

    @PostMapping("/scheduled/sync")
    public ResponseEntity<Map<String, Object>> syncScheduled(
            @RequestParam(required = false, defaultValue = "GLOBAL") String sede,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ResponseEntity.ok(tikTokService.getWeeklySchedule(sede, weekStart));
    }

    @PostMapping("/scheduled")
    @Auditable(action = "CREATE_SCHEDULED_VIDEO", entity = "TIKTOK_SCHEDULE")
    public ResponseEntity<TikTokScheduledVideo> addScheduledVideo(@RequestBody TikTokScheduledVideo video) {
        return ResponseEntity.ok(tikTokService.addScheduledVideo(video));
    }

    @DeleteMapping("/scheduled/{id}")
    @Auditable(action = "DELETE_SCHEDULED_VIDEO", entity = "TIKTOK_SCHEDULE")
    public ResponseEntity<Map<String, String>> deleteScheduledVideo(@PathVariable Long id) {
        tikTokService.deleteScheduledVideo(id);
        return ResponseEntity.ok(Map.of("message", "Video programado eliminado correctamente"));
    }
}
