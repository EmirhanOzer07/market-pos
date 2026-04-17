# Changelog

OZR POS sürüm geçmişi. [Keep a Changelog](https://keepachangelog.com/) formatı.

---

## [2.1.0] — 2026-04-16

### Eklendi
- `KasaEkrani`: Bulunamayan barkod geçmişi ve bilgi dialog'u
- `GirisEkrani`: Kayıt Ol butonu + `KayitEkrani` (davetiye kodlu kayıt)
- `PosApplication`: `version.txt` ile sürüm bilgisi altyapısı
- Caffeine Cache: `@Cacheable("urunListesi")`, 30 sn TTL, max 100 giriş

### Değiştirildi
- `SatisController.satisTamamla`: dönüş tipi `String` → `Map<String,Object>` (`satisId` eklendi)
- `KasaEkrani`: Delete tuşu ile seçili satır silme
- `YonetimEkrani`: Çift tıklama ile düzenleme, sütun sıralama, dinamik satır sayısı etiketi
- Bildirim gecikmesi 4 sn'ye güncellendi (KasaEkrani, YonetimEkrani, PatronEkrani)
- Pencere boyutu `config.properties`'e kaydedilip yükleniyor
- `PatronEkrani`: Static singleton `HttpClient` (bağlantı yeniden kullanımı)
- `YedekService`: `ReentrantLock` ile thread güvenliği
- `application.properties`: HikariCP `max=3 / min=1`, `open-in-view=false`

### Güvenlik
- `SatisController.satisTamamla` endpoint'ine input validation güçlendirildi (audit bulgusu)

### Temizlik
- `JwtFilter`: Kullanılmayan `ArrayList` import kaldırıldı
- `UrunRepository`: Tekrarlayan `findAllByMarketId` kaldırıldı, `findByMarketId` ile birleştirildi

---

## [2.0.0] — 2026-04-15

### Büyük Değişiklikler
- **OZR POS** yeniden markalaması (eski: Market POS)
- **Patron Paneli**: Lisans yönetimi ve davetiye üretimi (yalnızca localhost erişimi)
- **Çok Kiracılı Mimari**: Hibernate `@Filter` ile satır düzeyinde market izolasyonu

### Eklendi
- Karanlık / Aydınlık tema desteği
- Otomatik güncelleme: GitHub Releases üzerinden sürüm tespiti ve kurulum
- AES-256 H2 veritabanı şifrelemesi (`CIPHER=AES`, per-install key)
- JWT kara liste desteği
- `PosApplication`: `CompletableFuture` ile Spring başlangıcını bekleyen yükleniyor ekranı
- Entegrasyon test altyapısı (JUnit 5, H2 in-memory, test profili)

### Güvenlik
- CWE-79: HTML template endpoint'leri kaldırıldı, `SecurityConfig` temizlendi
- CWE-200: `GlobalExceptionHandler`'da `System.err.println` → SLF4J logger
- CWE-89: `YedekService`'de SQL parametresi sanitize edildi
- CWE-613: `JwtFilter` finally bloğunda `MarketFilterAspect.filtreTemizle()` doğrulandı

---

## [1.7.0] — 2026-04-14

### Değiştirildi
- Sürüm numarası güncellendi

---

## [1.6.0] — 2026-04-13

### Eklendi
- Sınıf düzeyinde Javadoc tamamlandı

### Temizlik
- Gereksiz yorum satırları kaldırıldı, kod yapısı sadeleştirildi

---

## [1.5.0] — 2026-04-13

### Eklendi
- Profesyonel GitHub görünümü (README, badges)

### Güvenlik
- Güvenlik ve kod kalitesi iyileştirmeleri

### Düzeltildi
- Şifre değiştirme hata mesajı düzeltildi

---

## [1.2.0] — 2026-04-13

### Eklendi
- Yeni özellikler ve güvenlik iyileştirmeleri (audit bulguları)

---

## [1.1.0] — 2026-04-07

### Düzeltildi
- Güncelleme sistemi düzeltildi
- JAR yolu düzeltildi
- Yeniden başlatma davranışı düzeltildi
- Port temizleme eklendi

---

## [1.0.0] — 2026-04-07

### İlk Sürüm
- Spring Boot 3.3 + JavaFX 21 masaüstü POS uygulaması
- `ADMIN` ve `KASIYER` rolleri, JWT kimlik doğrulama
- Ürün yönetimi, satış, raporlar, otomatik yedekleme
