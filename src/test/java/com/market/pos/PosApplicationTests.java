package com.market.pos;

import com.market.pos.config.TestDataSourceConfig;
import com.market.pos.service.ExcelYedekService;
import com.market.pos.service.VeriTabaniAnahtarService;
import com.market.pos.service.YedekService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestDataSourceConfig.class)
class PosApplicationTests {

	@MockBean private YedekService yedekService;
	@MockBean private ExcelYedekService excelYedekService;
	@MockBean private VeriTabaniAnahtarService veriTabaniAnahtarService;

	@Test
	void contextLoads() {
	}

}
