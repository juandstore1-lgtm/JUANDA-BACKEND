package com.jdqstore.backend.controller;
import com.jdqstore.backend.dto.SedeDTO;
import com.jdqstore.backend.service.SedeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/sedes")
@RequiredArgsConstructor
public class SedeController {
    private final SedeService service;
    @GetMapping
    public ResponseEntity<List<SedeDTO>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<SedeDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
    @PostMapping
    public ResponseEntity<SedeDTO> create(@RequestBody SedeDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }
    @PutMapping("/{id}")
    public ResponseEntity<SedeDTO> update(@PathVariable Long id, @RequestBody SedeDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
