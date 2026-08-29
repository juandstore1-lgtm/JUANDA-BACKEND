package com.jdqstore.backend.service;
import com.jdqstore.backend.dto.CategoryDTO;
import com.jdqstore.backend.entity.Category;
import com.jdqstore.backend.repository.CategoryRepository;
import com.jdqstore.backend.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository repository;
    private final SedeRepository sedeRepository;

    public List<CategoryDTO> findAll(Long storeId) {
        List<Category> categories = (storeId != null) 
            ? repository.findByStoreId(storeId) 
            : repository.findAll();
        return categories.stream().map(this::mapToDTO).collect(Collectors.toList());
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
        dto.setImage(entity.getImage());
        if (entity.getStore() != null) dto.setStoreId(entity.getStore().getId());
        return dto;
    }

    private Category mapToEntity(CategoryDTO dto, Category entity) {
        entity.setName(dto.getName());
        entity.setImage(dto.getImage());
        if (dto.getStoreId() != null) {
            entity.setStore(sedeRepository.findById(dto.getStoreId()).orElse(null));
        } else {
            entity.setStore(null);
        }
        return entity;
    }
}
