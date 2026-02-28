package com.hotelio.booking.events;

import java.time.Instant;

public record BookingCreatedEvent(
        String eventType,
        String bookingId,
        String userId,
        String hotelId,
        String promoCode,
        double discountPercent,
        double price,
        Instant createdAt
) {}
