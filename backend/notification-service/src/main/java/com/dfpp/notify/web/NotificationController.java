package com.dfpp.notify.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "websocketEndpoint", "/ws",
                "topics", Map.of(
                        "global", "/topic/progress",
                        "perUser", "/topic/progress/{userId}",
                        "alerts", "/topic/alerts/{userId}"),
                "transport", "STOMP over SockJS");
    }
}
