package com.jdqstore.backend.controller;
import com.jdqstore.backend.dto.CategoryDTO;
import com.jdqstore.backend.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService service;

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getAll(@RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(service.findAll(storeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> create(@RequestBody CategoryDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> update(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
