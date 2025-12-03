package com.example.cache.item.service;

import com.example.cache.item.controller.dto.ItemResponse;
import com.example.cache.item.domain.Item;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CachedItemService {

    private static final Logger log = LoggerFactory.getLogger(CachedItemService.class);

    private static final String CACHE_KEY_PREFIX = "itemCache:";
    private static final long TTL_S = 120L;
    private static final long SOFT_TTL_S = 80L;
    private static final double PER_ALPHA = 1.5;

    private final RedissonClient redissonClient;
    private final ItemReadService itemReadService;

    public ItemResponse getItem(Long id) {
        String key = buildKey(id);
        RBucket<ItemResponse> bucket = redissonClient.getBucket(key);

        ItemResponse cached = bucket.get();
        if (cached == null) {
            log.debug("Cache miss. id={}", id);
            return loadAndCache(id, bucket);
        }

        long ttlMs = bucket.remainTimeToLive();
        if (ttlMs <= 0) {
            log.debug("Cache hit but TTL unknown/expired. Force reload. id={}", id);
            return loadAndCache(id, bucket);
        }

        long ttlLeftS = TimeUnit.MILLISECONDS.toSeconds(ttlMs);
        long ageS = TTL_S - ttlLeftS;

        if (ageS <= SOFT_TTL_S) {
            log.debug("Cache hit (fresh). id={}, ageS={}, ttlLeftS={}", id, ageS, ttlLeftS);
            return cached;
        }

        double p = computePerProbability(ageS);
        double r = ThreadLocalRandom.current().nextDouble();

        if (r < p) {
            log.debug(
                    "Cache hit (PER revalidate). id={}, ageS={}, ttlLeftS={}, p={}, r={}",
                    id, ageS, ttlLeftS, p, r
            );
            return loadAndCache(id, bucket);
        }

        log.debug(
                "Cache hit (PER keep). id={}, ageS={}, ttlLeftS={}, p={}, r={}",
                id, ageS, ttlLeftS, p, r
        );
        return cached;
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

    /**
     * Log-based PER 확률 함수
     *
     * p(t) = log(1 + α · (t − softTTL)) / log(1 + a · (TTL − softTTL))
     *   - softTTL ≤ t ≤ TTL 범위에서 0 → 1로 증가
     */
    private double computePerProbability(long ageS) {
        if (ageS <= SOFT_TTL_S) {
            return 0.0;
        }
        if (ageS >= TTL_S) {
            return 1.0;
        }

        double x = ageS - SOFT_TTL_S;
        double window = TTL_S - SOFT_TTL_S;

        double numerator = Math.log(1.0 + PER_ALPHA * x);
        double denominator = Math.log(1.0 + PER_ALPHA * window);

        double p = numerator / denominator;

        if (p < 0.0) return 0.0;
        return Math.min(p, 1.0);
    }
}
