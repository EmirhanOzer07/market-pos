package com.market.pos.ekran;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class ApiClient {

    private static final String BASE_URL =
            "http://localhost:" + System.getProperty("server.port", "8080");
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    // JWT token — giriş yapınca buraya kaydedilir
    // volatile: JavaFX UI thread ve arka plan thread'ler arasında görünürlük garantisi
    private static volatile String jwtToken = null;
    private static volatile Long marketId = null;
    private static volatile String kullaniciAdi = null;
    private static volatile String rol = null;

    // ===================== TOKEN YÖNETİMİ =====================

    public static void tokenKaydet(String token, Long mktId, String kAdi, String r) {
        jwtToken = token;
        marketId = mktId;
        kullaniciAdi = kAdi;
        rol = r;
    }

    public static void tokenTemizle() {
        jwtToken = null;
        marketId = null;
        kullaniciAdi = null;
        rol = null;
    }

    public static String getToken() { return jwtToken; }
    public static Long getMarketId() { return marketId; }
    public static String getKullaniciAdi() { return kullaniciAdi; }
    public static String getRol() { return rol; }
    public static boolean girisYapildiMi() { return jwtToken != null; }

    // ===================== HTTP METODLARI =====================

    // GET isteği — JWT ile
    public static Map<String, Object> get(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        String body = response.body();
        // 404 veya boş body → ürün bulunamadı, null döndür (exception değil)
        if (response.statusCode() == 404 || body == null || body.isBlank() || "null".equals(body)) {
            return null;
        }
        return mapper.readValue(body, Map.class);
    }

    // GET — Liste döner
    public static java.util.List getList(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        return mapper.readValue(response.body(), java.util.List.class);
    }

    // POST isteği — JWT ile
    public static Map<String, Object> post(String endpoint, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        // Yanıt string ise Map'e sar
        String body2 = response.body();
        if (body2.startsWith("{")) {
            return mapper.readValue(body2, Map.class);
        } else {
            return Map.of("mesaj", body2, "status", response.statusCode());
        }
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

        String body2 = response.body();
        if (body2.startsWith("{")) {
            return mapper.readValue(body2, Map.class);
        } else {
            return Map.of("mesaj", body2, "status", response.statusCode());
        }
    }

    // PUT isteği
    public static Map<String, Object> put(String endpoint, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        String body2 = response.body();
        if (body2.startsWith("{")) {
            return mapper.readValue(body2, Map.class);
        } else {
            return Map.of("mesaj", body2, "status", response.statusCode());
        }
    }

    // DELETE isteği — başarısızsa {"hata":"..."} mesajını exception olarak fırlatır
    public static int delete(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + jwtToken)
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
        if (jwtToken == null) return;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/auth/cikis"))
                .header("Authorization", "Bearer " + jwtToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
        tokenTemizle();
    }
}