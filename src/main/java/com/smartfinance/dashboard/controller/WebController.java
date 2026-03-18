package com.smartfinance.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
    
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
    
    @GetMapping("/transactions")
    public String transactions() {
        return "transactions";
    }
    
    @GetMapping("/budgets")
    public String budgets() {
        return "budgets";
    }
    
    @GetMapping("/investments")
    public String investments() {
        return "investments";
    }
    
    @GetMapping("/goals")
    public String goals() {
        return "goals";
    }
    
    @GetMapping("/reports")
    public String reports() {
        return "reports";
    }
    
    @GetMapping("/analytics")
    public String analytics() {
        return "analytics";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    @GetMapping("/subscriptions")
    public String subscriptions() {
        return "subscriptions";
    }

    @GetMapping("/debts")
    public String debts() {
        return "debts";
    }
}
