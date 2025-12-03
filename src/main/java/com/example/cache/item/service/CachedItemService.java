package com.example.cache.item.service;

import com.example.cache.item.controller.dto.ItemResponse;
import com.example.cache.item.domain.Item;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CachedItemService {

    private static final Logger log = LoggerFactory.getLogger(CachedItemService.class);

    private static final String CACHE_KEY_PREFIX = "itemCache:";
    private static final long TTL_S = 120L;

    private final RedissonClient redissonClient;
    private final ItemReadService itemReadService;

    public ItemResponse getItem(Long id) {
        String key = buildKey(id);
        RBucket<ItemResponse> bucket = redissonClient.getBucket(key);

        ItemResponse cached = bucket.get();
        if (cached != null) {
            log.debug("Cache hit. id={}", id);
            return cached;
        }

        log.debug("Cache miss. id={}", id);
        return loadAndCache(id, bucket);
    }

    protected ItemResponse loadAndCache(Long id, RBucket<ItemResponse> bucket) {
        Item item = itemReadService.getItemOrThrow(id);

        ItemResponse response = ItemResponse.from(item);

        bucket.set(response, TTL_S, TimeUnit.SECONDS);
        log.debug("Cache put. id={}, ttlS={}", id, TTL_S);

        return response;
    }

    private String buildKey(Long id) {
        return CACHE_KEY_PREFIX + id;
    }
}
