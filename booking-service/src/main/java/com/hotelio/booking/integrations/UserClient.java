package com.hotelio.booking.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public UserClient(RestTemplate restTemplate,
            @Value("${monolith.baseUrl}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public boolean isActive(String userId) {
        String url = baseUrl + "/api/users/" + userId + "/active";
        Boolean resp = restTemplate.getForObject(url, Boolean.class);
        return resp != null && resp;
    }

    public boolean isBlacklisted(String userId) {
        String url = baseUrl + "/api/users/" + userId + "/blacklisted";
        Boolean resp = restTemplate.getForObject(url, Boolean.class);
        return resp != null && resp;
    }

    public boolean isVip(String userId) {
        String url = baseUrl + "/api/users/" + userId + "/vip";
        Boolean resp = restTemplate.getForObject(url, Boolean.class);
        return resp != null && resp;
    }
}
