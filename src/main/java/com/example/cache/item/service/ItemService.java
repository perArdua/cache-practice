package com.example.cache.item.service;

import com.example.cache.item.controller.dto.ItemResponse;
import com.example.cache.item.domain.Item;
import com.example.cache.item.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional(readOnly = true)
    public ItemResponse getItem(Long id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found. id=" + id));

        return ItemResponse.from(item);
    }
}
