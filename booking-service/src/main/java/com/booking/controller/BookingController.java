package com.booking.controller;

import com.booking.client.ResourceClient;
import com.booking.dto.BookingDtos.*;
import com.booking.model.Booking;
import com.booking.repository.BookingRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private final BookingRepository repository;
    private final ResourceClient resourceClient;

    public BookingController(BookingRepository repository, ResourceClient resourceClient) {
        this.repository = repository;
        this.resourceClient = resourceClient;
    }

    @GetMapping
    public List<BookingResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(b -> ResponseEntity.ok(toResponse(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public List<BookingResponse> getByUser(@PathVariable Long userId) {
        return repository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody BookingRequest req) {
        log.info("event=booking_requested userId={} resourceId={}", req.userId(), req.resourceId());

        boolean available = resourceClient.isResourceAvailable(req.resourceId());
        if (!available) {
            log.warn("event=booking_rejected reason=resource_unavailable userId={} resourceId={}",
                    req.userId(), req.resourceId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Resource is not available for booking");
        }

        Booking booking = new Booking(req.userId(), req.resourceId(), req.startTime(), req.endTime(), "CONFIRMED");
        repository.save(booking);
        log.info("event=booking_confirmed bookingId={} userId={} resourceId={}",
                booking.getId(), booking.getUserId(), booking.getResourceId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(booking));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancel(@PathVariable Long id) {
        return repository.findById(id).map(b -> {
            b.setStatus("CANCELLED");
            repository.save(b);
            log.info("event=booking_cancelled bookingId={}", id);
            return ResponseEntity.ok(toResponse(b));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        log.info("event=booking_deleted bookingId={}", id);
        return ResponseEntity.noContent().build();
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(b.getId(), b.getUserId(), b.getResourceId(), b.getStartTime(), b.getEndTime(), b.getStatus());
    }
}
