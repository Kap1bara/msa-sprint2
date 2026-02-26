package com.hotelio.history.events;

public record BookingCreatedEvent(
        String eventType,
        int eventVersion,
        String bookingId,
        String userId,
        String hotelId,
        String promoCode,
        double discountPercent,
        double price,
        String createdAt
) {}
