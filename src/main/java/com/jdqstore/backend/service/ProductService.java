package com.jdqstore.backend.service;

import com.jdqstore.backend.dto.ProductDTO;
import com.jdqstore.backend.entity.Product;
import com.jdqstore.backend.repository.ProductRepository;
import com.jdqstore.backend.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository repository;
    private final SedeRepository sedeRepository;
    private final R2StorageService storageService;

    public List<ProductDTO> findAll() {
        return repository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    public ProductDTO findById(Long id) {
        return repository.findById(id).map(this::mapToDTO).orElseThrow();
    }
    public ProductDTO create(ProductDTO dto) {
        Product entity = mapToEntity(dto, new Product());
        return mapToDTO(repository.save(entity));
    }
    public ProductDTO update(Long id, ProductDTO dto) {
        Product entity = repository.findById(id).orElseThrow();
        entity = mapToEntity(dto, entity);
        return mapToDTO(repository.save(entity));
    }
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private ProductDTO mapToDTO(Product entity) {
        ProductDTO dto = new ProductDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setPrice(entity.getPrice());
        dto.setOldPrice(entity.getOldPrice());
        dto.setDiscountPercentage(entity.getDiscountPercentage());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setSizes(copyOf(entity.getSizes()));
        dto.setColors(copyOf(entity.getColors()));
        dto.setImages(copyOf(entity.getImages()));
        dto.setStatus(entity.getStatus());
        dto.setTags(copyOf(entity.getTags()));
        dto.setOrder(entity.getOrder());
        if (entity.getStores() != null) dto.setStoreIds(entity.getStores().stream().map(s -> s.getId()).toList());
        return dto;
    }
    // Copia la coleccion en el servicio para que no se cargue de forma perezosa durante la serializacion JSON
    private static List<String> copyOf(List<String> values) {
        return values == null ? null : new ArrayList<>(values);
    }
    private Product mapToEntity(ProductDTO dto, Product entity) {
        entity.setName(dto.getName());
        entity.setPrice(dto.getPrice());
        entity.setOldPrice(dto.getOldPrice());
        entity.setDiscountPercentage(dto.getDiscountPercentage());
        entity.setDescription(dto.getDescription());
        entity.setCategory(dto.getCategory());
        entity.setSizes(dto.getSizes());
        entity.setColors(dto.getColors());

        if (dto.getImages() != null) {
            List<String> processedImages = new ArrayList<>();
            for (String img : dto.getImages()) {
                if (img != null && !img.trim().isEmpty()) {
                    if (img.startsWith("http://") || img.startsWith("https://")) {
                        processedImages.add(img.trim());
                    } else if (storageService.isBase64Image(img)) {
                        try {
                            String idStr = entity.getId() != null ? String.valueOf(entity.getId()) : null;
                            String r2Url = storageService.uploadBase64(img, "products", idStr);
                            processedImages.add(r2Url);
                        } catch (Exception e) {
                            log.warn("Could not auto-upload product image base64 to R2: {}", e.getMessage());
                            processedImages.add(img);
                        }
                    } else {
                        processedImages.add(img.trim());
                    }
                }
            }
            entity.setImages(processedImages);
        } else {
            entity.setImages(new ArrayList<>());
        }

        entity.setStatus(dto.getStatus());
        entity.setTags(dto.getTags());
        entity.setOrder(dto.getOrder());
        if (dto.getStoreIds() != null) {
            entity.setStores(sedeRepository.findAllById(dto.getStoreIds()));
        }
        return entity;
    }
}
