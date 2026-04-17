# OZR POS — Market Satış Noktası Sistemi

Küçük ve orta ölçekli marketler için geliştirilmiş, güvenli ve internet bağlantısı gerektirmeyen masaüstü satış noktası (POS) uygulaması.

> A secure, offline-first Point of Sale system for retail stores. Built with Spring Boot + JavaFX as a standalone desktop application — no internet required for daily operations.

[![CI](https://github.com/EmirhanOzer07/market-pos/actions/workflows/ci.yml/badge.svg)](https://github.com/EmirhanOzer07/market-pos/actions/workflows/ci.yml)
![Version](https://img.shields.io/badge/version-2.1.1-blue)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue?logo=java)
![H2](https://img.shields.io/badge/H2-AES--256%20Encrypted-red)
![JWT](https://img.shields.io/badge/Auth-JWT-yellow)
![License](https://img.shields.io/badge/License-Proprietary-lightgrey)

---

## Özellikler / Core Features

- **Satış & Kasa** — Barkod okuma, sepet yönetimi, nakit/kart ödeme
- **Ürün Yönetimi** — Ürün ekleme, güncelleme, silme; CSV ile toplu yükleme
- **Personel Yönetimi** — `ADMIN` ve `KASIYER` rolleri, şifre değiştirme
- **Satış Raporları** — Günlük/dönemsel ciro, nakit-kart ayrımı
- **Otomatik Yedekleme** — Başlangıçta SQL yedeği + her gece 22:00'de Excel ürün listesi
- **Yedek & Geri Yükleme** — Son 30 yedekten tek tıkla geri dönüş
- **Lisans Yönetimi** — Market bazında bitiş tarihi, uygulama içi uyarı
- **Otomatik Güncelleme** — GitHub Releases üzerinden yeni sürüm tespiti ve kurulum
- **Karanlık / Aydınlık Tema** — Kullanıcı tercihine göre değiştirilebilir
- **Çok Kiracılı Mimari** — Tek sunucu, birden fazla bağımsız market

---

## Teknoloji Yığını / Tech Stack

| Katman | Teknoloji |
|--------|-----------|
| Backend | Spring Boot 3.3 |
| UI | JavaFX 21 |
| Veritabanı | H2 (AES-256 şifreli, gömülü) |
| Kimlik Doğrulama | JWT (HMAC-SHA256) |
| Şifre Hashleme | BCrypt |
| Hız Sınırlama | Bucket4j 8.10 (token bucket) |
| Önbellekleme | Caffeine Cache (30s TTL) |
| Excel Dışa Aktarım | Apache POI 5 |
| Test | JUnit 5 + Spring Boot Test (H2 in-memory) |
| Paketleme | jpackage (gömülü JRE, `.exe`) |

---

## Güvenlik Mimarisi / Security Architecture

```
HTTP Request
    │
    ├─► RateLimitFilter     (Bucket4j — 10 req/min per IP on /api/auth/**)
    │
    ├─► JwtFilter           (HMAC-SHA256 token validation, SecurityContext population)
    │
    ├─► SecurityFilterChain (Spring Security — stateless, CSRF disabled)
    │
    ├─► MarketFilterAspect  (AOP — injects Hibernate @Filter before every
    │                         repository call; enforces row-level tenant isolation)
    │
    └─► Controller → Service → Repository
```

| Katman | Uygulama |
|--------|----------|
| Veritabanı | AES-256 şifreleme (`CIPHER=AES`, per-install key) |
| Kimlik doğrulama | JWT — HMAC-SHA256, kara liste desteği |
| Şifreler | BCrypt hashing |
| Çok kiracılılık | Hibernate `@Filter` ile satır düzeyinde izolasyon |
| Hız sınırlama | IP başına dakikada 10 istek |
| Yetkilendirme | Spring Security 6 + `@PreAuthorize` |
| SuperAdmin | Yalnızca localhost (`127.0.0.1`) erişimi |
| Denetim | Tüm giriş denemeleri ve kritik işlemler `[AUDIT]` prefix ile loglanır |
| Dosya yükleme | MIME tipi + uzantı + boyut doğrulaması |
| Path traversal | Yedek dosya erişiminde `normalize()` + allowlist kontrolü |

**Veritabanı şifreleme detayı:**  
H2 `CIPHER=AES` modu tüm `.mv.db` sayfalarını 256-bit AES ile şifreler. Anahtar `%LOCALAPPDATA%\MarketPOS\.dbkey` dosyasında saklanır; `icacls` ile dosya izinleri yalnızca mevcut Windows kullanıcısına kısıtlanır.

---

## Sistem Gereksinimleri

**Geliştirici ortamı:**
- Java 21+ JDK (Temurin önerilir: [adoptium.net](https://adoptium.net))
- Maven 3.9+

**Müşteri bilgisayarı:**
- Windows 10/11 (64-bit)
- Java kurulumu gerekmez — uygulama gömülü JRE ile gelir

---

## Kurulum (Geliştirici) / How to Run

### Uygulama

```bash
# Depoyu klonlayın
git clone https://github.com/EmirhanOzer07/market-pos.git
cd market-pos

# Derleyin (testler atlanır — hızlı iterasyon için)
mvn package -DskipTests

# Çalıştırın
java -jar target/pos-0.0.1-SNAPSHOT.jar
```

İlk başlatmada uygulama otomatik olarak:
1. `%LOCALAPPDATA%\MarketPOS\config.properties` dosyasını rastgele JWT secret ve DB şifresiyle oluşturur.
2. AES-256 şifreli H2 veritabanını başlatır.
3. JavaFX giriş ekranını açar.

### Testler

```bash
mvn test
```

Testler in-memory H2 instance'ı üzerinde çalışır (üretim verisi dokunulmaz). Test profili `NoOpCacheManager` kullanarak testler arası cache zehirlenmesini önler.

---

## Konfigürasyon / Configuration

Hassas değerler `%LOCALAPPDATA%\MarketPOS\config.properties` dosyasında tutulur (otomatik üretilir, git'e girmez):

| Özellik | Açıklama |
|---------|----------|
| `JWT_SECRET` | 64 karakterlik rastgele hex — HMAC-SHA256 imzalama anahtarı |
| `DB_KULLANICI_SIFRESI` | Rastgele UUID — H2 SQL kimlik doğrulama şifresi |

`application.properties` bu değerleri `${JWT_SECRET}` gibi placeholder'larla okur. Kaynak kodda sır yoktur.

### İsteğe Bağlı: Excel Yedek Yolu

Günlük Excel yedeklerini Google Drive senkronizasyon klasörüne yönlendirmek için `application.properties`'e ekleyin:

```properties
backup.excel.path=C:/Users/<kullanici>/Google Drive/MarketPOS/yedek
```

---

## Proje Yapısı / Project Structure

```
src/
├── main/
│   ├── java/com/market/pos/
│   │   ├── PosApplication.java        # Spring Boot + JavaFX giriş noktası
│   │   ├── ConfigManager.java         # Başlangıç konfigürasyonu, port tespiti
│   │   ├── config/                    # Spring Security, Cache, DataSource bean'leri
│   │   ├── controller/                # REST API uç noktaları (Satış, Ürün, Kullanıcı, Yedek)
│   │   ├── ekran/                     # JavaFX ekranları (Giriş, Kasa, Yönetim)
│   │   ├── entity/                    # JPA varlıkları (Market, Urun, Kullanici, Satis)
│   │   ├── repository/                # Spring Data JPA repository'leri
│   │   ├── security/                  # JWT, AOP filtre, rate limiting, audit logger
│   │   └── service/                   # İş mantığı (Yedek, Önbellek, Şifreleme)
│   └── resources/
│       ├── application.properties
│       └── db/migration/              # Flyway SQL migrasyonları
└── test/
    ├── java/com/market/pos/           # Entegrasyon testleri (satış, izolasyon, CSV)
    └── resources/application-test.properties
```

---

## Kullanıcı Rolleri / User Roles

| Rol | Yetkiler |
|-----|----------|
| `ADMIN` | Tüm işlemler: ürün, personel, raporlar, yedek |
| `KASIYER` | Yalnızca satış ve kasa işlemleri |
| Patron | SuperAdmin paneli: market lisansları, davetiye üretimi (yalnızca localhost) |

---

## Yedekleme Takvimi / Backup Schedule

| Tetikleyici | Tür | Saklama |
|-------------|-----|---------|
| Her başlangıçta (bugün yoksa) | SQL yedek (.zip, şifreli) | Son 30 dosya |
| Her gece 22:00 | Excel ürün listesi (.xlsx) | Son 20 dosya |
| Yönetim paneli tuşu | SQL veya Excel (istek üzerine) | — |

Yedekler varsayılan olarak `%LOCALAPPDATA%\MarketPOS\yedek\` altında saklanır.

---

## Müşteri Kurulumu

1. `OZRPos/` klasörünü müşteri bilgisayarına kopyalayın.
2. `OZRPos.exe`'yi çalıştırın.
3. Patron panelinden üretilen davetiye koduyla kayıt olun.

Sonraki güncellemeler GitHub Releases'dan otomatik indirilip kurulur.

---

## Legacy Migrasyon Notu

v1.0 öncesi kurulumlar şifresiz H2 veritabanı kullanıyordu. v1.x'e ilk geçişte:

1. Uygulama şifresiz `.mv.db` dosyasını tespit eder.
2. Tüm veriyi geçici SQL script'e aktarır.
3. Şifresiz dosyaları siler.
4. Veriyi yeni AES-256 şifreli veritabanına yükler.
5. Geçici dosyaları temizler.

Migrasyon otomatik ve kullanıcıya şeffaf olarak çalışır.

> **v2.0 Planı:** v1.0-öncesi kurulumlar desteklenmediğinde `ConfigManager.LEGACY_DB_SIFRESI_V1`, `DataSourceConfig.ESKI_DB_SIFRESI` ve `eskiDbMigrasyonuYap` metodunu silin.

---

## Lisans

Tüm hakları saklıdır. Ticari kullanım için iletişime geçin.
