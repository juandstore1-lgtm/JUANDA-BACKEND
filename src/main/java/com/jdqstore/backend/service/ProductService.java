package com.jdqstore.backend.service;
import com.jdqstore.backend.dto.ProductDTO;
import com.jdqstore.backend.entity.Product;
import com.jdqstore.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;
    private final com.jdqstore.backend.repository.SedeRepository sedeRepository;


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
        dto.setSizes(entity.getSizes());
        dto.setColors(entity.getColors());
        dto.setImages(entity.getImages());
        dto.setStatus(entity.getStatus());
        dto.setTags(entity.getTags());
        dto.setOrder(entity.getOrder());
        if (entity.getStores() != null) dto.setStoreIds(entity.getStores().stream().map(s -> s.getId()).toList());
        return dto;
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
        entity.setImages(dto.getImages());
        entity.setStatus(dto.getStatus());
        entity.setTags(dto.getTags());
        entity.setOrder(dto.getOrder());
        if (dto.getStoreIds() != null) {
            entity.setStores(sedeRepository.findAllById(dto.getStoreIds()));
        }
        return entity;
    }
}
