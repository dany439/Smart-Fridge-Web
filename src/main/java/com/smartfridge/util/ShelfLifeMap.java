package com.smartfridge.util;

import java.util.Map;

public class ShelfLifeMap {

    private static final Map<String, Integer> SHELF_LIFE_DAYS = Map.ofEntries(
            // Meat & Poultry
            Map.entry("chicken", 2),
            Map.entry("beef", 3),
            Map.entry("pork", 3),
            Map.entry("fish", 2),
            Map.entry("shrimp", 2),
            // Dairy
            Map.entry("milk", 7),
            Map.entry("cheese", 14),
            Map.entry("yogurt", 10),
            Map.entry("butter", 30),
            // Vegetables
            Map.entry("lettuce", 5),
            Map.entry("spinach", 5),
            Map.entry("carrots", 14),
            Map.entry("broccoli", 7),
            // Fruits
            Map.entry("strawberries", 5),
            Map.entry("blueberries", 7),
            Map.entry("apples", 30),
            Map.entry("bananas", 5)
    );

    public static Integer getDays(String itemName) {
        if (itemName == null) return null;
        return SHELF_LIFE_DAYS.getOrDefault(itemName.toLowerCase().trim(), 7);
    }
}
