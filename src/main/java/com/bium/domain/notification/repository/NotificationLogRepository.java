package com.bium.domain.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bium.domain.notification.entity.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}
