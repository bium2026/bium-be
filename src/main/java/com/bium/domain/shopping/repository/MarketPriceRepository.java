package com.bium.domain.shopping.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.shopping.entity.MarketPrice;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

	List<MarketPrice> findAllByItemNameIn(Collection<String> itemNames);

}
