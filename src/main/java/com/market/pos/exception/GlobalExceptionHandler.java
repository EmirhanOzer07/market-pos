package com.market.pos.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// ✅ Bu sınıf tüm controller'lardaki hataları merkezi olarak yakalar
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Yetki hatası → 403 Forbidden
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> yetkiHatasi(SecurityException e) {
        return hataYaniti("Yetkisiz işlem.", HttpStatus.FORBIDDEN);
    }

    // Geçersiz argüman → 400 Bad Request
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> gecersizArguman(IllegalArgumentException e) {
        // Bu hata kullanıcıya gösterilebilir (teknik detay içermiyor)
        return hataYaniti(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // @Valid validation hataları → 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validationHatasi(MethodArgumentNotValidException e) {
        String ilkHata = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Geçersiz veri gönderildi.");
        return hataYaniti(ilkHata, HttpStatus.BAD_REQUEST);
    }

    // Bulunamadı hatası → 404 Not Found
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> bulunamadi(java.util.NoSuchElementException e) {
        return hataYaniti("İstenen kayıt bulunamadı.", HttpStatus.NOT_FOUND);
    }

    // Beklenmeyen tüm hatalar → 500 (ama detay göstermiyoruz!)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> genelHata(Exception e) {
        // ✅ GÜVENLİK: Gerçek hatayı kullanıcıya göstermiyoruz — sadece logla
        log.error("Beklenmeyen hata: {}", e.getMessage(), e);
        return hataYaniti("Sunucu hatası oluştu.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Yardımcı metot: Standart hata formatı oluştur
    private ResponseEntity<Map<String, String>> hataYaniti(String mesaj, HttpStatus status) {
        Map<String, String> yanit = new HashMap<>();
        yanit.put("hata", mesaj);
        return ResponseEntity.status(status).body(yanit);
    }
}