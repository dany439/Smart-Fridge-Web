package com.smartfridge.controller;

import com.smartfridge.dto.ConsumeItemDTO;
import com.smartfridge.dto.FridgeItemDTO;
import com.smartfridge.entity.FridgeItem;
import com.smartfridge.entity.User;
import com.smartfridge.exceptions.InsufficientStockException;
import com.smartfridge.exceptions.ItemNotFoundException;
import com.smartfridge.records.SuggestionsViewModel;
import com.smartfridge.repository.RecipeRepository;
import com.smartfridge.service.*;
import com.smartfridge.util.ModelHolder;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Controller
@RequestMapping("/fridge")
public class FridgeController {

    private final UserService userService;
    private final FridgeService fridgeService;
    private final ModelHolder modelHolder;
    private final ImageScanService imageScanService;
    private final RecipeService recipeService;
    private final RateLimiterService rateLimiterService;
    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper; // Add this!

    @Autowired
    public FridgeController(UserService userService, FridgeService fridgeService,
                            ModelHolder modelHolder, ImageScanService imageScanService,
                            RecipeService recipeService, RateLimiterService rateLimiterService,
                            ObjectMapper objectMapper, RecipeRepository recipeRepository) {
        this.userService = userService;
        this.fridgeService = fridgeService;
        this.modelHolder = modelHolder;
        this.imageScanService = imageScanService;
        this.recipeService = recipeService;
        this.rateLimiterService = rateLimiterService;
        this.recipeRepository = recipeRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String showFridge() {
        return "redirect:/fridge/list";
    }

    @GetMapping("/insert")
    public String showInsertFood(Model model, Principal principal) {
        User user = userService.findByUsername(principal.getName()).orElseThrow();
        model.addAttribute("fridgeItemDTOManual", new FridgeItemDTO());
        model.addAttribute("fridgeItemDTOImage", new FridgeItemDTO());
        return "food-insertion";
    }



    @PostMapping("/addManual")
    public String processInsertFood(@Valid @ModelAttribute("fridgeItemDTOManual") FridgeItemDTO dto,
                                    BindingResult bindingResult,  // ← must be right after @Valid arg
                                    Principal principal,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("fridgeItemDTOManual", dto);
            model.addAttribute("fridgeItemDTOImage", new FridgeItemDTO());
            return "food-insertion";
        }

        User user = userService.findByUsername(principal.getName()).orElseThrow();
        dto.setAddedVia("MANUAL");
        FridgeItem fridgeItem = fridgeService.create(dto, user);

        redirectAttributes.addFlashAttribute("successMessage",
                "Item " + fridgeItem.getName() + " has been added to your fridge.");

        return "redirect:/fridge/list";
    }

    @PostMapping("/addByImage")
    public String addByImage(@RequestParam("image") MultipartFile image,
                             @Valid @ModelAttribute("fridgeItemDTOImage") FridgeItemDTO dto,
                             BindingResult bindingResult,
                             Principal principal,
                             RedirectAttributes redirectAttributes,
                             Model model) {

        User user = userService.findByUsername(principal.getName()).orElseThrow();

        if (bindingResult.hasErrors()) {
            model.addAttribute("fridgeItemDTOImage", dto);
            model.addAttribute("fridgeItemDTOManual", new FridgeItemDTO());
            return "food-insertion";  // ← was "fridge/addItem", inconsistent with your view name
        }

        if (image == null || image.isEmpty()) {
            model.addAttribute("imageError", "Please upload an image.");
            model.addAttribute("fridgeItemDTOImage", dto);
            model.addAttribute("fridgeItemDTOManual", new FridgeItemDTO());
            return "food-insertion";  // ← same fix
        }

        String contentType = image.getContentType();
        if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png"))) {
            model.addAttribute("imageError", "Please upload a JPG or PNG image.");
            model.addAttribute("fridgeItemDTOImage", dto);
            model.addAttribute("fridgeItemDTOManual", new FridgeItemDTO());
            return "food-insertion";
        }

        if (!modelHolder.isReady()) {
            model.addAttribute("imageError", "Image scanning is still loading. Please try again in a moment.");
            model.addAttribute("fridgeItemDTOImage", dto);
            model.addAttribute("fridgeItemDTOManual", new FridgeItemDTO());
            return "food-insertion";  // ← same fix
        }

        dto.setAddedVia("IMAGE");

        try {
            byte[] imageBytes;
            try {
                imageBytes = image.getBytes();   // read NOW, on the request thread, while the stream is alive
            } catch (IOException e) {
                redirectAttributes.addFlashAttribute("errorMessage", "Could not read uploaded image.");
                return "redirect:/fridge/list";
            }

            String predictedName = imageScanService
                    .submitScan(imageBytes, image.getOriginalFilename(), user.getId(), dto)
                    .get(30, TimeUnit.SECONDS);

            dto.setName(predictedName);
            FridgeItem item = fridgeService.create(dto, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "\"" + item.getName() + "\" added via image scan!");

        } catch (TimeoutException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Scan timed out. Please try again.");
        } catch (ExecutionException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not identify the item: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            redirectAttributes.addFlashAttribute("errorMessage", "Request was interrupted.");
        }

        return "redirect:/fridge/list";
    }

    @GetMapping("/list")
    public String getFridgeItems(Model model, Principal principal) {
        User user = userService.findByUsername(principal.getName()).orElseThrow();
        List<FridgeItem> fridgeItems = fridgeService.getUserItems(user);
        model.addAttribute("fridgeItems", fridgeItems);
        return "food-list";
    }

    @GetMapping("/suggestions")
    public String getSuggestions(Principal principal, Model model) {
        User user = userService.findByUsername(principal.getName()).orElseThrow();
        SuggestionsViewModel view = recipeService.buildSuggestionsView(user);

        model.addAttribute("fridgeEmpty", view.fridgeEmpty());
        model.addAttribute("hasExpiringItems", view.hasExpiringItems());
        model.addAttribute("remainingCalls", rateLimiterService.remainingCalls(principal.getName()));
        if (view.recipes() != null) model.addAttribute("recipes", view.recipes());
        if (view.error() != null) model.addAttribute("error", view.error());
        if (view.info() != null) model.addAttribute("info", view.info());

        return "food-suggestions";
    }

    @PostMapping("/regenerateSuggestions")
    public String regenerateSuggestions(Principal principal,
                                        @RequestParam(defaultValue = "3") int maxRecipes,
                                        RedirectAttributes redirectAttributes) {
        User user = userService.findByUsername(principal.getName()).orElseThrow();
        RecipeService.RegenerateResult result = recipeService.regenerate(user, maxRecipes);

        redirectAttributes.addFlashAttribute(result.success() ? "success" : "error", result.message());
        return "redirect:/fridge/suggestions";
    }

    @GetMapping("/consume")
    public String showConsumeForm(
            @RequestParam(required = false) Integer itemId,
            @RequestParam(required = false) String itemName,
            Model model,
            Principal principal) {

        // 1. Initialize the DTO
        ConsumeItemDTO consumeItemDTO = new ConsumeItemDTO();

        // 2. Pre-populate the DTO if parameters were passed from the list view
        if (itemId != null) {
            consumeItemDTO.setId(itemId);
        }
        if (itemName != null) {
            consumeItemDTO.setItemName(itemName);
        }

        // 3. Bind to the model
        // (Ensure "consumeItemDTO" matches your th:object in the form!)
        model.addAttribute("consumeItemDTO", consumeItemDTO);

        return "food-consume";
    }

    @PostMapping("/processConsume")
    public String consumeItem(@Valid @ModelAttribute("consumeItemDTO") ConsumeItemDTO dto,
                              BindingResult bindingResult,
                              Principal principal,
                              RedirectAttributes redirectAttributes) { // Added parameter

        if (bindingResult.hasErrors()) {
            return "food-consume";
        }

        User user = userService.findByUsername(principal.getName()).orElseThrow();
        long matchCount = fridgeService.countByNameAndUser(dto.getItemName(), user);

        if (matchCount > 1 && dto.getId() == null) {
            bindingResult.rejectValue("id", "duplicate.name",
                    "Multiple items found with this name. Please provide the specific ID.");
        }

        if (matchCount == 0) {
            bindingResult.rejectValue("itemName", "not.found",
                    "No item found with this name.");
        }

        if (bindingResult.hasErrors()) {
            return "food-consume";
        }

        // 5. Proceed with processing if everything is safe
        try {
            FridgeItem item = fridgeService.consume(dto.getItemName(), dto.getId(), dto.getQuantity(), user);

            // Set the success message to be flashed to the redirect destination
            String message;
            if (item == null)
                message = "Successfully deleted " + dto.getItemName();
            else
                message = "Successfully consumed " + dto.getQuantity() + " " + dto.getItemName();

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format(message));

            return "redirect:/fridge/list";

        } catch (ItemNotFoundException e) {
            // Highlight the Item Name text box in red
            bindingResult.rejectValue("itemName", "error.itemName", e.getMessage());
            return "food-consume";

        } catch (InsufficientStockException e) {
            // Highlight the Quantity number box in red
            bindingResult.rejectValue("quantity", "error.quantity", e.getMessage());
            return "food-consume";

        } catch (IllegalArgumentException e) {
            // Tie any other generic validation problems (like a missing ID) directly to the ID box
            bindingResult.rejectValue("id", "error.id", e.getMessage());
            return "food-consume";
        }
    }


}
