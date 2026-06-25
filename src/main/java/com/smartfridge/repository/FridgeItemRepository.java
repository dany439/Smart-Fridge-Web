package com.smartfridge.repository;

import com.smartfridge.entity.FridgeItem;
import com.smartfridge.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface FridgeItemRepository extends JpaRepository<FridgeItem, Integer> {
    List<FridgeItem> findByUser(User user);
    List<FridgeItem> findByUserAndCategory(User user, String category);
    List<FridgeItem> findByExpiryDateBeforeAndUser(LocalDateTime expiryDate, User user);
    long countByNameAndUser(String name, User user);
    List<FridgeItem> findByNameAndUser(String name, User user);

    @Query("SELECT f FROM FridgeItem f JOIN FETCH f.user ORDER BY f.user.username ASC, f.expiryDate ASC")
    List<FridgeItem> findAllWithUser();
}
