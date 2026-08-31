package com.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class BookingDtos {

    public record BookingRequest(
            @NotNull Long userId,
            @NotNull Long resourceId,
            @NotNull @Future LocalDateTime startTime,
            @NotNull @Future LocalDateTime endTime
    ) {}

    public record BookingResponse(
            Long id,
            Long userId,
            Long resourceId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status
    ) {}
}
