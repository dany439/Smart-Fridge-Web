package com.smartfridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfridge.dto.FridgeItemCacheDto;
import com.smartfridge.entity.FridgeItem;
import com.smartfridge.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FridgeCacheService {

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    @Autowired
    private final UserRepository userRepository;

    private static final int CACHE_TTL_SECONDS = 20 * 60; // 20 minutes

    private String itemKey(int id) { return "fridge:item:" + id; }
    private String userSetKey(int userId) { return "fridge:user:" + userId + ":items"; }

    public void cacheItem(FridgeItem item) {
        try (Jedis jedis = jedisPool.getResource()) {
            String itemKey = itemKey(item.getId());
            String setKey = userSetKey(item.getUser().getId());

            jedis.setex(itemKey, CACHE_TTL_SECONDS, toJson(toDto(item)));
            jedis.sadd(setKey, String.valueOf(item.getId()));
            jedis.expire(setKey, CACHE_TTL_SECONDS);
        }
    }

    public void removeItem(int itemId, int userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(itemKey(itemId));
            jedis.srem(userSetKey(userId), String.valueOf(itemId));
        }
    }

    /** Returns null on cache miss (no set found), empty list if user genuinely has 0 items but cache is warm. */
    public List<FridgeItem> getUserItems(int userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.exists(userSetKey(userId))) return null;

            Set<String> ids = jedis.smembers(userSetKey(userId));
            Pipeline pipeline = jedis.pipelined();
            List<Response<String>> responses = new ArrayList<>();
            for (String id : ids) responses.add(pipeline.get(itemKey(Integer.parseInt(id))));
            pipeline.sync();

            List<FridgeItem> items = new ArrayList<>();
            for (Response<String> r : responses) {
                if (r.get() != null) items.add(fromDto(fromJson(r.get())));
            }
            return items;
        }
    }

    public void warmCache(int userId, List<FridgeItem> items) {
        try (Jedis jedis = jedisPool.getResource()) {
            String setKey = userSetKey(userId);
            Pipeline pipeline = jedis.pipelined();
            pipeline.del(setKey);
            for (FridgeItem item : items) {
                String itemKey = itemKey(item.getId());
                pipeline.setex(itemKey, CACHE_TTL_SECONDS, toJson(toDto(item)));
                pipeline.sadd(setKey, String.valueOf(item.getId()));
            }
            pipeline.expire(setKey, CACHE_TTL_SECONDS);
            pipeline.sync();
        }
    }

    private FridgeItemCacheDto toDto(FridgeItem item) {
        return new FridgeItemCacheDto(
                item.getId(), item.getUser().getId(), item.getName(), item.getCategory(),
                item.getQuantity(), item.getUnit(), item.getStorageDate(), item.getExpiryDate(),
                item.getAddedVia()
        );
    }

    private FridgeItem fromDto(FridgeItemCacheDto dto) {
        FridgeItem item = new FridgeItem();
        item.setId(dto.id());
        item.setUser(userRepository.getReferenceById(dto.userId())); // lazy proxy, no DB hit
        item.setName(dto.name());
        item.setCategory(dto.category());
        item.setQuantity(dto.quantity());
        item.setUnit(dto.unit());
        item.setStorageDate(dto.storageDate());
        item.setExpiryDate(dto.expiryDate());
        item.setAddedVia(dto.addedVia());
        return item;
    }

    private String toJson(FridgeItemCacheDto dto) {
        try { return objectMapper.writeValueAsString(dto); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private FridgeItemCacheDto fromJson(String json) {
        try { return objectMapper.readValue(json, FridgeItemCacheDto.class); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
