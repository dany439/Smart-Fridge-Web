package com.smartfridge.service;

import com.smartfridge.records.RecipesResponse;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Service
public class RecipeCacheService {

    private static final String KEY_PREFIX = "recipes:";
    private static final int TTL_SECONDS = 10 * 60; // 10 minutes cached

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeCacheService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public Optional<RecipesResponse> getCached(String username) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(KEY_PREFIX + username);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, RecipesResponse.class));
        } catch (Exception e) {
            // don't let a cache/parse failure break the page — just treat as a miss
            return Optional.empty();
        }
    }

    public void save(String username, RecipesResponse response) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = objectMapper.writeValueAsString(response);
            jedis.setex(KEY_PREFIX + username, TTL_SECONDS, json);
        } catch (Exception e) {
            // caching is best-effort — don't fail the request if Redis is down
        }
    }

    public void evict(String username) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(KEY_PREFIX + username);
        } catch (Exception e) {
            // ignore
        }
    }
}