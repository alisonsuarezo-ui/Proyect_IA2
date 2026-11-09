package com.taskflow.repository;

import com.taskflow.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n left join fetch n.task where n.user.id = :userId order by n.createdAt desc")
    List<Notification> findByUserId(@Param("userId") Long userId);

    @Query("select n from Notification n left join fetch n.task where n.user.id = :userId and n.read = false order by n.createdAt desc")
    List<Notification> findUnreadByUserId(@Param("userId") Long userId);

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByDedupKey(String dedupKey);

    @Modifying
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);
}
