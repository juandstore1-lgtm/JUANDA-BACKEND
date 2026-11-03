package com.jdqstore.backend.service;

import com.jdqstore.backend.dto.CategoryDTO;
import com.jdqstore.backend.entity.Category;
import com.jdqstore.backend.repository.CategoryRepository;
import com.jdqstore.backend.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository repository;
    private final SedeRepository sedeRepository;
    private final R2StorageService storageService;

    public List<CategoryDTO> findAll(Long storeId) {
        List<Category> categories = (storeId != null) 
            ? repository.findByStoreId(storeId) 
            : repository.findAll();
        return categories.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<CategoryDTO> findAllWithoutImages(Long storeId) {
        List<CategoryRepository.CategorySummary> categories = (storeId != null)
            ? repository.findSummariesByStoreId(storeId)
            : repository.findAllSummaries();
        return categories.stream().map(c -> {
            CategoryDTO dto = new CategoryDTO();
            dto.setId(c.getId());
            dto.setName(c.getName());
            dto.setStoreId(c.getStoreId());
            return dto;
        }).collect(Collectors.toList());
    }

    public CategoryDTO findById(Long id) {
        return repository.findById(id).map(this::mapToDTO).orElseThrow();
    }

    public CategoryDTO create(CategoryDTO dto) {
        Category entity = mapToEntity(dto, new Category());
        return mapToDTO(repository.save(entity));
    }

    public CategoryDTO update(Long id, CategoryDTO dto) {
        Category entity = repository.findById(id).orElseThrow();
        entity = mapToEntity(dto, entity);
        return mapToDTO(repository.save(entity));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private CategoryDTO mapToDTO(Category entity) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        String effectiveImage = entity.getEffectiveImageUrl();
        dto.setImage(effectiveImage);
        dto.setImageUrl(effectiveImage);
        if (entity.getStore() != null) dto.setStoreId(entity.getStore().getId());
        return dto;
    }

    private Category mapToEntity(CategoryDTO dto, Category entity) {
        entity.setName(dto.getName());

        String inputImage = dto.getImageUrl() != null && !dto.getImageUrl().trim().isEmpty()
                ? dto.getImageUrl().trim()
                : (dto.getImage() != null ? dto.getImage().trim() : null);

        if (inputImage != null) {
            if (inputImage.startsWith("http://") || inputImage.startsWith("https://")) {
                entity.setImageUrl(inputImage);
            } else if (storageService.isBase64Image(inputImage)) {
                try {
                    String idStr = entity.getId() != null ? String.valueOf(entity.getId()) : null;
                    String r2Url = storageService.uploadBase64(inputImage, "categories", idStr);
                    entity.setImageUrl(r2Url);
                } catch (Exception e) {
                    log.warn("Could not auto-upload category base64 to R2, keeping in image column: {}", e.getMessage());
                    entity.setImage(inputImage);
                }
            } else {
                entity.setImage(inputImage);
            }
        }

        if (dto.getStoreId() != null) {
            entity.setStore(sedeRepository.findById(dto.getStoreId()).orElse(null));
        } else {
            entity.setStore(null);
        }
        return entity;
    }
}
