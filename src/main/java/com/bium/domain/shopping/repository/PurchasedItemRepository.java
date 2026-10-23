package com.bium.domain.shopping.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.shopping.entity.PurchasedItem;

public interface PurchasedItemRepository extends JpaRepository<PurchasedItem, Long> {
}
