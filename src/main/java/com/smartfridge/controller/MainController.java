package com.smartfridge.controller;

import com.smartfridge.entity.User;
import com.smartfridge.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class MainController {

    private final UserService userService;

    public MainController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String redirectToHome() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String showHomePage(Model model, Principal principal) {
        // 1. Fetch the user directly from the database using your service [cite: 70]
        User user = userService.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Add the database roles list to the UI model [cite: 73, 78]
        // (Assumes your User entity has a getRoles() method returning a Collection/List)
        model.addAttribute("dbRoles", userService.findUserRole(user));

        return "home";
    }
}
