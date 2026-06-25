package com.smartfridge.controller;

import com.smartfridge.dto.RegistrationDTO;
import com.smartfridge.entity.User;
import com.smartfridge.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/register")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final SecurityContextRepository securityContextRepository;

    public RegistrationController(RegistrationService registrationService,
                                  SecurityContextRepository securityContextRepository) {
        this.registrationService = registrationService;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/showRegistrationPage")
    public String showRegistrationPage(Model model) {
        model.addAttribute("registrationDTO", new RegistrationDTO());
        return "register"; // maps to register.html
    }

    @PostMapping("/processRegistration")
    public String processRegistration(@Valid @ModelAttribute("registrationDTO") RegistrationDTO dto,
                                      BindingResult bindingResult,
                                      Model model,
                                      HttpServletRequest request,
                                      HttpServletResponse response,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors())
            return "register";

        try {
            User user = registrationService.register(dto);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

            // Build a real UserDetails principal, not a bare String
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword()) // already encoded by registrationService
                    .authorities(authorities)
                    .build();

            var auth = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Welcome, " + user.getFirstName() + "! Your account has been created.");
            return "redirect:/home";

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }
}