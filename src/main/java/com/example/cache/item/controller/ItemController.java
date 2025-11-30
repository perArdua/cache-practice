package com.example.cache.item.controller;

import com.example.cache.item.controller.dto.ItemResponse;
import com.example.cache.item.service.CachedItemService;
import com.example.cache.item.service.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;
    private final CachedItemService cachedItemService;

    @GetMapping("/{id}")
    public ItemResponse getItemWithCache(@PathVariable Long id) {
        return cachedItemService.getItem(id);
    }

    @GetMapping("/{id}/db")
    public ItemResponse getItemFromDb(@PathVariable Long id) {
        return itemService.getItem(id);
    }
}
