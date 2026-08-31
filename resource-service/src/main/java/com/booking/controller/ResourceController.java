package com.booking.controller;

import com.booking.dto.ResourceDtos.*;
import com.booking.model.Resource;
import com.booking.repository.ResourceRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private static final Logger log = LoggerFactory.getLogger(ResourceController.class);
    private final ResourceRepository repository;

    public ResourceController(ResourceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ResourceResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(r -> ResponseEntity.ok(toResponse(r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Called by booking-service (via the gateway or directly) to check availability
    // before confirming a booking. Kept as a simple, fast, read-only endpoint so
    // it is a good candidate for the circuit breaker on the calling side.
    @GetMapping("/{id}/availability")
    public ResponseEntity<Boolean> checkAvailability(@PathVariable Long id) {
        return repository.findById(id)
                .map(r -> ResponseEntity.ok(r.isAvailable()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody ResourceRequest req) {
        Resource resource = new Resource(req.name(), req.type(), req.location(), req.capacity(), true);
        repository.save(resource);
        log.info("event=resource_created resourceId={} name={} type={}", resource.getId(), resource.getName(), resource.getType());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(resource));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResourceResponse> update(@PathVariable Long id, @Valid @RequestBody ResourceRequest req) {
        return repository.findById(id).map(r -> {
            r.setName(req.name());
            r.setType(req.type());
            r.setLocation(req.location());
            r.setCapacity(req.capacity());
            repository.save(r);
            log.info("event=resource_updated resourceId={}", id);
            return ResponseEntity.ok(toResponse(r));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<ResourceResponse> setAvailability(@PathVariable Long id, @RequestParam boolean available) {
        return repository.findById(id).map(r -> {
            r.setAvailable(available);
            repository.save(r);
            log.info("event=resource_availability_changed resourceId={} available={}", id, available);
            return ResponseEntity.ok(toResponse(r));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        log.info("event=resource_deleted resourceId={}", id);
        return ResponseEntity.noContent().build();
    }

    private ResourceResponse toResponse(Resource r) {
        return new ResourceResponse(r.getId(), r.getName(), r.getType(), r.getLocation(), r.getCapacity(), r.isAvailable());
    }
}
