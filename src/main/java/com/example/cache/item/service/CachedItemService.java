package com.example.cache.item.service;

import com.example.cache.item.controller.dto.ItemResponse;
import com.example.cache.item.domain.Item;
import com.example.cache.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
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

    private static final String CACHE_NAME = "itemCache";
    private static final String LOCK_PREFIX = "lock:item:";

    private static final long LOGICAL_TTL_MS = 60_000L;
    private static final long PHYSICAL_TTL_MS = LOGICAL_TTL_MS * 5;

    private static final long LOCK_WAIT_MS = 50L;
    private static final long LOCK_LEASE_MS = 1_000L;

    private final ItemRepository itemRepository;
    private final RedissonClient redissonClient;

    @Transactional(readOnly = true)
    public ItemResponse getItem(Long id) {
        RMapCache<Long, ItemCacheEntry> cache = redissonClient.getMapCache(CACHE_NAME);
        long now = System.currentTimeMillis();

        ItemCacheEntry entry = cache.get(id);

        if (entry != null && entry.logicalExpireAt() > now) {
            log.debug("Cache hit (fresh). id={}", id);
            return entry.value();
        }

        log.debug("Cache stale or miss. id={}", id);

        String lockKey = LOCK_PREFIX + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);

            if (acquired) {
                entry = cache.get(id);
                now = System.currentTimeMillis();
                if (entry != null && entry.logicalExpireAt() > now) {
                    log.debug("Cache refreshed by another thread. id={}", id);
                    return entry.value();
                }

                log.debug("Refreshing cache from DB. id={}", id);
                Item item = itemRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Item not found. id=" + id));

                ItemResponse resp = ItemResponse.from(item);
                long newExpireAt = System.currentTimeMillis() + LOGICAL_TTL_MS;

                ItemCacheEntry newEntry = new ItemCacheEntry(resp, newExpireAt);
                cache.put(id, newEntry, PHYSICAL_TTL_MS, TimeUnit.MILLISECONDS);
                log.debug("Cache refreshed. id={}, logicalTtlMs={}, physicalTtlMs={}", id, LOGICAL_TTL_MS, PHYSICAL_TTL_MS);

                return resp;

            } else {
                if (entry != null) {
                    log.debug("Lock not acquired. Return stale value. id={}", id);
                    return entry.value();
                }

                log.warn("No cache entry and lock not acquired. Fallback DB. id={}", id);
                Item item = itemRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Item not found. id=" + id));
                return ItemResponse.from(item);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while acquiring lock. id=" + id, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("Failed to unlock. id={}", id, e);
                }
            }
        }
    }
}

