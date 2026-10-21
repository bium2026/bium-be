package com.bium.domain.fridge.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.fridge.entity.Fridge;

public interface FridgeRepository extends JpaRepository<Fridge, Long> {
}
