package com.example.cache.item.service;

import com.example.cache.item.controller.dto.ItemResponse;

public record ItemCacheEntry(
        ItemResponse value,
        long logicalExpireAt
) {}
