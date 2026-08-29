package com.jdqstore.backend.controller;
import com.jdqstore.backend.dto.RouletteCouponDTO;
import com.jdqstore.backend.service.RouletteCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/roulettecoupons")
@RequiredArgsConstructor
public class RouletteCouponController {
    private final RouletteCouponService service;
    @GetMapping
    public ResponseEntity<List<RouletteCouponDTO>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }
    @GetMapping("/{id}")
    public ResponseEntity<RouletteCouponDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
    @PostMapping
    public ResponseEntity<RouletteCouponDTO> create(@RequestBody RouletteCouponDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }
    @PutMapping("/{id}")
    public ResponseEntity<RouletteCouponDTO> update(@PathVariable Long id, @RequestBody RouletteCouponDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
