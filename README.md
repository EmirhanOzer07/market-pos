# Market POS Sistemi

Küçük ve orta ölçekli marketler için geliştirilmiş, güvenli ve internet bağlantısı gerektirmeyen masaüstü satış noktası (POS) uygulaması.

> A secure, offline-first Point of Sale system for retail stores. Built with Spring Boot + JavaFX as a standalone desktop application — no internet required for daily operations.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen?logo=springboot)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue?logo=java)
![H2](https://img.shields.io/badge/H2-AES--256%20Encrypted-red)
![JWT](https://img.shields.io/badge/Auth-JWT-yellow)
![License](https://img.shields.io/badge/License-Proprietary-lightgrey)

---

## Özellikler

- **Satış & Kasa** — Barkod okuma, sepet yönetimi, nakit/kart ödeme
- **Ürün Yönetimi** — Ürün ekleme, güncelleme, silme; CSV ile toplu yükleme
- **Personel Yönetimi** — ADMIN ve KASIYER rolleri, şifre değiştirme
- **Satış Raporları** — Günlük/dönemsel ciro, nakit-kart ayrımı
- **Otomatik Yedekleme** — Günlük SQL yedekleri + Excel ürün listesi (Google Drive uyumlu)
- **Yedek & Geri Yükleme** — Son 20 yedekten tek tıkla geri dönüş
- **Lisans Yönetimi** — Market bazında bitiş tarihi, uygulama içi uyarı
- **Otomatik Güncelleme** — GitHub üzerinden yeni sürüm tespiti ve kurulum
- **Karanlık / Aydınlık Tema** — Kullanıcı tercihine göre değiştirilebilir
- **Çok Kiracılı Mimari** — Tek sunucu, birden fazla bağımsız market

---

## Teknoloji Yığını

| Katman | Teknoloji |
|---|---|
| Backend | Spring Boot 3.3.4 |
| UI | JavaFX 21 |
| Veritabanı | H2 (AES-256 şifreli, gömülü) |
| Kimlik Doğrulama | JWT (HMAC-SHA256) |
| Şifre Hashleme | BCrypt |
| Hız Sınırlama | Bucket4j (token bucket) |
| Önbellekleme | Caffeine Cache |
| Excel Dışa Aktarım | Apache POI 5.2.5 |
| Paketleme | jpackage (gömülü JRE, `.exe`) |

---

## Güvenlik Özellikleri

| Katman | Uygulama |
|---|---|
| Veritabanı | AES-256 şifreleme (`CIPHER=AES`) |
| Kimlik doğrulama | JWT — HMAC-SHA256, kara liste desteği |
| Şifreler | BCrypt hashing |
| Çok kiracılılık | Hibernate `@Filter` ile satır düzeyinde izolasyon |
| Hız sınırlama | IP başına dakikada 10 istek |
| Yetkilendirme | Spring Security + `@PreAuthorize` |
| SuperAdmin | Yalnızca localhost erişimi |
| Denetim | Tüm giriş denemeleri ve işlemler loglanır |
| Dosya yükleme | MIME tipi + uzantı + boyut doğrulaması |
| Path traversal | Yedek dosya erişiminde `normalize()` kontrolü |

---

## Sistem Gereksinimleri

**Geliştirici ortamı:**
- Java 21 (JDK)
- Maven 3.9+

**Müşteri bilgisayarı:**
- Windows 10/11 (64-bit)
- Java kurulumu gerekmez — uygulama gömülü JRE ile gelir

---

## Kurulum (Geliştirici)

```bash
git clone https://github.com/EmirhanOzer07/market-pos.git
cd market-pos
mvn package -DskipTests
java -jar target/pos-0.0.1-SNAPSHOT.jar
```

---

## Müşteri Kurulumu

1. `MarketPOS/` klasörünü müşteri bilgisayarına kopyalayın
2. `MarketPOS.exe`'yi çalıştırın
3. Davetiye koduyla kayıt olun (Patron panelinden üretilir)

Sonraki güncellemeler otomatik olarak indirilip kurulur.

---

## Proje Yapısı

```
src/main/java/com/market/pos/
├── config/          # Spring Security, uygulama bean'leri, DataSource
├── controller/      # REST API uç noktaları
├── dto/             # İstek/yanıt veri transfer nesneleri
├── ekran/           # JavaFX UI ekranları ve API istemcisi
├── entity/          # JPA varlıkları
├── exception/       # Global hata yönetimi
├── repository/      # Spring Data JPA repository'leri
├── security/        # JWT, rate limiting, audit, AOP filtresi
└── service/         # Yedekleme ve Excel dışa aktarım servisleri
```

---

## Kullanıcı Rolleri

| Rol | Yetkiler |
|---|---|
| `ADMIN` | Tüm işlemler: ürün, personel, raporlar, yedek |
| `KASIYER` | Yalnızca satış ve kasa işlemleri |
| Patron | SuperAdmin paneli: market lisansları, davetiye üretimi |

---

## Yedekleme

- **Günlük otomatik yedek** — her gece 02:00 (son 30 yedek saklanır)
- **İşlem sonrası yedek** — her satış/ürün/silme işleminden sonra (son 20 yedek)
- **Excel dışa aktarım** — her gece 23:59 (ürün listesi)
- Yedekler `%LOCALAPPDATA%\MarketPOS\yedekler\` konumunda tutulur

---

## Lisans

Tüm hakları saklıdır. Ticari kullanım için iletişime geçin.
