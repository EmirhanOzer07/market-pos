package com.market.pos.ekran;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring Boot backend ile HTTP iletişimi kuran istemci yardımcı sınıfı.
 *
 * <p>JWT token'ı atomik olarak tutar; tüm isteklere {@code Authorization: Bearer} başlığı ekler.
 * HTTP 5xx yanıtları {@link IOException} fırlatır.</p>
 *
 * <p>Oturum bilgisi ({token, marketId, kullaniciAdi, rol}) tek bir {@link AtomicReference} ile
 * yönetilir — dört ayrı volatile alan yerine tek atomik okuma/yazma garantisi sağlanır.</p>
 */
public class ApiClient {

    private static final String BASE_URL =
            "http://localhost:" + System.getProperty("server.port", "8080");
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Tek AtomicReference ile tüm oturum bilgisi atomik olarak okunur/yazılır.
     * null → oturum kapalı.
     */
    private static final AtomicReference<OturumBilgisi> oturum = new AtomicReference<>(null);

    /** Oturum bilgisini taşıyan değişmez kayıt. */
    private record OturumBilgisi(String token, Long marketId, String kullaniciAdi, String rol) {}

    // ===================== TOKEN YÖNETİMİ =====================

    public static void tokenKaydet(String token, Long mktId, String kAdi, String r) {
        oturum.set(new OturumBilgisi(token, mktId, kAdi, r));
    }

    public static void tokenTemizle() {
        oturum.set(null);
    }

    public static String getToken() {
        OturumBilgisi o = oturum.get();
        return o != null ? o.token() : null;
    }

    public static Long getMarketId() {
        OturumBilgisi o = oturum.get();
        return o != null ? o.marketId() : null;
    }

    public static String getKullaniciAdi() {
        OturumBilgisi o = oturum.get();
        return o != null ? o.kullaniciAdi() : null;
    }

    public static String getRol() {
        OturumBilgisi o = oturum.get();
        return o != null ? o.rol() : null;
    }

    public static boolean girisYapildiMi() {
        return oturum.get() != null;
    }

    // ===================== HTTP METODLARI =====================

    // GET isteği — JWT ile
    public static Map<String, Object> get(String endpoint) throws Exception {
        String token = getToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        String body = response.body();
        // 404 veya boş body → bulunamadı, null döndür (exception değil)
        if (response.statusCode() == 404 || body == null || body.isBlank() || "null".equals(body)) {
            return null;
        }
        if (response.statusCode() >= 500) {
            throw new IOException("Sunucu hatası: HTTP " + response.statusCode());
        }
        return mapper.readValue(body, Map.class);
    }

    // GET — Liste döner
    @SuppressWarnings("unchecked")
    public static java.util.List<Map<String, Object>> getList(String endpoint) throws Exception {
        String token = getToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 500) {
            throw new IOException("Sunucu hatası: HTTP " + status);
        }
        return mapper.readValue(response.body(), java.util.List.class);
    }

    // POST isteği — JWT ile
    public static Map<String, Object> post(String endpoint, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        String token = getToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        return yanıtıCoz(response);
    }

    // POST — JWT olmadan (giriş/kayıt için)
    public static Map<String, Object> postPublic(String endpoint, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        return yanıtıCoz(response);
    }

    // PUT isteği
    public static Map<String, Object> put(String endpoint, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        String token = getToken();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        return yanıtıCoz(response);
    }

    /**
     * Yanıt gövdesini Map'e çevirir.
     * JSON nesnesi ({...}) → doğrudan parse et.
     * JSON dizisi ([...]) veya düz metin → {"mesaj": body, "status": kod} olarak sar.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> yanıtıCoz(HttpResponse<String> response) throws Exception {
        String body = response.body();
        if (body != null && !body.isBlank()) {
            String trimmed = body.stripLeading();
            if (trimmed.startsWith("{")) {
                return mapper.readValue(body, Map.class);
            }
        }
        return Map.of("mesaj", body != null ? body : "", "status", response.statusCode());
    }

    // DELETE isteği — başarısızsa {"hata":"..."} mesajını exception olarak fırlatır
    public static int delete(String endpoint) throws Exception {
        String token = getToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status != 200 && status != 204) {
            String mesaj = "Silinemedi";
            try {
                Map<?, ?> body = mapper.readValue(response.body(), Map.class);
                if (body.containsKey("hata")) mesaj = body.get("hata").toString();
            } catch (Exception ignored) {}
            throw new RuntimeException(mesaj);
        }
        return status;
    }

    // Çıkış yap
    public static void cikisYap() throws Exception {
        String token = getToken();
        if (token == null) return;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/auth/cikis"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
        tokenTemizle();
    }
}
