package com.jdqstore.backend.service;
import com.jdqstore.backend.dto.PromotionDTO;
import com.jdqstore.backend.entity.Promotion;
import com.jdqstore.backend.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionService {
    private final PromotionRepository repository;


    public List<PromotionDTO> findAll() {
        return repository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    public PromotionDTO findById(Long id) {
        return repository.findById(id).map(this::mapToDTO).orElseThrow();
    }
    public PromotionDTO create(PromotionDTO dto) {
        Promotion entity = mapToEntity(dto, new Promotion());
        return mapToDTO(repository.save(entity));
    }
    public PromotionDTO update(Long id, PromotionDTO dto) {
        Promotion entity = repository.findById(id).orElseThrow();
        entity = mapToEntity(dto, entity);
        return mapToDTO(repository.save(entity));
    }
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private PromotionDTO mapToDTO(Promotion entity) {
        PromotionDTO dto = new PromotionDTO();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDiscountPercentage(entity.getDiscountPercentage());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }
    private Promotion mapToEntity(PromotionDTO dto, Promotion entity) {
        entity.setTitle(dto.getTitle());
        entity.setDiscountPercentage(dto.getDiscountPercentage());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setIsActive(dto.getIsActive());
        return entity;
    }
}
