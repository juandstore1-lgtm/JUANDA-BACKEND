package com.jdqstore.backend.controller;
import com.jdqstore.backend.dto.UserDTO;
import com.jdqstore.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;

    @GetMapping
    public ResponseEntity<List<UserDTO>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<UserDTO> create(@RequestBody UserDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> update(@PathVariable Long id, @RequestBody UserDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getMe() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(service.findByEmail(email));
    }

}
