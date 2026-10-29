package com.jdqstore.backend.controller;
import com.jdqstore.backend.dto.ProductDTO;
import com.jdqstore.backend.security.SedeAccessService;
import com.jdqstore.backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;
    // Las rutas de escritura exigen GLOBAL_ADMIN o STORE_ADMIN (SecurityConfig); aquí se limita STORE_ADMIN a sus sedes
    private final SedeAccessService sedeAccess;
    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
    @PostMapping
    public ResponseEntity<ProductDTO> create(@RequestBody ProductDTO dto) {
        sedeAccess.checkProductCreate(dto.getStoreIds());
        return ResponseEntity.ok(service.create(dto));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> update(@PathVariable Long id, @RequestBody ProductDTO dto) {
        sedeAccess.checkProductUpdate(id, dto.getStoreIds());
        return ResponseEntity.ok(service.update(id, dto));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sedeAccess.checkProductDelete(id);
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
