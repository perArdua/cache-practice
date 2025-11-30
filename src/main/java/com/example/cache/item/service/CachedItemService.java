package com.example.cache.item.service;

import com.example.cache.item.controller.dto.ItemResponse;
import com.example.cache.item.domain.Item;
import com.example.cache.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CachedItemService {

    private static final Logger log = LoggerFactory.getLogger(CachedItemService.class);

    private static final String CACHE_NAME = "itemCache"; // RMapCache 이름
    private static final long TTL_MS = 60_000L;           // 60초 TTL

    private final ItemRepository itemRepository;
    private final RedissonClient redissonClient;

    @Transactional(readOnly = true)
    public ItemResponse getItem(Long id) {
        RMapCache<Long, ItemResponse> cache = redissonClient.getMapCache(CACHE_NAME);

        ItemResponse cached = cache.get(id);
        if (cached != null) {
            log.debug("Cache hit. id={}", id);
            return cached;
        }

        log.debug("Cache miss. id={}", id);

        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found. id=" + id));

        ItemResponse response = ItemResponse.from(item);

        cache.put(id, response, TTL_MS, TimeUnit.MILLISECONDS);
        log.debug("Cache put. id={}, ttlMs={}", id, TTL_MS);

        return response;
    }
}
