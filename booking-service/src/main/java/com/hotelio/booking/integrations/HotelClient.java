package com.hotelio.booking.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HotelClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public HotelClient(RestTemplate restTemplate,
                       @Value("${monolith.baseUrl}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public boolean isOperational(String hotelId) {
        String url = baseUrl + "/api/hotels/" + hotelId + "/operational";
        Boolean resp = restTemplate.getForObject(url, Boolean.class);
        return resp != null && resp;
    }

    public boolean isFullyBooked(String hotelId) {
        String url = baseUrl + "/api/hotels/" + hotelId + "/fully-booked";
        Boolean resp = restTemplate.getForObject(url, Boolean.class);
        return resp != null && resp;
    }
}
