package com.hotelio.history.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelio.history.db.BookingHistoryEntity;
import com.hotelio.history.db.BookingHistoryRepository;
import com.hotelio.history.events.BookingCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BookingEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final BookingHistoryRepository repo;

    public BookingEventsConsumer(ObjectMapper objectMapper, BookingHistoryRepository repo) {
        this.objectMapper = objectMapper;
        this.repo = repo;
    }

    @KafkaListener(topics = "${booking.kafka.topic:booking-events}")
    public void onMessage(String message) {
        try {
            BookingCreatedEvent evt = objectMapper.readValue(message, BookingCreatedEvent.class);

            // простая защита, чтобы не сохранять мусор
            if (evt == null || evt.bookingId() == null || evt.bookingId().isBlank()) {
                log.warn("Skip invalid event: {}", message);
                return;
            }

            BookingHistoryEntity e = new BookingHistoryEntity();
            e.setEventType(evt.eventType());
            e.setEventVersion(evt.eventVersion());
            e.setBookingId(evt.bookingId());
            e.setUserId(evt.userId());
            e.setHotelId(evt.hotelId());
            e.setPromoCode(evt.promoCode() == null || evt.promoCode().isBlank() ? null : evt.promoCode());
            e.setDiscountPercent(evt.discountPercent());
            e.setPrice(evt.price());

            // createdAt в событии ISO строка
            try {
                e.setCreatedAt(Instant.parse(evt.createdAt()));
            } catch (Exception parseIgnored) {
                e.setCreatedAt(null);
            }

            repo.save(e);
            log.info("Saved booking history event bookingId={} userId={} hotelId={}",
                    evt.bookingId(), evt.userId(), evt.hotelId());
        } catch (Exception ex) {
            log.error("Failed to process message: {}", message, ex);
        }
    }
}
