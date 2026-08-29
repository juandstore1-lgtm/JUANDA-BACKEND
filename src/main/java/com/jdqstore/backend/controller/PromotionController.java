package com.jdqstore.backend.controller;
import com.jdqstore.backend.dto.PromotionDTO;
import com.jdqstore.backend.service.PromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController {
    private final PromotionService service;
    @GetMapping
    public ResponseEntity<List<PromotionDTO>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<PromotionDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
    @PostMapping
    public ResponseEntity<PromotionDTO> create(@RequestBody PromotionDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }
    @PutMapping("/{id}")
    public ResponseEntity<PromotionDTO> update(@PathVariable Long id, @RequestBody PromotionDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
