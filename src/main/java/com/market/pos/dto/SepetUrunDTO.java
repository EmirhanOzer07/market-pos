package com.market.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Sepetteki tek bir ürün kalemini temsil eden veri transfer nesnesi.
 *
 * <p>Barkod üzerinden sunucu tarafında ürün doğrulaması yapılır;
 * istemciden gelen fiyat bilgisi hiçbir zaman kullanılmaz.</p>
 */
@Getter
@Setter
public class SepetUrunDTO {

    private Long id;

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String barkod;

    @NotNull(message = "Adet boş olamaz!")
    @DecimalMin(value = "0.001", message = "Adet sıfır veya negatif olamaz!")
    private Double adet;
}
