package com.hotelio.booking.grpc;

import com.hotelio.booking.db.BookingEntity;
import com.hotelio.booking.db.BookingRepository;
import com.hotelio.proto.booking.BookingListRequest;
import com.hotelio.proto.booking.BookingListResponse;
import com.hotelio.proto.booking.BookingRequest;
import com.hotelio.proto.booking.BookingResponse;
import com.hotelio.proto.booking.BookingServiceGrpc;
import com.hotelio.booking.integrations.HotelClient;
import com.hotelio.booking.integrations.UserClient;
import com.hotelio.booking.integrations.ReviewClient;
import com.hotelio.booking.integrations.PromoClient;
import com.hotelio.booking.events.BookingCreatedEvent;
import com.hotelio.booking.events.BookingEventPublisher;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class BookingGrpcService extends BookingServiceGrpc.BookingServiceImplBase {

    private final BookingRepository bookingRepository;
    private static final Logger log = LoggerFactory.getLogger(BookingGrpcService.class);
    private final UserClient userClient;
    private final ReviewClient reviewClient;
    private final HotelClient hotelClient;
    private final PromoClient promoClient;
    private final BookingEventPublisher eventPublisher;

    public BookingGrpcService(BookingRepository bookingRepository,
            UserClient userClient,
            ReviewClient reviewClient,
            HotelClient hotelClient,
            PromoClient promoClient,
            BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.userClient = userClient;
        this.reviewClient = reviewClient;
        this.hotelClient = hotelClient;
        this.promoClient = promoClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void createBooking(BookingRequest request, StreamObserver<BookingResponse> responseObserver) {
        try {
            log.info("Creating booking: userId={}, hotelId={}, promoCode={}", request.getUserId(), request.getHotelId(),
                    request.getPromoCode());

            validateUser(request.getUserId());
            validateHotel(request.getHotelId());

            double discount = resolvePromoDiscount(request.getPromoCode(), request.getUserId());
            double basePrice = resolveBasePrice(request.getUserId());
            double finalPrice = basePrice - discount;

            String promo = request.getPromoCode();

            log.info("Creating entry");
            BookingEntity entity = new BookingEntity();
            entity.setUserId(request.getUserId());
            entity.setHotelId(request.getHotelId());
            entity.setPromoCode(promo == null || promo.isBlank() ? null : promo);
            entity.setDiscountPercent((int) Math.round(discount)); // в entity int
            entity.setPrice((int) Math.round(finalPrice));
            entity.setCreatedAt(Instant.now());

            BookingEntity saved = bookingRepository.save(entity);
            log.info("Saved, Creating event");
            try{
            BookingCreatedEvent event = new BookingCreatedEvent(
                    "BOOKING_CREATED",
                    String.valueOf(saved.getId()),
                    saved.getUserId(),
                    saved.getHotelId(),
                    saved.getPromoCode(),
                    discount,
                    finalPrice,
                    saved.getCreatedAt());
            eventPublisher.publishBookingCreated(event);
            log.info("Published event for booking ID: {}", saved.getId());
            } catch (Exception e) {
                log.error("Failed to publish booking event for booking ID: {}", saved.getId(), e);
            }

            BookingResponse response = BookingResponse.newBuilder()
                    .setId(String.valueOf(saved.getId()))
                    .setUserId(saved.getUserId())
                    .setHotelId(saved.getHotelId())
                    .setPromoCode(saved.getPromoCode() == null ? "" : saved.getPromoCode())
                    .setDiscountPercent(discount)
                    .setPrice(finalPrice)
                    .setCreatedAt(saved.getCreatedAt().toString())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Failed to create booking")
                            .withCause(e)
                            .asRuntimeException());
        }
    }

    @Override
    public void listBookings(BookingListRequest request, StreamObserver<BookingListResponse> responseObserver) {
        try {
            log.info("ListBookings userId={}", request.getUserId());
            List<BookingEntity> list;
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                list = bookingRepository.findAll();
            } else {
                list = bookingRepository.findAllByUserId(request.getUserId());
            }
            BookingListResponse.Builder resp = BookingListResponse.newBuilder();
            for (BookingEntity e : list) {
                resp.addBookings(
                        BookingResponse.newBuilder()
                                .setId(String.valueOf(e.getId()))
                                .setUserId(e.getUserId())
                                .setHotelId(e.getHotelId())
                                .setPromoCode(e.getPromoCode() == null ? "" : e.getPromoCode())
                                .setDiscountPercent((double) e.getDiscountPercent())
                                .setPrice((double) e.getPrice())
                                .setCreatedAt(e.getCreatedAt() == null ? "" : e.getCreatedAt().toString())
                                .build());
            }

            responseObserver.onNext(resp.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Failed to list bookings")
                            .withCause(e)
                            .asRuntimeException());
        }
    }

    private void validateUser(String userId) {
        if (!userClient.isActive(userId)) {
            log.warn("User {} is inactive", userId);
            throw new IllegalArgumentException("User is inactive");
        }
        if (userClient.isBlacklisted(userId)) {
            log.warn("User {} is blacklisted", userId);
            throw new IllegalArgumentException("User is blacklisted");
        }
    }

    private void validateHotel(String hotelId) {
        if (!hotelClient.isOperational(hotelId)) {
            log.warn("Hotel {} is not operational", hotelId);
            throw new IllegalArgumentException("Hotel is not operational");
        }
        if (!reviewClient.isTrustedHotel(hotelId)) {
            log.warn("Hotel {} is not trusted", hotelId);
            throw new IllegalArgumentException("Hotel is not trusted based on reviews");
        }
        if (hotelClient.isFullyBooked(hotelId)) {
            log.warn("Hotel {} is fully booked", hotelId);
            throw new IllegalArgumentException("Hotel is fully booked");
        }
    }

    private double resolveBasePrice(String userId) {
        boolean vip = userClient.isVip(userId);
        double base = vip ? 80.0 : 100.0;
        log.debug("User {} vip={}, basePrice={}", userId, vip, base);
        return base;
    }

    private double resolvePromoDiscount(String promoCode, String userId) {
        double discount = promoClient.resolveDiscountPercent(promoCode, userId);
        log.debug("Promo code '{}' for user {} gives discount {}", promoCode, userId, discount);
        return discount;
    }
}
