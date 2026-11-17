package com.jdqstore.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdqstore.backend.entity.*;
import com.jdqstore.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TikTokService {

    private final TikTokAccountRepository accountRepository;
    private final TikTokVideoRepository videoRepository;
    private final TikTokScheduledVideoRepository scheduledVideoRepository;
    private final TikTokDayNoteRepository dayNoteRepository;
    private final SedeRepository sedeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tiktok.client-key:}")
    private String clientKey;

    @Value("${tiktok.client-secret:}")
    private String clientSecret;

    @Value("${tiktok.redirect-uri:http://localhost:3000/admin/tiktok/callback}")
    private String redirectUri;

    // --- Configuración y URL de OAuth 2.0 Oficial ---

    public Map<String, Object> getOAuthConfig() {
        boolean configured = clientKey != null && !clientKey.trim().isEmpty() &&
                clientSecret != null && !clientSecret.trim().isEmpty();

        Map<String, Object> res = new HashMap<>();
        res.put("configured", configured);
        res.put("clientKey", (clientKey != null && !clientKey.trim().isEmpty()) ? clientKey.trim() : null);
        res.put("redirectUri", redirectUri);
        res.put("scopes", List.of("user.info.basic", "user.info.profile", "video.list"));
        res.put("requiredCredentials", Map.of(
                "TIKTOK_CLIENT_KEY", "Client Key (App ID) obtenido en developers.tiktok.com",
                "TIKTOK_CLIENT_SECRET", "Client Secret de tu App de TikTok Developers",
                "TIKTOK_REDIRECT_URI", redirectUri + " (Registrada en Redirect URIs de tu app)"
        ));
        return res;
    }

    public String getAuthorizationUrl(String sedeName) {
        String cleanKey = (clientKey != null) ? clientKey.trim() : "";
        if (cleanKey.isEmpty()) {
            throw new IllegalStateException("TIKTOK_CLIENT_KEY no está configurado en las variables del servidor. Configura tus credenciales de TikTok Developers.");
        }

        String safeSede = (sedeName != null && !sedeName.trim().isEmpty()) ? sedeName.trim() : "TODAS";
        String encodedRedirect = URLEncoder.encode(redirectUri.trim(), StandardCharsets.UTF_8);
        String encodedState = URLEncoder.encode(safeSede, StandardCharsets.UTF_8);
        String scopes = "user.info.basic,user.info.profile,video.list";

        return "https://www.tiktok.com/v2/auth/authorize/?"
                + "client_key=" + cleanKey
                + "&response_type=code"
                + "&scope=" + scopes
                + "&redirect_uri=" + encodedRedirect
                + "&state=" + encodedState;
    }

    // --- Intercambio de Código OAuth 2.0 por Token ---

    @Transactional
    public TikTokAccount exchangeOAuthCode(String code, String sedeName) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("El código de autorización de TikTok es requerido.");
        }
        if (clientKey == null || clientKey.trim().isEmpty() || clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalStateException("Las credenciales TIKTOK_CLIENT_KEY o TIKTOK_CLIENT_SECRET no están configuradas.");
        }

        String safeSede = (sedeName != null && !sedeName.trim().isEmpty()) ? sedeName.trim() : "Villa Colombia";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // 1. Solicitar tokens a TikTok
            String formBody = "client_key=" + URLEncoder.encode(clientKey.trim(), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret.trim(), StandardCharsets.UTF_8)
                    + "&code=" + URLEncoder.encode(code.trim(), StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri.trim(), StandardCharsets.UTF_8);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/oauth/token/"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cache-Control", "no-cache")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            if (tokenResponse.statusCode() != 200) {
                log.error("Error al obtener token de TikTok. HTTP {}: {}", tokenResponse.statusCode(), tokenResponse.body());
                throw new IllegalArgumentException("TikTok rechazó la autorización (HTTP " + tokenResponse.statusCode() + "): " + tokenResponse.body());
            }

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
            if (tokenJson.has("error") && !tokenJson.get("error").has("code") || (tokenJson.has("error") && !"ok".equalsIgnoreCase(tokenJson.get("error").path("code").asText()))) {
                String errMsg = tokenJson.path("error").path("message").asText("Error desconocido al autorizar en TikTok");
                throw new IllegalArgumentException("TikTok devolvió un error: " + errMsg);
            }

            JsonNode dataNode = tokenJson.path("data");
            String accessToken = dataNode.path("access_token").asText();
            String refreshToken = dataNode.path("refresh_token").asText();
            long expiresIn = dataNode.path("expires_in").asLong(86400L);
            String openId = dataNode.path("open_id").asText();
            String scope = dataNode.path("scope").asText("user.info.basic,video.list");

            // 2. Consultar perfil con el access token
            HttpRequest userRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/user/info/?fields=open_id,union_id,avatar_url,display_name,username"))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> userResponse = client.send(userRequest, HttpResponse.BodyHandlers.ofString());
            String username = "@cuenta_tiktok";
            String displayName = safeSede;
            String avatarUrl = null;

            if (userResponse.statusCode() == 200) {
                JsonNode userJson = objectMapper.readTree(userResponse.body());
                JsonNode userData = userJson.path("data").path("user");
                if (!userData.isMissingNode()) {
                    String rawUser = userData.path("username").asText("");
                    if (!rawUser.isEmpty()) {
                        username = rawUser.startsWith("@") ? rawUser : "@" + rawUser;
                    }
                    displayName = userData.path("display_name").asText(displayName);
                    avatarUrl = userData.path("avatar_url").asText(null);
                }
            }

            // 3. Buscar sedeId
            Long sedeId = null;
            Optional<Sede> sOpt = sedeRepository.findFirstByBarrioIgnoreCase(safeSede);
            if (!sOpt.isPresent()) {
                sOpt = sedeRepository.findFirstByNameIgnoreCase(safeSede);
            }
            if (sOpt.isPresent()) {
                sedeId = sOpt.get().getId();
            }

            // 4. Guardar cuenta en base de datos
            TikTokAccount acc = accountRepository.findFirstBySedeNameIgnoreCase(safeSede)
                    .orElse(TikTokAccount.builder().sedeName(safeSede).build());

            acc.setSedeId(sedeId);
            acc.setSedeName(safeSede);
            acc.setUsername(username);
            acc.setDisplayName(displayName);
            acc.setAccountId(openId != null && !openId.isEmpty() ? openId : "73" + System.currentTimeMillis());
            acc.setOpenId(openId);
            acc.setScope(scope);
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                acc.setAvatarUrl(avatarUrl);
            }
            acc.setAccessToken(accessToken);
            acc.setRefreshToken(refreshToken);
            acc.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            acc.setStatus("CONNECTED");
            acc.setPermissions("Lectura de perfil y videos públicos");
            acc.setConnectedAt(LocalDateTime.now());

            TikTokAccount saved = accountRepository.save(acc);

            // 5. Sincronizar videos reales de la cuenta
            syncVideosFromTikTok(saved);

            return saved;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excepción al intercambiar código de TikTok OAuth: {}", e.getMessage(), e);
            throw new RuntimeException("Error durante la vinculación OAuth 2.0 con TikTok: " + e.getMessage(), e);
        }
    }

    // --- Sincronizar videos reales de TikTok mediante API Oficial ---

    @Transactional
    public int syncVideosFromTikTok(TikTokAccount account) {
        if (account == null || account.getAccessToken() == null || !"CONNECTED".equalsIgnoreCase(account.getStatus())) {
            return 0;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            String postBody = "{\"max_count\": 20}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/video/list/?fields=id,title,video_description,duration,cover_image_url,create_time,share_url,like_count,comment_count,share_count,view_count"))
                    .header("Authorization", "Bearer " + account.getAccessToken())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(postBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode videosNode = root.path("data").path("videos");
                if (videosNode.isArray()) {
                    int count = 0;
                    for (JsonNode vNode : videosNode) {
                        String videoId = vNode.path("id").asText();
                        if (videoId == null || videoId.isEmpty()) continue;

                        String desc = vNode.path("video_description").asText("");
                        String title = vNode.path("title").asText(desc.isEmpty() ? "Video TikTok" : desc.split("\n")[0]);
                        if (title.length() > 250) title = title.substring(0, 247) + "...";

                        int durationSec = vNode.path("duration").asInt(24);
                        String durationStr = String.format("%d:%02d", durationSec / 60, durationSec % 60);

                        long createTime = vNode.path("create_time").asLong(0);
                        LocalDateTime publishedAt = createTime > 0
                                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(createTime), ZoneId.systemDefault())
                                : LocalDateTime.now();

                        Optional<TikTokVideo> existingOpt = videoRepository.findFirstByVideoId(videoId);
                        TikTokVideo vid = existingOpt.orElse(TikTokVideo.builder().videoId(videoId).build());

                        vid.setAccountId(account.getId());
                        vid.setSedeName(account.getSedeName());
                        vid.setTitle(title);
                        vid.setDescription(desc);
                        vid.setThumbnailUrl(vNode.path("cover_image_url").asText(null));
                        vid.setVideoUrl(vNode.path("share_url").asText(null));
                        vid.setDuration(durationStr);
                        vid.setPublishedAt(publishedAt);
                        vid.setViewCount(vNode.path("view_count").asLong(0));
                        vid.setLikeCount(vNode.path("like_count").asLong(0));
                        vid.setCommentCount(vNode.path("comment_count").asLong(0));
                        vid.setShareCount(vNode.path("share_count").asLong(0));
                        vid.setContentType("Video");

                        videoRepository.save(vid);
                        count++;
                    }
                    log.info("Sincronizados {} videos oficiales de TikTok para la sede {}", count, account.getSedeName());
                    return count;
                }
            } else {
                log.warn("TikTok Video List devolvió HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("No se pudieron sincronizar videos de TikTok para {}: {}", account.getSedeName(), e.getMessage());
        }
        return 0;
    }

    // --- Cuentas Vinculadas por Sede ---

    public List<Map<String, Object>> getAccountsStatus() {
        List<Sede> sedes = sedeRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Sede s : sedes) {
            String sedeName = s.getBarrio() != null && !s.getBarrio().trim().isEmpty() ? s.getBarrio().trim() : s.getName();
            Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCase(sedeName);
            if (!accOpt.isPresent() && s.getId() != null) {
                accOpt = accountRepository.findBySedeId(s.getId());
            }

            Map<String, Object> map = new HashMap<>();
            map.put("sedeId", s.getId());
            map.put("sedeName", sedeName);
            map.put("storeName", s.getName());
            map.put("address", s.getAddress());
            map.put("storeImage", s.getEffectiveImageUrl());

            if (accOpt.isPresent() && "CONNECTED".equalsIgnoreCase(accOpt.get().getStatus())) {
                TikTokAccount acc = accOpt.get();
                map.put("connected", true);
                map.put("accountId", acc.getId());
                map.put("tiktokAccountId", acc.getAccountId());
                map.put("openId", acc.getOpenId());
                map.put("username", acc.getUsername());
                map.put("displayName", acc.getDisplayName());
                map.put("avatarUrl", acc.getAvatarUrl());
                map.put("connectedAt", acc.getConnectedAt());
                map.put("permissions", acc.getPermissions());
                map.put("status", "Conectada");
            } else {
                map.put("connected", false);
                map.put("status", "Sin vincular");
                map.put("username", null);
                map.put("displayName", null);
                map.put("avatarUrl", null);
            }
            result.add(map);
        }

        return result;
    }

    // --- Validación en vivo con TikTok API (oEmbed oficial) ---

    public Map<String, Object> validateTikTokAccount(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario de TikTok es requerido.");
        }

        String cleanUsername = username.trim();
        if (cleanUsername.startsWith("@")) {
            cleanUsername = cleanUsername.substring(1);
        }

        if (cleanUsername.isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario de TikTok no puede estar vacío.");
        }

        String oembedUrl = "https://www.tiktok.com/oembed?url=https://www.tiktok.com/@" + cleanUsername;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oembedUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());

                String authorName = root.has("author_name") ? root.get("author_name").asText() : cleanUsername;
                String authorUrl = root.has("author_url") ? root.get("author_url").asText() : "https://www.tiktok.com/@" + cleanUsername;

                Map<String, Object> result = new HashMap<>();
                result.put("valid", true);
                result.put("username", "@" + cleanUsername);
                result.put("displayName", authorName);
                result.put("profileUrl", authorUrl);
                return result;
            } else {
                throw new IllegalArgumentException("La cuenta @" + cleanUsername + " no existe en TikTok. Ingresa un usuario real de TikTok.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al validar cuenta de TikTok con oEmbed: {}", e.getMessage());
            throw new IllegalArgumentException("No se pudo verificar la cuenta @" + cleanUsername + " con TikTok: " + e.getMessage());
        }
    }

    // --- Vinculación directa con verificación de cuenta (sin contraseñas) ---

    @Transactional
    public TikTokAccount linkAccount(Map<String, Object> body) {
        String sedeName = (String) body.get("sedeName");
        Long sedeId = body.get("sedeId") != null ? Long.valueOf(body.get("sedeId").toString()) : null;
        String rawUsername = (String) body.get("username");
        String displayName = (String) body.get("displayName");
        String accountId = (String) body.getOrDefault("accountId", "73" + System.currentTimeMillis());
        String avatarUrl = (String) body.get("avatarUrl");
        String accessToken = (String) body.get("accessToken");

        if (sedeName == null || sedeName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de la sede es requerido.");
        }
        if (rawUsername == null || rawUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre de usuario de TikTok es requerido.");
        }
        String cleanSede = sedeName.trim();

        // Validar obligatoriamente en vivo que la cuenta existe en los servidores de TikTok
        Map<String, Object> validation = validateTikTokAccount(rawUsername);
        String realUsername = (String) validation.get("username");
        String realDisplayName = (String) validation.get("displayName");

        TikTokAccount acc = accountRepository.findFirstBySedeNameIgnoreCase(cleanSede)
                .orElse(TikTokAccount.builder().sedeName(cleanSede).build());

        acc.setSedeId(sedeId);
        acc.setSedeName(cleanSede);
        acc.setUsername(realUsername);
        acc.setDisplayName(displayName != null && !displayName.trim().isEmpty() ? displayName : realDisplayName);
        acc.setAccountId(accountId);
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            acc.setAvatarUrl(avatarUrl);
        }
        acc.setStatus("CONNECTED");
        acc.setPermissions("Lectura de perfil y videos públicos");
        acc.setConnectedAt(LocalDateTime.now());
        if (accessToken != null && !accessToken.trim().isEmpty()) {
            acc.setAccessToken(accessToken.trim());
        }

        TikTokAccount saved = accountRepository.save(acc);

        // Si cuenta con token, sincronizar videos
        if (saved.getAccessToken() != null && !saved.getAccessToken().startsWith("act_mock")) {
            syncVideosFromTikTok(saved);
        }

        return saved;
    }

    @Transactional
    public void disconnectAccount(Long id) {
        accountRepository.findById(id).ifPresent(acc -> {
            acc.setStatus("DISCONNECTED");
            acc.setAccessToken(null);
            acc.setRefreshToken(null);
            accountRepository.save(acc);
        });
    }

    // --- Resumen General ---

    public Map<String, Object> getSummary(String sedeFilter, String period) {
        LocalDateTime from = calculatePeriodStart(period);
        LocalDateTime to = LocalDateTime.now();
        String safeSede = (sedeFilter != null && !sedeFilter.trim().isEmpty()) ? sedeFilter.trim() : "TODAS";

        boolean isLinked = false;
        if (!"TODAS".equalsIgnoreCase(safeSede)) {
            Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus(safeSede, "CONNECTED");
            isLinked = accOpt.isPresent();
        } else {
            isLinked = !accountRepository.findByStatus("CONNECTED").isEmpty();
        }

        List<TikTokVideo> videos = isLinked
                ? videoRepository.findFiltered(safeSede, from, to)
                : Collections.emptyList();

        long totalVideos = videos.size();
        long totalViews = videos.stream().mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L).sum();
        long totalLikes = videos.stream().mapToLong(v -> v.getLikeCount() != null ? v.getLikeCount() : 0L).sum();
        long totalComments = videos.stream().mapToLong(v -> v.getCommentCount() != null ? v.getCommentCount() : 0L).sum();
        long totalShares = videos.stream().mapToLong(v -> v.getShareCount() != null ? v.getShareCount() : 0L).sum();

        // Comparativo de visualizaciones por sede (para el gráfico de barras)
        List<Sede> sedes = sedeRepository.findAll();
        List<Map<String, Object>> viewsBySede = new ArrayList<>();
        for (Sede s : sedes) {
            String sName = s.getBarrio() != null && !s.getBarrio().trim().isEmpty() ? s.getBarrio().trim() : s.getName();
            long sViews = videos.stream()
                    .filter(v -> v.getSedeName() != null && v.getSedeName().equalsIgnoreCase(sName))
                    .mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L)
                    .sum();
            Map<String, Object> item = new HashMap<>();
            item.put("sede", sName);
            item.put("views", sViews);
            double pct = totalViews > 0 ? ((double) sViews / totalViews) * 100.0 : 0.0;
            item.put("percentage", Math.round(pct * 10.0) / 10.0);
            viewsBySede.add(item);
        }

        List<TikTokVideo> recent = videos.stream().limit(5).collect(Collectors.toList());

        Map<String, Object> summary = new HashMap<>();
        summary.put("isLinked", isLinked);
        summary.put("videosPublicados", totalVideos);
        summary.put("visualizacionesAcumuladas", totalViews);
        summary.put("meGusta", totalLikes);
        summary.put("comentarios", totalComments);
        summary.put("compartidos", totalShares);
        summary.put("viewsBySede", viewsBySede);
        summary.put("recentVideos", recent);

        return summary;
    }

    // --- Historial de Publicaciones ---

    public Map<String, Object> getHistory(String sede, String dateFrom, String dateTo, String search, String contentType, int page, int size) {
        LocalDateTime from = (dateFrom != null && !dateFrom.trim().isEmpty()) ? LocalDate.parse(dateFrom).atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime to = (dateTo != null && !dateTo.trim().isEmpty()) ? LocalDate.parse(dateTo).atTime(LocalTime.MAX) : LocalDateTime.of(2099, 12, 31, 23, 59, 59);
        String safeSede = (sede != null && !sede.trim().isEmpty()) ? sede.trim() : "TODAS";

        boolean isLinked = false;
        if (!"TODAS".equalsIgnoreCase(safeSede)) {
            Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus(safeSede, "CONNECTED");
            isLinked = accOpt.isPresent();
        } else {
            isLinked = !accountRepository.findByStatus("CONNECTED").isEmpty();
        }

        List<TikTokVideo> list = isLinked
                ? videoRepository.findFiltered(safeSede, from, to)
                : Collections.emptyList();

        if (contentType != null && !contentType.equalsIgnoreCase("TODOS") && !contentType.trim().isEmpty()) {
            list = list.stream()
                    .filter(v -> v.getContentType() != null && v.getContentType().equalsIgnoreCase(contentType))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.trim().isEmpty()) {
            String query = search.toLowerCase().trim();
            list = list.stream().filter(v ->
                    (v.getTitle() != null && v.getTitle().toLowerCase().contains(query)) ||
                    (v.getDescription() != null && v.getDescription().toLowerCase().contains(query)) ||
                    (v.getHashtags() != null && v.getHashtags().toLowerCase().contains(query))
            ).collect(Collectors.toList());
        }

        long totalVideos = list.size();
        long totalViews = list.stream().mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L).sum();
        long totalLikes = list.stream().mapToLong(v -> v.getLikeCount() != null ? v.getLikeCount() : 0L).sum();
        long totalComments = list.stream().mapToLong(v -> v.getCommentCount() != null ? v.getCommentCount() : 0L).sum();
        long totalShares = list.stream().mapToLong(v -> v.getShareCount() != null ? v.getShareCount() : 0L).sum();

        int start = Math.min(page * size, list.size());
        int end = Math.min(start + size, list.size());
        List<TikTokVideo> paginated = list.subList(start, end);

        Map<String, Object> res = new HashMap<>();
        res.put("isLinked", isLinked);
        res.put("totalVideos", totalVideos);
        res.put("totalViews", totalViews);
        res.put("totalLikes", totalLikes);
        res.put("totalComments", totalComments);
        res.put("totalShares", totalShares);
        res.put("page", page);
        res.put("size", size);
        res.put("totalPages", (int) Math.ceil((double) totalVideos / Math.max(1, size)));
        res.put("videos", paginated);

        return res;
    }

    // --- Programación Semanal ---

    public Map<String, Object> getWeeklySchedule(String sede, LocalDate weekStartDate) {
        LocalDate startMonday = weekStartDate != null
                ? weekStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endSunday = startMonday.plusDays(6);

        LocalDateTime startDateTime = startMonday.atStartOfDay();
        LocalDateTime endDateTime = endSunday.atTime(LocalTime.MAX);
        String safeSede = (sede != null && !sede.trim().isEmpty()) ? sede.trim() : "TODAS";

        boolean isLinked = false;
        String linkedUsername = null;
        String linkedDisplayName = null;
        Long linkedAccountId = null;

        if (!"TODAS".equalsIgnoreCase(safeSede)) {
            Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus(safeSede, "CONNECTED");
            if (accOpt.isPresent()) {
                TikTokAccount acc = accOpt.get();
                isLinked = true;
                linkedUsername = acc.getUsername();
                linkedDisplayName = acc.getDisplayName();
                linkedAccountId = acc.getId();
            }
        } else {
            List<TikTokAccount> connected = accountRepository.findByStatus("CONNECTED");
            isLinked = !connected.isEmpty();
        }

        List<TikTokScheduledVideo> scheduledList = isLinked
                ? scheduledVideoRepository.findBySedeAndDateRange(safeSede, startDateTime, endDateTime)
                : Collections.emptyList();

        List<Map<String, Object>> days = new ArrayList<>();
        String[] dayNames = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
        Set<LocalDate> daysWithContent = new HashSet<>();

        for (int i = 0; i < 7; i++) {
            LocalDate currentDay = startMonday.plusDays(i);
            List<TikTokScheduledVideo> dayVideos = scheduledList.stream()
                    .filter(s -> s.getScheduledAt() != null && s.getScheduledAt().toLocalDate().isEqual(currentDay))
                    .collect(Collectors.toList());

            if (!dayVideos.isEmpty()) {
                daysWithContent.add(currentDay);
            }

            Optional<TikTokDayNote> noteOpt = (isLinked && !"TODAS".equalsIgnoreCase(safeSede))
                    ? dayNoteRepository.findBySedeNameAndNoteDate(safeSede, currentDay)
                    : Optional.empty();

            Map<String, Object> dayMap = new HashMap<>();
            dayMap.put("dayName", dayNames[i]);
            dayMap.put("dayNumber", currentDay.getDayOfMonth());
            dayMap.put("date", currentDay.toString());
            dayMap.put("videos", dayVideos);
            dayMap.put("note", noteOpt.map(TikTokDayNote::getNote).orElse(""));
            days.add(dayMap);
        }

        int videosProgramados = scheduledList.size();
        int diasConContenido = daysWithContent.size();
        double promedioDiario = videosProgramados > 0 ? Math.round((videosProgramados / 7.0) * 10.0) / 10.0 : 0.0;
        int programacionActiva = diasConContenido > 0 ? (int) Math.round((diasConContenido / 7.0) * 100) : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("weekStart", startMonday.toString());
        result.put("weekEnd", endSunday.toString());
        result.put("sede", safeSede);
        result.put("isLinked", isLinked);
        result.put("linkedUsername", linkedUsername);
        result.put("linkedDisplayName", linkedDisplayName);
        result.put("linkedAccountId", linkedAccountId);
        result.put("videosProgramados", videosProgramados);
        result.put("diasConContenido", diasConContenido);
        result.put("promedioDiario", promedioDiario);
        result.put("programacionActiva", programacionActiva);
        result.put("days", days);

        // Indicador explícito de la limitación de la API oficial de TikTok
        result.put("apiLimitNotice", "La API oficial de TikTok no ofrece acceso para consultar borradores o programaciones creadas nativamente en TikTok Studio. Todas las programaciones administradas aquí se gestionan de forma segura en la base de datos.");
        result.put("tiktokStudioUrl", "https://www.tiktok.com/tiktokstudio");

        return result;
    }

    @Transactional
    public TikTokDayNote saveDayNote(String sedeName, LocalDate date, String note) {
        TikTokDayNote dayNote = dayNoteRepository.findBySedeNameAndNoteDate(sedeName, date)
                .orElse(TikTokDayNote.builder().sedeName(sedeName).noteDate(date).build());
        dayNote.setNote(note);
        return dayNoteRepository.save(dayNote);
    }

    @Transactional
    public TikTokScheduledVideo addScheduledVideo(TikTokScheduledVideo video) {
        if (video.getSedeName() == null || video.getSedeName().trim().isEmpty()) {
            throw new IllegalArgumentException("La sede es requerida para programar un video.");
        }
        if (video.getTitle() == null || video.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("El título del video es requerido.");
        }
        if (video.getScheduledAt() == null) {
            throw new IllegalArgumentException("La fecha y hora de programación es requerida.");
        }
        if (video.getStatus() == null || video.getStatus().trim().isEmpty()) {
            video.setStatus("PROGRAMADO");
        }
        if (video.getDuration() == null || video.getDuration().trim().isEmpty()) {
            video.setDuration("0:24");
        }
        return scheduledVideoRepository.save(video);
    }

    @Transactional
    public void deleteScheduledVideo(Long id) {
        scheduledVideoRepository.deleteById(id);
    }

    // Helper período
    private LocalDateTime calculatePeriodStart(String period) {
        if (period == null) return LocalDateTime.now().minusDays(7);
        switch (period.toUpperCase()) {
            case "ESTA_SEMANA":
                return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case "ESTE_MES":
                return LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
            case "ULTIMOS_30_DIAS":
                return LocalDateTime.now().minusDays(30);
            case "HISTORICO":
            case "TODO":
                return LocalDateTime.of(2020, 1, 1, 0, 0);
            default:
                return LocalDateTime.now().minusDays(7);
        }
    }
}
