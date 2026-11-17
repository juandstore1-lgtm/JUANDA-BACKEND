package com.jdqstore.backend.controller;

import com.jdqstore.backend.audit.Auditable;
import com.jdqstore.backend.entity.TikTokAccount;
import com.jdqstore.backend.entity.TikTokDayNote;
import com.jdqstore.backend.entity.TikTokScheduledVideo;
import com.jdqstore.backend.service.TikTokService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    @GetMapping("/oauth/authorize-url")
    public ResponseEntity<Map<String, String>> getAuthorizeUrl(@RequestParam(required = false, defaultValue = "TODAS") String sede) {
        return ResponseEntity.ok(Map.of("url", tikTokService.getAuthorizationUrl(sede)));
    }

    @PostMapping("/oauth/callback")
    @Auditable(action = "TIKTOK_OAUTH_CONNECT", entity = "TIKTOK_ACCOUNT")
    public ResponseEntity<TikTokAccount> handleOAuthCallback(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String sede = body.get("sede");
        return ResponseEntity.ok(tikTokService.exchangeOAuthCode(code, sede));
    }

    // --- Cuentas por sede ---
    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAccounts() {
        return ResponseEntity.ok(tikTokService.getAccountsStatus());
    }

    @PostMapping("/accounts/validate")
    public ResponseEntity<Map<String, Object>> validateAccount(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        return ResponseEntity.ok(tikTokService.validateTikTokAccount(username));
    }

    @PostMapping("/accounts/link")
    @Auditable(action = "LINK_TIKTOK_ACCOUNT", entity = "TIKTOK_ACCOUNT")
    public ResponseEntity<TikTokAccount> linkAccount(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(tikTokService.linkAccount(body));
    }

    @PostMapping("/accounts/{id}/disconnect")
    @Auditable(action = "DISCONNECT_TIKTOK_ACCOUNT", entity = "TIKTOK_ACCOUNT")
    public ResponseEntity<Map<String, String>> disconnectAccount(@PathVariable Long id) {
        tikTokService.disconnectAccount(id);
        return ResponseEntity.ok(Map.of("message", "Cuenta de TikTok desconectada correctamente"));
    }

    // --- Resumen General ---
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false, defaultValue = "TODAS") String sede,
            @RequestParam(required = false, defaultValue = "ESTA_SEMANA") String period
    ) {
        return ResponseEntity.ok(tikTokService.getSummary(sede, period));
    }

    // --- Historial de Publicaciones ---
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(required = false, defaultValue = "TODAS") String sede,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "TODOS") String contentType,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(tikTokService.getHistory(sede, dateFrom, dateTo, search, contentType, page, size));
    }

    // --- Programación Semanal ---
    @GetMapping("/scheduled")
    public ResponseEntity<Map<String, Object>> getWeeklySchedule(
            @RequestParam(required = false, defaultValue = "TODAS") String sede,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ResponseEntity.ok(tikTokService.getWeeklySchedule(sede, weekStart));
    }

    @PostMapping("/scheduled/notes")
    @Auditable(action = "SAVE_TIKTOK_NOTE", entity = "TIKTOK_SCHEDULE")
    public ResponseEntity<TikTokDayNote> saveDayNote(@RequestBody Map<String, Object> body) {
        String sede = (String) body.get("sedeName");
        String dateStr = (String) body.get("date");
        String note = (String) body.get("note");
        LocalDate date = LocalDate.parse(dateStr);
        return ResponseEntity.ok(tikTokService.saveDayNote(sede, date, note));
    }

    @PostMapping("/scheduled/sync")
    public ResponseEntity<Map<String, Object>> syncScheduled(
            @RequestParam(required = false, defaultValue = "TODAS") String sede,
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
