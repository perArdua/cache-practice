package com.example.cache.item.service;

import com.example.cache.item.controller.dto.ItemResponse;
import com.example.cache.item.domain.Item;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CachedItemService {

    private static final Logger log = LoggerFactory.getLogger(CachedItemService.class);

    private static final String CACHE_NAME = "itemCache";
    private static final String LOCK_PREFIX = "lock:item:";

    private static final long TTL_MS = 30_000L;      // 캐시 TTL
    private static final long LOCK_WAIT_MS = 300L;   // 락 대기 시간
    private static final long LOCK_LEASE_MS = 1_000L;// 락 자동 해제 시간
    private static final long SPIN_WAIT_MS = 20L;    // 스핀 대기 간격

    private final RedissonClient redissonClient;
    private final ItemReadService itemReadService;

    public ItemResponse getItem(Long id) {
        RMapCache<Long, ItemResponse> cache = redissonClient.getMapCache(CACHE_NAME);

        ItemResponse cached = cache.get(id);
        if (cached != null) {
            log.debug("Cache hit. id={}", id);
            return cached;
        }

        log.debug("Cache miss. id={}", id);

        String lockKey = LOCK_PREFIX + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);

            if (!acquired) {
                log.debug("Failed to acquire lock. Start spin-wait. id={}", id);

                long deadline = System.currentTimeMillis() + LOCK_LEASE_MS;
                while (System.currentTimeMillis() < deadline) {
                    cached = cache.get(id);
                    if (cached != null) {
                        log.debug("Cache filled during spin. id={}", id);
                        return cached;
                    }

                    try {
                        Thread.sleep(SPIN_WAIT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while spin-waiting. id=" + id, e);
                    }
                }

                log.warn("Spin-wait timeout. Fallback DB. id={}", id);
                return loadAndCache(id, cache);
            }

            cached = cache.get(id);
            if (cached != null) {
                log.debug("Cache hit after lock. id={}", id);
                return cached;
            }

            return loadAndCache(id, cache);

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

    protected ItemResponse loadAndCache(Long id, RMapCache<Long, ItemResponse> cache) {
        log.debug("Loading from DB. id={}", id);

        Item item = itemReadService.getItemOrThrow(id);

        ItemResponse response = ItemResponse.from(item);

        cache.put(id, response, TTL_MS, TimeUnit.MILLISECONDS);
        log.debug("Cache put. id={}, ttlMs={}", id, TTL_MS);

        return response;
    }
}
