package com.smartfridge.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private static final int MAX_CALLS_PER_DAY = 10;

    // username -> [date, count]
    private final ConcurrentHashMap<String, long[]> usage = new ConcurrentHashMap<>();
    // long[0] = epoch day, long[1] = count

    public boolean isAllowed(String username) {
        long today = LocalDate.now().toEpochDay();

        usage.compute(username, (key, val) -> {
            if (val == null || val[0] != today) {
                return new long[]{today, 0};  // reset for new day
            }
            return val;
        });

        long[] val = usage.get(username);
        return val[1] < MAX_CALLS_PER_DAY;
    }

    public void increment(String username) {
        long today = LocalDate.now().toEpochDay();

        usage.compute(username, (key, val) -> {
            if (val == null || val[0] != today) {
                return new long[]{today, 1};
            }
            val[1]++;
            return val;
        });
    }

    public int remainingCalls(String username) {
        long today = LocalDate.now().toEpochDay();
        long[] val = usage.get(username);
        if (val == null || val[0] != today) return MAX_CALLS_PER_DAY;
        return (int) Math.max(0, MAX_CALLS_PER_DAY - val[1]);
    }
}