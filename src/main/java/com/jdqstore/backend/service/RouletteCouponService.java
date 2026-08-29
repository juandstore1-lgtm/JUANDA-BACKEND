package com.jdqstore.backend.service;
import com.jdqstore.backend.dto.RouletteCouponDTO;
import com.jdqstore.backend.entity.RouletteCoupon;
import com.jdqstore.backend.repository.RouletteCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouletteCouponService {
    private final RouletteCouponRepository repository;


    public List<RouletteCouponDTO> findAll() {
        return repository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    public RouletteCouponDTO findById(Long id) {
        return repository.findById(id).map(this::mapToDTO).orElseThrow();
    }
    public RouletteCouponDTO create(RouletteCouponDTO dto) {
        RouletteCoupon entity = mapToEntity(dto, new RouletteCoupon());
        return mapToDTO(repository.save(entity));
    }
    public RouletteCouponDTO update(Long id, RouletteCouponDTO dto) {
        RouletteCoupon entity = repository.findById(id).orElseThrow();
        entity = mapToEntity(dto, entity);
        return mapToDTO(repository.save(entity));
    }
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private RouletteCouponDTO mapToDTO(RouletteCoupon entity) {
        RouletteCouponDTO dto = new RouletteCouponDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setDiscountPercentage(entity.getDiscountPercentage());
        dto.setExpiresAt(entity.getExpiresAt());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }
    private RouletteCoupon mapToEntity(RouletteCouponDTO dto, RouletteCoupon entity) {
        entity.setCode(dto.getCode());
        entity.setDiscountPercentage(dto.getDiscountPercentage());
        entity.setExpiresAt(dto.getExpiresAt());
        entity.setIsActive(dto.getIsActive());
        return entity;
    }
}
