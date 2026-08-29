package com.jdqstore.backend.service;
import com.jdqstore.backend.dto.UserDTO;
import com.jdqstore.backend.entity.User;
import com.jdqstore.backend.repository.UserRepository;
import com.jdqstore.backend.repository.RoleRepository;
import com.jdqstore.backend.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final SedeRepository sedeRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserDTO> findAll() {
        return repository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public UserDTO findById(Long id) {
        return repository.findById(id).map(this::mapToDTO).orElseThrow();
    }

    public UserDTO create(UserDTO dto) {
        User entity = mapToEntity(dto, new User());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        return mapToDTO(repository.save(entity));
    }

    public UserDTO update(Long id, UserDTO dto) {
        User entity = repository.findById(id).orElseThrow();
        entity = mapToEntity(dto, entity);
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            entity.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        return mapToDTO(repository.save(entity));
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    
    public UserDTO findByEmail(String email) {
        return repository.findByEmail(email).map(this::mapToDTO).orElseThrow();
    }

private UserDTO mapToDTO(User entity) {
        UserDTO dto = new UserDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        dto.setIsActive(entity.getIsActive());
        if (entity.getRole() != null) dto.setRoleName(entity.getRole().getName());
        if (entity.getStores() != null) {
            dto.setStoreIds(entity.getStores().stream().map(s -> s.getId()).collect(Collectors.toList()));
        }
        return dto;
    }

    private User mapToEntity(UserDTO dto, User entity) {
        entity.setName(dto.getName());
        entity.setEmail(dto.getEmail());
        entity.setIsActive(dto.getIsActive());
        if (dto.getRoleName() != null) {
            entity.setRole(roleRepository.findByName(dto.getRoleName()).orElse(null));
        }
        if (dto.getStoreIds() != null) {
            entity.setStores(sedeRepository.findAllById(dto.getStoreIds()));
        }
        return entity;
    }
}
