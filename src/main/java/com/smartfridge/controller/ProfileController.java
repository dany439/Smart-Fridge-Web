package com.smartfridge.controller;

import com.smartfridge.dto.ProfileUpdateDTO;
import com.smartfridge.entity.User;
import com.smartfridge.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
public class ProfileController {

    private final UserService userService;

    @Autowired
    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile/edit")
    public String showEditProfilePage(Principal principal, Model model) {
        User user = userService.findByUsername(principal.getName()).orElseThrow();

        ProfileUpdateDTO dto = new ProfileUpdateDTO(
                user.getFirstName(),
                user.getLastName(),
                user.getEmail()
        );

        model.addAttribute("profileUpdateDTO", dto);
        return "profile-edit";
    }

    @PostMapping("/profile/update")
    public String processProfileUpdate(
            @Valid @ModelAttribute("profileUpdateDTO") ProfileUpdateDTO dto,
            BindingResult bindingResult,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "profile-edit";
        }

        User user = userService.findByUsername(principal.getName()).orElseThrow();

        userService.updateProfile(user, dto);

        redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully");
        return "redirect:/home";
    }
}
