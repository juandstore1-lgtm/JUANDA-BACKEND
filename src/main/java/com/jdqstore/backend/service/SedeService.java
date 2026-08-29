package com.jdqstore.backend.service;
import com.jdqstore.backend.dto.SedeDTO;
import com.jdqstore.backend.entity.Sede;
import com.jdqstore.backend.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SedeService {
    private final SedeRepository repository;


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
        dto.setPhone(entity.getPhone());
        dto.setSchedule(entity.getSchedule());
        dto.setImage(entity.getImage());
        dto.setDescription(entity.getDescription());
        return dto;
    }
    private Sede mapToEntity(SedeDTO dto, Sede entity) {
        entity.setName(dto.getName());
        entity.setAddress(dto.getAddress());
        entity.setPhone(dto.getPhone());
        entity.setSchedule(dto.getSchedule());
        entity.setImage(dto.getImage());
        entity.setDescription(dto.getDescription());
        return entity;
    }
}
