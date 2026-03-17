package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.model.StorageType;
import com.smartfinance.dashboard.service.StorageTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storage-types")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StorageTypeController {

    private final StorageTypeService storageTypeService;

    @GetMapping
    public ResponseEntity<List<StorageType>> getAll() {
        return ResponseEntity.ok(storageTypeService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody StorageType storageType) {
        try {
            return ResponseEntity.ok(storageTypeService.create(storageType));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody StorageType storageType) {
        try {
            return ResponseEntity.ok(storageTypeService.update(id, storageType));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            storageTypeService.delete(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
