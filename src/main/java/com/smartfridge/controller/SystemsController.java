package com.smartfridge.controller;

import com.smartfridge.entity.FridgeItem;
import com.smartfridge.entity.User;
import com.smartfridge.service.FridgeService;
import com.smartfridge.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
@RequestMapping("/systems")
public class SystemsController {

    private final FridgeService fridgeService;
    private final UserService userService;

    @Autowired
    public SystemsController(FridgeService fridgeService, UserService userService) {
        this.fridgeService = fridgeService;
        this.userService = userService;
    }

    @GetMapping
    public String redirectToUsers() {
        return "redirect:/systems/users";
    }

    @GetMapping("/viewall")
    public String viewAllItems(Model model) {
        List<FridgeItem> allItems = fridgeService.findAllWithUser();
        model.addAttribute("allItems", allItems);
        model.addAttribute("totalItems", allItems.size());
        return "systems-viewall";
    }

    @GetMapping("/users")
    public String viewAllUsers(Model model) {
        List<User> users = userService.findAll();
        model.addAttribute("users", users);
        return "systems-users";
    }

    @GetMapping("/users/{id}")
    public String viewUserItems(@PathVariable Integer id, Model model) {
        User selectedUser = userService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<FridgeItem> items = fridgeService.findByUser(selectedUser);

        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("items", items);
        return "systems-user-items";
    }
}
