package com.market.pos.repository;

import com.market.pos.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@link Market} varlığı için Spring Data JPA repository.
 *
 * <p>Standart CRUD işlemleri {@link JpaRepository} tarafından sağlanır.</p>
 */
public interface MarketRepository extends JpaRepository<Market, Long> {
}
