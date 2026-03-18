package com.smartfinance.dashboard.controller;

import com.smartfinance.dashboard.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<SubscriptionService.SubscriptionSummary>> getAll() {
        return ResponseEntity.ok(subscriptionService.detectSubscriptions());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<SubscriptionService.SubscriptionSummary>> getUpcoming(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(subscriptionService.getUpcomingSubscriptions(days));
    }
}
