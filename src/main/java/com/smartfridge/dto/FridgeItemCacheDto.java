package com.smartfridge.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FridgeItemCacheDto(int id, int userId, String name, String category,
                                 BigDecimal quantity, String unit,
                                 LocalDateTime storageDate, LocalDateTime expiryDate,
                                 String addedVia) {}
