package com.smartfridge.records;

import java.util.List;

public record Recipe(String title, List<String> ingredients, List<String> steps) {}