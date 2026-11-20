package com.jdqstore.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jdqstore.backend.entity.*;
import com.jdqstore.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private final TikTokSettingsRepository settingsRepository;
    private final SedeRepository sedeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tiktok.client-key:}")
    private String defaultClientKey;

    @Value("${tiktok.client-secret:}")
    private String defaultClientSecret;

    @Value("${tiktok.redirect-uri:https://www.juandstore.com/admin/tiktok/callback}")
    private String defaultRedirectUri;

    private static final String UPLOAD_DIR = "uploads/tiktok_videos";

    // --- Resolución dinámica de credenciales (Base de datos o application.yml) ---

    public String getEffectiveClientKey() {
        Optional<TikTokSettings> opt = settingsRepository.findById(1L);
        if (opt.isPresent() && opt.get().getClientKey() != null && !opt.get().getClientKey().trim().isEmpty()) {
            return opt.get().getClientKey().trim();
        }
        return defaultClientKey != null ? defaultClientKey.trim() : "";
    }

    public String getEffectiveClientSecret() {
        Optional<TikTokSettings> opt = settingsRepository.findById(1L);
        if (opt.isPresent() && opt.get().getClientSecret() != null && !opt.get().getClientSecret().trim().isEmpty()) {
            return opt.get().getClientSecret().trim();
        }
        return defaultClientSecret != null ? defaultClientSecret.trim() : "";
    }

    public String getEffectiveRedirectUri() {
        Optional<TikTokSettings> opt = settingsRepository.findById(1L);
        if (opt.isPresent() && opt.get().getRedirectUri() != null && !opt.get().getRedirectUri().trim().isEmpty()) {
            String uri = opt.get().getRedirectUri().trim();
            if ("https://www.juandstore.com/admin/tiktok".equalsIgnoreCase(uri)) {
                return "https://www.juandstore.com/admin/tiktok/callback";
            }
            return uri;
        }
        return defaultRedirectUri != null ? defaultRedirectUri.trim() : "https://www.juandstore.com/admin/tiktok/callback";
    }

    public Map<String, Object> getOAuthConfig() {
        String clientKey = getEffectiveClientKey();
        String clientSecret = getEffectiveClientSecret();
        String redirectUri = getEffectiveRedirectUri();
        boolean configured = !clientKey.isEmpty() && !clientSecret.isEmpty();

        Map<String, Object> res = new HashMap<>();
        res.put("configured", configured);
        res.put("clientKey", !clientKey.isEmpty() ? clientKey : null);
        res.put("redirectUri", redirectUri);
        res.put("scopes", List.of("user.info.basic", "video.list", "video.publish", "video.upload"));
        res.put("requiredCredentials", Map.of(
                "TIKTOK_CLIENT_KEY", "Client Key (App ID) de tu app en developers.tiktok.com",
                "TIKTOK_CLIENT_SECRET", "Client Secret de tu app en developers.tiktok.com",
                "TIKTOK_REDIRECT_URI", redirectUri + " (Registrada en Redirect URIs de tu app)"
        ));
        return res;
    }

    @Transactional
    public Map<String, Object> saveOAuthConfig(Map<String, String> body) {
        String clientKey = body.get("clientKey");
        String clientSecret = body.get("clientSecret");
        String redirectUri = body.get("redirectUri");

        TikTokSettings settings = settingsRepository.findById(1L).orElse(TikTokSettings.builder().id(1L).build());
        if (clientKey != null) settings.setClientKey(clientKey.trim());
        if (clientSecret != null) settings.setClientSecret(clientSecret.trim());
        if (redirectUri != null && !redirectUri.trim().isEmpty()) settings.setRedirectUri(redirectUri.trim());

        settingsRepository.save(settings);
        log.info("Configuración de credenciales de TikTok actualizada exitosamente en base de datos.");
        return getOAuthConfig();
    }

    // --- Generación de URL OAuth 2.0 Oficial ---

    public String getAuthorizationUrl(String sedeName) {
        return getAuthorizationUrl(sedeName, null);
    }

    public String getAuthorizationUrl(String sedeName, String customScope) {
        String clientKey = getEffectiveClientKey();
        String redirectUri = getEffectiveRedirectUri();

        if (clientKey.isEmpty()) {
            throw new IllegalStateException("TIKTOK_CLIENT_KEY no está configurado. Configura tus credenciales de TikTok Developers.");
        }

        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String state = "GLOBAL";
        // Ahora que el Sandbox tiene Content Posting API activado y configurado, solicitamos todos los permisos de video
        String scopes = (customScope != null && !customScope.trim().isEmpty())
                ? customScope.trim()
                : "user.info.basic,video.list,video.publish,video.upload";

        return "https://www.tiktok.com/v2/auth/authorize/?"
                + "client_key=" + clientKey
                + "&response_type=code"
                + "&scope=" + scopes
                + "&redirect_uri=" + encodedRedirect
                + "&state=" + state;
    }

    // --- Intercambio de Código OAuth 2.0 por Token ---

    @Transactional
    public TikTokAccount exchangeOAuthCode(String code, String sedeName) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("El código de autorización de TikTok es requerido.");
        }

        String clientKey = getEffectiveClientKey();
        String clientSecret = getEffectiveClientSecret();
        String redirectUri = getEffectiveRedirectUri();

        if (clientKey.isEmpty() || clientSecret.isEmpty()) {
            throw new IllegalStateException("Las credenciales TIKTOK_CLIENT_KEY o TIKTOK_CLIENT_SECRET no están configuradas.");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            // 1. Solicitar tokens a TikTok
            String formBody = "client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&code=" + URLEncoder.encode(code.trim(), StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/oauth/token/"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cache-Control", "no-cache")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

            if (tokenResponse.statusCode() != 200) {
                log.error("Error al obtener token de TikTok. HTTP {}: {}", tokenResponse.statusCode(), tokenResponse.body());
                throw new IllegalArgumentException("TikTok rechazó la autorización (HTTP " + tokenResponse.statusCode() + "): " + tokenResponse.body());
            }

            log.info("TikTok OAuth token raw response HTTP {}: {}", tokenResponse.statusCode(), tokenResponse.body());

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
            if (tokenJson.has("error") && !tokenJson.get("error").isMissingNode() && !tokenJson.get("error").isNull()) {
                JsonNode errNode = tokenJson.get("error");
                String errorStr = errNode.isTextual() ? errNode.asText() : errNode.path("code").asText("");
                if (!errorStr.isEmpty() && !"ok".equalsIgnoreCase(errorStr)) {
                    String desc = tokenJson.has("error_description")
                            ? tokenJson.path("error_description").asText()
                            : errNode.path("message").asText(errorStr);
                    log.error("TikTok OAuth token exchange error: {} - {}", errorStr, desc);
                    throw new IllegalArgumentException("TikTok rechazó la autorización: " + desc + " (" + errorStr + ")");
                }
            }

            JsonNode dataNode = (tokenJson.has("data") && !tokenJson.get("data").isNull() && !tokenJson.get("data").isMissingNode())
                    ? tokenJson.get("data")
                    : tokenJson;

            String accessToken = dataNode.path("access_token").asText("");
            if (accessToken.isEmpty()) {
                accessToken = tokenJson.path("access_token").asText("");
            }

            if (accessToken == null || accessToken.trim().isEmpty()) {
                log.error("TikTok token response no contiene access_token: {}", tokenResponse.body());
                throw new IllegalStateException("TikTok no devolvió el access_token oficial. Respuesta: " + tokenResponse.body());
            }

            String refreshToken = dataNode.path("refresh_token").asText("");
            if (refreshToken.isEmpty()) {
                refreshToken = tokenJson.path("refresh_token").asText("");
            }

            long expiresIn = dataNode.path("expires_in").asLong(tokenJson.path("expires_in").asLong(86400L));
            long refreshExpiresIn = dataNode.path("refresh_expires_in").asLong(tokenJson.path("refresh_expires_in").asLong(31536000L));

            String openId = dataNode.path("open_id").asText("");
            if (openId.isEmpty()) {
                openId = tokenJson.path("open_id").asText("");
            }

            String scope = dataNode.path("scope").asText("");
            if (scope.isEmpty()) {
                scope = tokenJson.path("scope").asText("user.info.basic,video.list,video.publish,video.upload");
            }

            // 2. Consultar perfil con el access token
            HttpRequest userRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/user/info/?fields=open_id,union_id,avatar_url,avatar_url_100,display_name,username"))
                    .header("Authorization", "Bearer " + accessToken)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> userResponse = client.send(userRequest, HttpResponse.BodyHandlers.ofString());
            String username = "@juandstore";
            String displayName = "JUANDSTORE";
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
                    if (avatarUrl == null || avatarUrl.isEmpty()) {
                        avatarUrl = userData.path("avatar_url_100").asText(null);
                    }
                }
            } else {
                log.warn("TikTok User Info devolvió HTTP {}: {}", userResponse.statusCode(), userResponse.body());
            }

            // 3. Crear o actualizar cuenta global
            TikTokAccount acc = accountRepository.findFirstBySedeNameIgnoreCase("GLOBAL")
                    .orElse(accountRepository.findByStatus("CONNECTED").stream().findFirst()
                            .orElse(TikTokAccount.builder().sedeName("GLOBAL").build()));

            acc.setSedeName("GLOBAL");
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
            acc.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(refreshExpiresIn));
            acc.setStatus("CONNECTED");

            // Formatear permisos amigables basados en los scopes reales
            List<String> permList = new ArrayList<>();
            permList.add("Perfil (user.info.basic)");
            if (scope.contains("video.publish")) permList.add("Publicación directa (video.publish)");
            if (scope.contains("video.upload")) permList.add("Carga de borradores (video.upload)");
            if (scope.contains("video.list")) permList.add("Lectura de videos (video.list)");
            acc.setPermissions(String.join(", ", permList));
            acc.setConnectedAt(LocalDateTime.now());

            TikTokAccount saved = accountRepository.save(acc);
            log.info("Cuenta de TikTok {} vinculada exitosamente con scopes: {}", username, scope);

            // 4. Sincronizar videos reales de la cuenta
            syncVideosFromTikTok(saved);

            return saved;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excepción al intercambiar código de TikTok OAuth: {}", e.getMessage(), e);
            throw new RuntimeException("Error durante la vinculación OAuth 2.0 con TikTok: " + e.getMessage(), e);
        }
    }

    // --- Token Refresh Automático ---

    public String ensureValidAccessToken(TikTokAccount account) {
        if (account == null || account.getAccessToken() == null) {
            throw new IllegalStateException("No hay ninguna cuenta de TikTok vinculada.");
        }

        // Si faltan más de 5 minutos para que expire, el token sigue válido
        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return account.getAccessToken() != null ? account.getAccessToken().trim() : "";
        }

        // Si expiró y tenemos refresh_token, renovarlo
        if (account.getRefreshToken() != null && !account.getRefreshToken().isEmpty()) {
            try {
                String clientKey = getEffectiveClientKey();
                String clientSecret = getEffectiveClientSecret();

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                String formBody = "client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                        + "&grant_type=refresh_token"
                        + "&refresh_token=" + URLEncoder.encode(account.getRefreshToken(), StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://open.tiktokapis.com/v2/oauth/token/"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    JsonNode data = (json.has("data") && !json.get("data").isNull() && !json.get("data").isMissingNode())
                            ? json.get("data")
                            : json;
                    String newAccess = data.path("access_token").asText(json.path("access_token").asText(""));
                    String newRefresh = data.path("refresh_token").asText(json.path("refresh_token").asText(""));
                    long expiresIn = data.path("expires_in").asLong(json.path("expires_in").asLong(86400L));

                    account.setAccessToken(newAccess);
                    if (!newRefresh.isEmpty()) account.setRefreshToken(newRefresh);
                    account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                    accountRepository.save(account);
                    log.info("Token de TikTok renovado automáticamente para {}", account.getUsername());
                    return newAccess;
                } else {
                    log.warn("Fallo al refrescar token de TikTok HTTP {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Error al refrescar token de TikTok: {}", e.getMessage());
            }
        }

        return account.getAccessToken() != null ? account.getAccessToken().trim() : "";
    }

    // --- Cuenta Global (Administrador Global) ---

    public Map<String, Object> getGlobalAccount() {
        Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus("GLOBAL", "CONNECTED");
        if (!accOpt.isPresent()) {
            accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        }

        Map<String, Object> res = new HashMap<>();
        if (accOpt.isPresent()) {
            TikTokAccount acc = accOpt.get();
            String scope = acc.getScope() != null ? acc.getScope() : "";

            res.put("connected", true);
            res.put("id", acc.getId());
            res.put("username", acc.getUsername());
            res.put("displayName", acc.getDisplayName());
            res.put("avatarUrl", acc.getAvatarUrl());
            res.put("accountId", acc.getAccountId());
            res.put("openId", acc.getOpenId());
            res.put("status", "Conectada");
            res.put("connectedAt", acc.getConnectedAt() != null ? acc.getConnectedAt().toString() : LocalDateTime.now().toString());
            res.put("permissions", acc.getPermissions() != null ? acc.getPermissions() : "Perfil, carga y publicación de videos");
            res.put("scope", scope);
            res.put("canPublishDirectly", scope.contains("video.publish"));
            res.put("canUploadDraft", scope.contains("video.upload"));
            res.put("canListVideos", scope.contains("video.list"));
            res.put("tokenExpiresAt", acc.getTokenExpiresAt() != null ? acc.getTokenExpiresAt().toString() : null);
        } else {
            res.put("connected", false);
            res.put("status", "Sin conectar");
            res.put("username", null);
            res.put("displayName", null);
            res.put("avatarUrl", null);
            res.put("canPublishDirectly", false);
            res.put("canUploadDraft", false);
        }
        return res;
    }

    @Transactional
    public void disconnectGlobalAccount() {
        List<TikTokAccount> accounts = accountRepository.findByStatus("CONNECTED");
        for (TikTokAccount acc : accounts) {
            acc.setStatus("DISCONNECTED");
            acc.setAccessToken(null);
            acc.setRefreshToken(null);
            accountRepository.save(acc);
        }
        log.info("Cuenta global de TikTok desconectada.");
    }

    // --- Publicar Video Real (Content Posting API v2) ---

    @Transactional
    public Map<String, Object> publishVideo(
            MultipartFile videoFile,
            String title,
            String hashtags,
            String privacyLevel,
            Boolean allowComments,
            Boolean allowDuet,
            Boolean allowStitch,
            Long coverTimestampMs,
            Boolean isDraft
    ) {
        TikTokAccount account = getConnectedGlobalAccount();
        String accessToken = ensureValidAccessToken(account) != null ? ensureValidAccessToken(account).trim() : "";

        if (accessToken.isEmpty()) {
            throw new IllegalStateException("El token de autorización de TikTok está vacío. Por favor desconecta la cuenta en 'Mi cuenta' y vuelve a presionar 'Conectar con TikTok' para renovar las credenciales.");
        }

        if (videoFile == null || videoFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo de video es obligatorio para publicar.");
        }

        // 1. Guardar archivo localmente
        Path savedPath = saveVideoFileLocally(videoFile);
        File fileOnDisk = savedPath.toFile();
        long fileSize = fileOnDisk.length();

        if (fileSize <= 0) {
            throw new IllegalArgumentException("El archivo de video está vacío.");
        }

        String safePrivacy = (privacyLevel != null && !privacyLevel.trim().isEmpty()) ? privacyLevel.trim() : "PUBLIC_TO_EVERYONE";
        boolean safeComments = allowComments != null ? allowComments : true;
        boolean safeDuet = allowDuet != null ? allowDuet : true;
        boolean safeStitch = allowStitch != null ? allowStitch : true;
        boolean safeDraft = isDraft != null ? isDraft : false;
        long safeCoverMs = coverTimestampMs != null ? coverTimestampMs : 1000L;

        String fullCaption = (title != null ? title.trim() : "");
        if (hashtags != null && !hashtags.trim().isEmpty()) {
            fullCaption = fullCaption + " " + hashtags.trim();
        }
        if (fullCaption.length() > 2200) {
            fullCaption = fullCaption.substring(0, 2197) + "...";
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();

            String publishId = null;
            String uploadUrl = null;

            if (safeDraft) {
                // Modo Borrador: Inbox Video Init
                log.info("Iniciando subida de borrador a TikTok Inbox...");
                ObjectNode initBody = objectMapper.createObjectNode();
                ObjectNode sourceInfo = initBody.putObject("source_info");
                sourceInfo.put("source", "FILE_UPLOAD");
                sourceInfo.put("video_size", fileSize);
                sourceInfo.put("chunk_size", fileSize);
                sourceInfo.put("total_chunk_count", 1);

                HttpRequest initReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(initBody)))
                        .build();

                HttpResponse<String> initRes = client.send(initReq, HttpResponse.BodyHandlers.ofString());
                JsonNode initJson = objectMapper.readTree(initRes.body());

                validateTikTokApiResponse(initRes.statusCode(), initJson, "video.upload (Borrador)");

                publishId = initJson.path("data").path("publish_id").asText();
                uploadUrl = initJson.path("data").path("upload_url").asText();
            } else {
                // Modo Publicación Directa: Video Publish Init
                log.info("Iniciando publicación directa en TikTok Content Posting API...");
                ObjectNode initBody = objectMapper.createObjectNode();

                ObjectNode postInfo = initBody.putObject("post_info");
                postInfo.put("title", fullCaption);
                postInfo.put("privacy_level", safePrivacy);
                postInfo.put("disable_duet", !safeDuet);
                postInfo.put("disable_comment", !safeComments);
                postInfo.put("disable_stitch", !safeStitch);
                postInfo.put("video_cover_timestamp_ms", safeCoverMs);

                ObjectNode sourceInfo = initBody.putObject("source_info");
                sourceInfo.put("source", "FILE_UPLOAD");
                sourceInfo.put("video_size", fileSize);
                sourceInfo.put("chunk_size", fileSize);
                sourceInfo.put("total_chunk_count", 1);

                HttpRequest initReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/video/init/"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(initBody)))
                        .build();

                HttpResponse<String> initRes = client.send(initReq, HttpResponse.BodyHandlers.ofString());
                JsonNode initJson = objectMapper.readTree(initRes.body());

                validateTikTokApiResponse(initRes.statusCode(), initJson, "video.publish (Publicación Directa)");

                publishId = initJson.path("data").path("publish_id").asText();
                uploadUrl = initJson.path("data").path("upload_url").asText();
            }

            if (uploadUrl == null || uploadUrl.isEmpty()) {
                throw new IllegalStateException("TikTok no proporcionó una URL de subida (upload_url).");
            }

            // 2. Subir binario del video a upload_url con PUT y Content-Range
            log.info("Subiendo archivo de video ({} bytes) al servidor de TikTok...", fileSize);
            HttpRequest uploadReq = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "video/mp4")
                    .header("Content-Range", "bytes 0-" + (fileSize - 1) + "/" + fileSize)
                    .timeout(Duration.ofMinutes(5))
                    .PUT(HttpRequest.BodyPublishers.ofFile(savedPath))
                    .build();

            HttpResponse<String> uploadRes = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
            if (uploadRes.statusCode() != 200 && uploadRes.statusCode() != 201 && uploadRes.statusCode() != 204) {
                log.error("Fallo al subir video a upload_url de TikTok: HTTP {} - {}", uploadRes.statusCode(), uploadRes.body());
                throw new IllegalArgumentException("Error al transmitir el video a TikTok (HTTP " + uploadRes.statusCode() + "): " + uploadRes.body());
            }

            log.info("Video subido exitosamente a TikTok. Publish ID: {}", publishId);

            // 3. Consultar estado inmediato
            String finalStatus = "PROCESSING_UPLOAD";
            try {
                Thread.sleep(1500); // Breve espera para que TikTok registre el estado
                HttpRequest statusReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/status/fetch/"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"publish_id\": \"" + publishId + "\"}"))
                        .build();

                HttpResponse<String> statusRes = client.send(statusReq, HttpResponse.BodyHandlers.ofString());
                if (statusRes.statusCode() == 200) {
                    JsonNode sJson = objectMapper.readTree(statusRes.body());
                    String statusText = sJson.path("data").path("status").asText();
                    if (!statusText.isEmpty()) {
                        finalStatus = statusText;
                    }
                }
            } catch (Exception ex) {
                log.warn("No se pudo verificar el estado preliminar del publish_id {}: {}", publishId, ex.getMessage());
            }

            // 4. Registrar en la base de datos de publicaciones
            TikTokVideo newVid = TikTokVideo.builder()
                    .accountId(account.getId())
                    .sedeName("GLOBAL")
                    .videoId(publishId)
                    .title(title != null && !title.isEmpty() ? title : "Publicación en TikTok")
                    .description(fullCaption)
                    .hashtags(hashtags)
                    .videoUrl(null)
                    .duration("0:30")
                    .publishedAt(LocalDateTime.now())
                    .viewCount(0L)
                    .likeCount(0L)
                    .commentCount(0L)
                    .shareCount(0L)
                    .contentType("Video")
                    .build();
            videoRepository.save(newVid);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("publishId", publishId);
            result.put("status", finalStatus);
            result.put("isDraft", safeDraft);
            result.put("message", safeDraft
                    ? "Borrador enviado exitosamente a tu cuenta de TikTok. Abre la app de TikTok para finalizar su publicación."
                    : "¡Video enviado exitosamente a TikTok mediante Content Posting API oficial!");
            return result;

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excepción al publicar video en TikTok: {}", e.getMessage(), e);
            throw new RuntimeException("Error en la integración de TikTok: " + e.getMessage(), e);
        }
    }

    // --- Programar Video para Publicación Semanal ---

    @Transactional
    public TikTokScheduledVideo scheduleVideo(
            MultipartFile videoFile,
            String title,
            String hashtags,
            String privacyLevel,
            Boolean allowComments,
            Boolean allowDuet,
            Boolean allowStitch,
            Long coverTimestampMs,
            LocalDateTime scheduledAt,
            Boolean isDraft
    ) {
        TikTokAccount account = getConnectedGlobalAccount();

        if (videoFile == null || videoFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo de video es obligatorio para programar.");
        }
        if (scheduledAt == null) {
            throw new IllegalArgumentException("La fecha y hora de programación es requerida.");
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La fecha y hora programada debe ser posterior al momento actual.");
        }

        Path savedPath = saveVideoFileLocally(videoFile);

        TikTokScheduledVideo sched = TikTokScheduledVideo.builder()
                .accountId(account.getId())
                .sedeName("GLOBAL")
                .title(title != null && !title.trim().isEmpty() ? title.trim() : "Video programado")
                .description(title)
                .hashtags(hashtags)
                .videoFilePath(savedPath.toAbsolutePath().toString())
                .scheduledAt(scheduledAt)
                .privacyLevel(privacyLevel != null ? privacyLevel : "PUBLIC_TO_EVERYONE")
                .allowComments(allowComments != null ? allowComments : true)
                .allowDuet(allowDuet != null ? allowDuet : true)
                .allowStitch(allowStitch != null ? allowStitch : true)
                .isDraft(isDraft != null ? isDraft : false)
                .duration("0:30")
                .status("PROGRAMADO")
                .build();

        TikTokScheduledVideo saved = scheduledVideoRepository.save(sched);
        log.info("Video programado exitosamente (ID: {}) para {}", saved.getId(), scheduledAt);
        return saved;
    }

    // --- Tarea Automática de Publicación Programada (@Scheduled cada minuto) ---

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processScheduledVideos() {
        LocalDateTime now = LocalDateTime.now();
        List<TikTokScheduledVideo> pending = scheduledVideoRepository.findPendingToPublish(now);
        if (pending.isEmpty()) return;

        log.info("Ejecutando verificador de videos programados de TikTok: {} videos pendientes.", pending.size());

        Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus("GLOBAL", "CONNECTED");
        if (!accOpt.isPresent()) {
            accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        }

        if (!accOpt.isPresent()) {
            log.warn("Hay videos programados pero no hay ninguna cuenta de TikTok conectada.");
            for (TikTokScheduledVideo s : pending) {
                s.setStatus("ERROR");
                s.setErrorMessage("No hay ninguna cuenta de TikTok conectada en el momento de la publicación.");
                scheduledVideoRepository.save(s);
            }
            return;
        }

        TikTokAccount account = accOpt.get();
        String accessToken = ensureValidAccessToken(account);

        for (TikTokScheduledVideo s : pending) {
            try {
                if (s.getVideoFilePath() == null || !Files.exists(Paths.get(s.getVideoFilePath()))) {
                    s.setStatus("ERROR");
                    s.setErrorMessage("El archivo de video local no fue encontrado en el servidor.");
                    scheduledVideoRepository.save(s);
                    continue;
                }

                Path filePath = Paths.get(s.getVideoFilePath());
                long fileSize = Files.size(filePath);

                String fullCaption = (s.getTitle() != null ? s.getTitle() : "");
                if (s.getHashtags() != null && !s.getHashtags().isEmpty()) {
                    fullCaption = fullCaption + " " + s.getHashtags();
                }

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

                // 1. Init
                ObjectNode initBody = objectMapper.createObjectNode();
                ObjectNode postInfo = initBody.putObject("post_info");
                postInfo.put("title", fullCaption);
                postInfo.put("privacy_level", s.getPrivacyLevel() != null ? s.getPrivacyLevel() : "PUBLIC_TO_EVERYONE");
                postInfo.put("disable_duet", s.getAllowDuet() != null && !s.getAllowDuet());
                postInfo.put("disable_comment", s.getAllowComments() != null && !s.getAllowComments());
                postInfo.put("disable_stitch", s.getAllowStitch() != null && !s.getAllowStitch());
                postInfo.put("video_cover_timestamp_ms", 1000L);

                ObjectNode sourceInfo = initBody.putObject("source_info");
                sourceInfo.put("source", "FILE_UPLOAD");
                sourceInfo.put("video_size", fileSize);
                sourceInfo.put("chunk_size", fileSize);
                sourceInfo.put("total_chunk_count", 1);

                HttpRequest initReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/video/init/"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(initBody)))
                        .build();

                HttpResponse<String> initRes = client.send(initReq, HttpResponse.BodyHandlers.ofString());
                JsonNode initJson = objectMapper.readTree(initRes.body());

                validateTikTokApiResponse(initRes.statusCode(), initJson, "video.publish (Programado)");

                String publishId = initJson.path("data").path("publish_id").asText();
                String uploadUrl = initJson.path("data").path("upload_url").asText();

                // 2. Upload
                HttpRequest uploadReq = HttpRequest.newBuilder()
                        .uri(URI.create(uploadUrl))
                        .header("Content-Type", "video/mp4")
                        .header("Content-Range", "bytes 0-" + (fileSize - 1) + "/" + fileSize)
                        .timeout(Duration.ofMinutes(5))
                        .PUT(HttpRequest.BodyPublishers.ofFile(filePath))
                        .build();

                HttpResponse<String> uploadRes = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
                if (uploadRes.statusCode() >= 200 && uploadRes.statusCode() < 300) {
                    s.setStatus("PUBLICADO");
                    s.setTiktokPublishId(publishId);
                    s.setPublishedAt(LocalDateTime.now());
                    s.setErrorMessage(null);
                    scheduledVideoRepository.save(s);

                    // Registrar en historial
                    TikTokVideo newVid = TikTokVideo.builder()
                            .accountId(account.getId())
                            .sedeName("GLOBAL")
                            .videoId(publishId)
                            .title(s.getTitle())
                            .description(fullCaption)
                            .hashtags(s.getHashtags())
                            .duration(s.getDuration() != null ? s.getDuration() : "0:30")
                            .publishedAt(LocalDateTime.now())
                            .viewCount(0L)
                            .likeCount(0L)
                            .commentCount(0L)
                            .shareCount(0L)
                            .contentType("Video")
                            .build();
                    videoRepository.save(newVid);
                    log.info("Video programado ID {} publicado exitosamente en TikTok con publish_id: {}", s.getId(), publishId);
                } else {
                    s.setStatus("ERROR");
                    s.setErrorMessage("Error al subir binario a TikTok: HTTP " + uploadRes.statusCode());
                    scheduledVideoRepository.save(s);
                }
            } catch (Exception ex) {
                log.error("Fallo al publicar video programado ID {}: {}", s.getId(), ex.getMessage());
                s.setStatus("ERROR");
                s.setErrorMessage(ex.getMessage());
                scheduledVideoRepository.save(s);
            }
        }
    }

    // --- Helper de validación de respuestas de TikTok ---

    private void validateTikTokApiResponse(int httpCode, JsonNode json, String scopeNeeded) {
        JsonNode errNode = json.path("error");
        String codeStr = "";
        String errMsg = "";
        if (!errNode.isMissingNode() && errNode.has("code")) {
            codeStr = errNode.path("code").asText("");
            errMsg = errNode.path("message").asText("");
        }

        if ("unaudited_client_can_only_post_to_private_accounts".equalsIgnoreCase(codeStr)
                || (json != null && json.toString().contains("unaudited_client_can_only_post_to_private_accounts"))) {
            throw new IllegalArgumentException("unaudited_client_can_only_post_to_private_accounts: "
                    + "TikTok prohíbe que aplicaciones en modo Sandbox (sin auditar) publiquen en cuentas públicas. "
                    + "Para publicar en Sandbox: 1) Activa 'Cuenta privada' en tu app de TikTok (@juandstore) en tu celular (Ajustes y privacidad > Privacidad > Cuenta privada). "
                    + "2) Selecciona 'Solo yo' en Privacidad del video o pulsa 'Guardar borrador'.");
        }

        if (httpCode != 200) {
            if ("scope_not_authorized".equalsIgnoreCase(codeStr)) {
                throw new IllegalArgumentException("TikTok rechazó la publicación (error: scope_not_authorized). "
                        + "Tu aplicación en developers.tiktok.com aún no tiene aprobado el permiso oficial '" + scopeNeeded + "'. "
                        + "Mientras esté en modo desarrollo, agrega la cuenta como Administrador/Tester en la consola de TikTok Developers.");
            }
            if ("unaudited_client_can_only_post_to_admin_users".equalsIgnoreCase(codeStr)) {
                throw new IllegalArgumentException("TikTok rechazó la publicación: Tu aplicación no está auditada comercialmente y solo puede publicar a cuentas asociadas como testers o administradores.");
            }
            if ("access_token_invalid".equalsIgnoreCase(codeStr)) {
                throw new IllegalArgumentException("El token de TikTok expiró o fue revocado. Desconecta y vuelve a conectar tu cuenta.");
            }
            throw new IllegalArgumentException("TikTok rechazó la petición (HTTP " + httpCode + "): " + json.toString());
        }

        if (!codeStr.isEmpty() && !"ok".equalsIgnoreCase(codeStr)) {
            if ("scope_not_authorized".equalsIgnoreCase(codeStr)) {
                throw new IllegalArgumentException("TikTok rechazó la publicación (error: scope_not_authorized). "
                        + "Tu aplicación en developers.tiktok.com aún no tiene aprobado el permiso oficial '" + scopeNeeded + "'. "
                        + "Mientras esté en modo desarrollo, agrega la cuenta como Administrador/Tester en la consola de TikTok Developers.");
            }
            if ("unaudited_client_can_only_post_to_admin_users".equalsIgnoreCase(codeStr)) {
                throw new IllegalArgumentException("TikTok rechazó la publicación: Tu aplicación no está auditada comercialmente y solo puede publicar a cuentas asociadas como testers o administradores.");
            }
            if ("access_token_invalid".equalsIgnoreCase(codeStr)) {
                throw new IllegalArgumentException("El token de TikTok expiró o fue revocado. Desconecta y vuelve a conectar tu cuenta.");
            }

            throw new IllegalArgumentException("TikTok rechazó la solicitud (código " + codeStr + "): " + (errMsg.isEmpty() ? json.toString() : errMsg));
        }
    }

    private TikTokAccount getConnectedGlobalAccount() {
        Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus("GLOBAL", "CONNECTED");
        if (!accOpt.isPresent()) {
            accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        }
        if (!accOpt.isPresent()) {
            throw new IllegalStateException("No hay ninguna cuenta de TikTok conectada en JUANDSTORE. Ve a 'Mi cuenta' y conecta tu cuenta con TikTok Login Kit.");
        }
        return accOpt.get();
    }

    private Path saveVideoFileLocally(MultipartFile videoFile) {
        try {
            Path dir = Paths.get(UPLOAD_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String original = videoFile.getOriginalFilename() != null ? videoFile.getOriginalFilename() : "video.mp4";
            String cleanName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
            String filename = UUID.randomUUID().toString() + "_" + cleanName;
            Path target = dir.resolve(filename);

            Files.copy(videoFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            log.error("Error al guardar archivo de video localmente: {}", e.getMessage());
            throw new RuntimeException("No se pudo almacenar el archivo de video en el servidor: " + e.getMessage(), e);
        }
    }

    // --- Sincronizar videos reales de TikTok mediante API Oficial ---

    @Transactional
    public int syncVideosFromTikTok(TikTokAccount account) {
        if (account == null || account.getAccessToken() == null || !"CONNECTED".equalsIgnoreCase(account.getStatus())) {
            return 0;
        }

        try {
            String accessToken = ensureValidAccessToken(account);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            String postBody = "{\"max_count\": 20}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/video/list/?fields=id,title,video_description,duration,cover_image_url,create_time,share_url,like_count,comment_count,share_count,view_count"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
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
                        vid.setSedeName("GLOBAL");
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
                    log.info("Sincronizados {} videos oficiales de TikTok.", count);
                    return count;
                }
            } else {
                log.warn("TikTok Video List devolvió HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("No se pudieron sincronizar videos de TikTok: {}", e.getMessage());
        }
        return 0;
    }

    // --- Cuentas (Retrocompatibilidad) ---

    public List<Map<String, Object>> getAccountsStatus() {
        Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus("GLOBAL", "CONNECTED");
        if (!accOpt.isPresent()) {
            accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> map = new HashMap<>();
        map.put("sedeId", 1L);
        map.put("sedeName", "GLOBAL");
        map.put("storeName", "JUANDSTORE");

        if (accOpt.isPresent()) {
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
            map.put("status", "Sin conectar");
            map.put("username", null);
            map.put("displayName", null);
            map.put("avatarUrl", null);
        }
        result.add(map);
        return result;
    }

    // --- Resumen General ---

    public Map<String, Object> getSummary(String sedeFilter, String period) {
        LocalDateTime from = calculatePeriodStart(period);
        LocalDateTime to = LocalDateTime.now();

        Optional<TikTokAccount> accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        boolean isLinked = accOpt.isPresent();
        if (isLinked && videoRepository.count() == 0) {
            try {
                syncVideosFromTikTok(accOpt.get());
            } catch (Exception e) {
                log.warn("Auto-sync de videos en getSummary omitido: {}", e.getMessage());
            }
        }

        List<TikTokVideo> videos = videoRepository.findAllByOrderByPublishedAtDesc();

        long totalVideos = videos.size();
        long totalViews = videos.stream().mapToLong(v -> v.getViewCount() != null ? v.getViewCount() : 0L).sum();
        long totalLikes = videos.stream().mapToLong(v -> v.getLikeCount() != null ? v.getLikeCount() : 0L).sum();
        long totalComments = videos.stream().mapToLong(v -> v.getCommentCount() != null ? v.getCommentCount() : 0L).sum();
        long totalShares = videos.stream().mapToLong(v -> v.getShareCount() != null ? v.getShareCount() : 0L).sum();

        List<TikTokVideo> recent = videos.stream().limit(5).collect(Collectors.toList());

        Map<String, Object> summary = new HashMap<>();
        summary.put("isLinked", isLinked);
        summary.put("videosPublicados", totalVideos);
        summary.put("visualizacionesAcumuladas", totalViews);
        summary.put("meGusta", totalLikes);
        summary.put("comentarios", totalComments);
        summary.put("compartidos", totalShares);
        summary.put("recentVideos", recent);

        return summary;
    }

    // --- Historial de Publicaciones ---

    public Map<String, Object> getHistory(String sede, String dateFrom, String dateTo, String search, String contentType, int page, int size) {
        Optional<TikTokAccount> accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        boolean isLinked = accOpt.isPresent();
        if (isLinked && videoRepository.count() == 0) {
            try {
                syncVideosFromTikTok(accOpt.get());
            } catch (Exception e) {
                log.warn("Auto-sync de videos en getHistory omitido: {}", e.getMessage());
            }
        }

        List<TikTokVideo> list = videoRepository.findAllByOrderByPublishedAtDesc();

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

    @Transactional
    public Map<String, Object> syncVideosNow() {
        Optional<TikTokAccount> accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        if (!accOpt.isPresent()) {
            return Map.of("success", false, "count", 0, "message", "No hay ninguna cuenta de TikTok conectada actualmente.");
        }
        int count = syncVideosFromTikTok(accOpt.get());
        return Map.of("success", true, "count", count, "message", "Se sincronizaron " + count + " videos oficiales de TikTok.");
    }

    // --- Programación Semanal ---

    public Map<String, Object> getWeeklySchedule(String sede, LocalDate weekStartDate) {
        LocalDate startMonday = weekStartDate != null
                ? weekStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endSunday = startMonday.plusDays(6);

        LocalDateTime startDateTime = startMonday.atStartOfDay();
        LocalDateTime endDateTime = endSunday.atTime(LocalTime.MAX);

        Optional<TikTokAccount> accOpt = accountRepository.findFirstBySedeNameIgnoreCaseAndStatus("GLOBAL", "CONNECTED");
        if (!accOpt.isPresent()) {
            accOpt = accountRepository.findByStatus("CONNECTED").stream().findFirst();
        }

        boolean isLinked = accOpt.isPresent();
        String linkedUsername = isLinked ? accOpt.get().getUsername() : null;
        String linkedDisplayName = isLinked ? accOpt.get().getDisplayName() : null;
        Long linkedAccountId = isLinked ? accOpt.get().getId() : null;

        List<TikTokScheduledVideo> scheduledList = isLinked
                ? scheduledVideoRepository.findBySedeAndDateRange("GLOBAL", startDateTime, endDateTime)
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

            Optional<TikTokDayNote> noteOpt = dayNoteRepository.findBySedeNameAndNoteDate("GLOBAL", currentDay);

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
        result.put("sede", "GLOBAL");
        result.put("isLinked", isLinked);
        result.put("linkedUsername", linkedUsername);
        result.put("linkedDisplayName", linkedDisplayName);
        result.put("linkedAccountId", linkedAccountId);
        result.put("videosProgramados", videosProgramados);
        result.put("diasConContenido", diasConContenido);
        result.put("promedioDiario", promedioDiario);
        result.put("programacionActiva", programacionActiva);
        result.put("days", days);

        return result;
    }

    @Transactional
    public TikTokDayNote saveDayNote(String sedeName, LocalDate date, String note) {
        TikTokDayNote dayNote = dayNoteRepository.findBySedeNameAndNoteDate("GLOBAL", date)
                .orElse(TikTokDayNote.builder().sedeName("GLOBAL").noteDate(date).build());
        dayNote.setNote(note);
        return dayNoteRepository.save(dayNote);
    }

    @Transactional
    public TikTokScheduledVideo addScheduledVideo(TikTokScheduledVideo video) {
        if (video.getTitle() == null || video.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("El título del video es requerido.");
        }
        if (video.getScheduledAt() == null) {
            throw new IllegalArgumentException("La fecha y hora de programación es requerida.");
        }
        video.setSedeName("GLOBAL");
        if (video.getStatus() == null || video.getStatus().trim().isEmpty()) {
            video.setStatus("PROGRAMADO");
        }
        if (video.getDuration() == null || video.getDuration().trim().isEmpty()) {
            video.setDuration("0:30");
        }
        return scheduledVideoRepository.save(video);
    }

    @Transactional
    public void deleteScheduledVideo(Long id) {
        scheduledVideoRepository.deleteById(id);
    }

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
