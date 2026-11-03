package com.jdqstore.backend.service;

import com.jdqstore.backend.dto.SedeDTO;
import com.jdqstore.backend.entity.Sede;
import com.jdqstore.backend.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SedeService {
    private final SedeRepository repository;
    private final R2StorageService storageService;

    public List<SedeDTO> findAll() {
        return repository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public SedeDTO findById(Long id) {
        return repository.findById(id).map(this::mapToDTO).orElseThrow();
    }

    public SedeDTO create(SedeDTO dto) {
        Sede entity = mapToEntity(dto, new Sede());
        return mapToDTO(repository.save(entity));
    }

    public SedeDTO update(Long id, SedeDTO dto) {
        Sede entity = repository.findById(id).orElseThrow();
        entity = mapToEntity(dto, entity);
        return mapToDTO(repository.save(entity));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private SedeDTO mapToDTO(Sede entity) {
        SedeDTO dto = new SedeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setAddress(entity.getAddress());
        dto.setBarrio(entity.getBarrio());
        dto.setPhone(entity.getPhone());
        dto.setSchedule(entity.getSchedule());
        String effectiveImage = entity.getEffectiveImageUrl();
        dto.setImage(effectiveImage);
        dto.setImageUrl(effectiveImage);
        dto.setDescription(entity.getDescription());
        return dto;
    }

    private Sede mapToEntity(SedeDTO dto, Sede entity) {
        entity.setName(dto.getName());
        entity.setAddress(dto.getAddress());
        entity.setBarrio(dto.getBarrio());
        entity.setPhone(dto.getPhone());
        entity.setSchedule(dto.getSchedule());
        entity.setDescription(dto.getDescription());

        String inputImage = dto.getImageUrl() != null && !dto.getImageUrl().trim().isEmpty()
                ? dto.getImageUrl().trim()
                : (dto.getImage() != null ? dto.getImage().trim() : null);

        if (inputImage != null) {
            if (inputImage.startsWith("http://") || inputImage.startsWith("https://")) {
                entity.setImageUrl(inputImage);
            } else if (storageService.isBase64Image(inputImage)) {
                try {
                    String idStr = entity.getId() != null ? String.valueOf(entity.getId()) : null;
                    String r2Url = storageService.uploadBase64(inputImage, "sedes", idStr);
                    entity.setImageUrl(r2Url);
                } catch (Exception e) {
                    log.warn("Could not auto-upload sede base64 to R2, keeping in image column: {}", e.getMessage());
                    entity.setImage(inputImage);
                }
            } else {
                entity.setImage(inputImage);
            }
        }

        return entity;
    }
}
