package com.hotelio.booking.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ReviewClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ReviewClient(RestTemplate restTemplate,
                        @Value("${monolith.baseUrl}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public boolean isTrustedHotel(String hotelId) {
        String url = baseUrl + "/api/reviews/hotel/" + hotelId + "/trusted";
        Boolean resp = restTemplate.getForObject(url, Boolean.class);
        return resp != null && resp;
    }
}
