package com.jdqstore.backend.controller;

import com.jdqstore.backend.dto.RaffleDTO;
import com.jdqstore.backend.service.RaffleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleService raffleService;

    @GetMapping
    public ResponseEntity<List<RaffleDTO>> getAll() {
        return ResponseEntity.ok(raffleService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RaffleDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(raffleService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN')")
    public ResponseEntity<RaffleDTO> create(@RequestBody RaffleDTO dto) {
        return ResponseEntity.ok(raffleService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN')")
    public ResponseEntity<RaffleDTO> update(@PathVariable Long id, @RequestBody RaffleDTO dto) {
        return ResponseEntity.ok(raffleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        raffleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
