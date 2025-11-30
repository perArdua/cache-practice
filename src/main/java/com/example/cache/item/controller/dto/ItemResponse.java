package com.example.cache.item.controller.dto;

import com.example.cache.item.domain.Item;

public record ItemResponse(
        Long id,
        String name,
        int price,
        int stock
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getPrice(),
                item.getStock()
        );
    }
}
