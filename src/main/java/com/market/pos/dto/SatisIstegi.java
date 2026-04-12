package com.market.pos.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class SatisIstegi {
    private String odemeTipi;
    private List<SepetUrunDTO> sepet;
    
}