package com.hotelio.booking.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class PromoClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PromoClient(RestTemplate restTemplate,
            @Value("${monolith.baseUrl}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public double resolveDiscountPercent(String promoCode, String userId) {
        if (promoCode == null || promoCode.isBlank())
            return 0.0;

        String url = baseUrl + "/api/promos/validate?code=" + promoCode + "&userId=" + userId;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = restTemplate.postForObject(url, null, Map.class);
            if (json == null)
                return 0.0;

            // active
            Object active = json.get("active");
            if (active instanceof Boolean b && !b)
                return 0.0;

            Object discount = json.get("discountPercent");
            if (discount instanceof Number n)
                return n.doubleValue();
            return 0.0;
        } catch (RestClientException e) {
            return 0.0;
        }
    }

}
