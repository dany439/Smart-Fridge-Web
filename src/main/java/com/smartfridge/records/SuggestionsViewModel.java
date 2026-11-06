package com.smartfridge.records;

import java.util.List;
import java.util.Map;

public record SuggestionsViewModel(
        boolean fridgeEmpty,
        boolean hasExpiringItems,
        List<Map<String, Object>> recipes,
        String error,
        String info
) {}
