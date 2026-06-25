package com.smartfridge.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SecurityController {

    @GetMapping("/showMyLoginPage")
    public String showMyLoginPage(){

        return "fancy-login";
    }

    @GetMapping("/access-denied")
    public String showAccessDenied(HttpServletRequest request, Model model) {
        String targetUrl = (String) request.getAttribute("jakarta.servlet.forward.request_uri");

        model.addAttribute("requestedUrl", targetUrl != null ? targetUrl : "Unknown Page");

        return "access-denied"; // maps to your access-denied.html template
    }


}
