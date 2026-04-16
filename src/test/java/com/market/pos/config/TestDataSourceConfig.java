package com.market.pos.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Test profili için şifresiz H2 in-memory DataSource sağlar.
 *
 * <p>Üretim DataSourceConfig'i @Profile("!test") ile devre dışıdır; bu sınıf
 * onun yerine DataSource + EntityManagerFactory + TransactionManager üçlüsünü sağlar.
 * Her test context'i temiz bir schema ile başlar (ddl-auto=create-drop).</p>
 */
@TestConfiguration
public class TestDataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb-" + System.nanoTime()) // Her context'e özgü DB adı
                .build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.market.pos.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        factory.setJpaVendorAdapter(vendorAdapter);

        Properties jpaProperties = new Properties();
        jpaProperties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        jpaProperties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        jpaProperties.setProperty("hibernate.show_sql", "false");
        // Hibernate filtreleri (marketFilter) için gerekli
        jpaProperties.setProperty("hibernate.session_factory.session_scoped_interceptors",
                "org.hibernate.Session");
        factory.setJpaProperties(jpaProperties);

        return factory;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    /**
     * Testler arası stale cache sorununu önler.
     * AppConfig'deki CaffeineCacheManager'ı NoOp ile ezilir:
     * Caffeine 30s TTL, tearDown/setUp arasında eski market ID'yi döndürür
     * → MarketFilterAspect yanlış filter parametresi set eder → 400/500.
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new NoOpCacheManager();
    }
}
