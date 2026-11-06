package com.smartfridge.service;

import com.smartfridge.entity.FridgeItem;
import com.smartfridge.entity.GeneratedRecipes;
import com.smartfridge.entity.User;
import com.smartfridge.records.RecipesResponse;
import com.smartfridge.records.SuggestionsViewModel;
import com.smartfridge.repository.RecipeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecipeService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";

    private static final String SYSTEM_PROMPT = """
        You are a recipe generator for a smart fridge.
        The input you receive will contain:
        - FRIDGE_ITEMS: a JSON array of objects, each:
            { "name": string, "expires_in_days": integer }
        - MAX_RECIPES: an integer.
        Your tasks:
        1. Generate up to MAX_RECIPES recipes that make good use of the ingredients in FRIDGE_ITEMS.
        2. Prefer ingredients that are closer to expiring (smaller expires_in_days),
           but you MAY also use ingredients that are not in FRIDGE_ITEMS (IMPORTANT).
        3. Do NOT remove a recipe just because some ingredients are not in FRIDGE_ITEMS.
        4. Keep recipes realistic and simple to mid complexity, not something 5 stars.
        5. Avoid giving multiple recipes that are nearly similar, try to switch up the ingredients.
        6. You can also use only 1 available ingredient in a recipe but
        DO NOT repeat similar foods that use nearly same ingredients more than twice.
        7. Mix between simple and complex foods to make the recipes diverse.
        Each recipe you output MUST have exactly these fields:
        - title: string
        - ingredients: array of strings (ingredient names)
        - steps: array of short strings (cooking steps)
        Important:
        - Do NOT include any other fields.
        - Do NOT include categories.
        - Output ONLY valid JSON with this structure:
        {
          "recipes": [
            {
              "title": "string",
              "ingredients": ["string", "..."],
              "steps": ["step 1", "step 2", "..."]
            }
          ]
        }
        """;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final FridgeService fridgeService;
    private final RecipeRepository recipeRepository;
    private final RecipeCacheService recipeCacheService;
    private final RateLimiterService rateLimiterService;

    public RecipeService(FridgeService fridgeService,
                         RecipeRepository recipeRepository,
                         RecipeCacheService recipeCacheService,
                         RateLimiterService rateLimiterService) {
        this.fridgeService = fridgeService;
        this.recipeRepository = recipeRepository;
        this.recipeCacheService = recipeCacheService;
        this.rateLimiterService = rateLimiterService;
    }

    // -------------------------------------------------
    // Suggestions view (used by GET /suggestions)
    // -------------------------------------------------

    public SuggestionsViewModel buildSuggestionsView(User user) {
        List<FridgeItem> fridgeItems = fridgeService.getUserItems(user);
        Set<String> fridgeItemNames = fridgeItems.stream()
                .map(item -> item.getName().toLowerCase().trim())
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        boolean hasExpiringItems = fridgeItems.stream()
                .anyMatch(i -> i.getExpiryDate() != null && ChronoUnit.DAYS.between(now, i.getExpiryDate()) <= 3);
        boolean fridgeEmpty = fridgeItems.isEmpty();

        RecipesResponse result = resolveRecipes(user);

        if (result == null) {
            return new SuggestionsViewModel(fridgeEmpty, hasExpiringItems, null,
                    null, "No recipes generated yet. Click 'Regenerate' to ask Gemini!");
        }

        List<Map<String, Object>> recipes = mapWithFridgeStatus(result, fridgeItemNames);
        return new SuggestionsViewModel(fridgeEmpty, hasExpiringItems, recipes, null, null);
    }

    // cache first, DB fallback, repopulate cache on fallback hit
    private RecipesResponse resolveRecipes(User user) {
        Optional<RecipesResponse> cached = recipeCacheService.getCached(user.getUsername());
        if (cached.isPresent()) {
            System.out.println("Cache hit: " + user.getId() + " @ " + LocalDateTime.now());
            return cached.get();
        }

        Optional<GeneratedRecipes> dbRow = recipeRepository.findByUser(user);
        if (dbRow.isEmpty()) {
            return null;
        }

        try {
            RecipesResponse parsed = objectMapper.readValue(dbRow.get().getSavedResponse(), RecipesResponse.class);
            recipeCacheService.save(user.getUsername(), parsed);
            System.out.println("DB fallback: " + user.getId() + " @ " + LocalDateTime.now());
            return parsed;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> mapWithFridgeStatus(RecipesResponse result, Set<String> fridgeItemNames) {
        return result.recipes().stream().map(recipe -> {
            Map<String, Object> recipeMap = new LinkedHashMap<>();
            recipeMap.put("title", recipe.title());
            recipeMap.put("steps", recipe.steps());

            List<Map<String, String>> ingredients = recipe.ingredients().stream().map(name -> {
                Map<String, String> ingMap = new HashMap<>();
                ingMap.put("name", name);
                ingMap.put("inFridge", String.valueOf(fridgeItemNames.contains(name.toLowerCase().trim())));
                return ingMap;
            }).toList();

            recipeMap.put("ingredients", ingredients);
            return recipeMap;
        }).toList();
    }

    // -------------------------------------------------
    // Regenerate (used by POST /regenerateSuggestions)
    // -------------------------------------------------

    public RegenerateResult regenerate(User user, int maxRecipes) {
        String username = user.getUsername();

        if (!rateLimiterService.isAllowed(username)) {
            return RegenerateResult.failure("You've reached the limit of 10 recipe suggestions per day. Come back tomorrow!");
        }

        List<FridgeItem> fridgeItems = fridgeService.getUserItems(user);
        if (fridgeItems.isEmpty()) {
            return RegenerateResult.failure("Your fridge is empty! Add some food before generating recipes.");
        }

        try {
            RecipesResponse result = generateRecipes(fridgeItems, maxRecipes);
            rateLimiterService.increment(username);

            GeneratedRecipes outputData = recipeRepository.findByUser(user)
                    .orElseGet(() -> {
                        GeneratedRecipes newRow = new GeneratedRecipes();
                        newRow.setUser(user);
                        return newRow;
                    });

            outputData.setSavedResponse(objectMapper.writeValueAsString(result));
            outputData.setLastUpdated(LocalDateTime.now());
            recipeRepository.save(outputData);

            recipeCacheService.save(username, result);

            return RegenerateResult.success("Recipes successfully regenerated!");
        } catch (Exception e) {
            return RegenerateResult.failure("Could not generate recipes. Please try again.");
        }
    }

    public record RegenerateResult(boolean success, String message) {
        static RegenerateResult success(String msg) { return new RegenerateResult(true, msg); }
        static RegenerateResult failure(String msg) { return new RegenerateResult(false, msg); }
    }

    // -------------------------------------------------
    // Gemini API call (unchanged from your original)
    // -------------------------------------------------

    public RecipesResponse generateRecipes(List<FridgeItem> fridgeItems, int maxRecipes) throws Exception {
        String userMessage = buildUserMessage(fridgeItems, maxRecipes);
        String requestBody = buildRequestBody(userMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: " + response.statusCode() + " — " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();

        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

        return objectMapper.readValue(text, RecipesResponse.class);
    }

    private String buildUserMessage(List<FridgeItem> items, int maxRecipes) throws Exception {
        LocalDateTime now = LocalDateTime.now();

        ArrayNode fridgeArray = objectMapper.createArrayNode();
        for (FridgeItem item : items) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("name", item.getName());

            if (item.getExpiryDate() != null) {
                long daysLeft = ChronoUnit.DAYS.between(now, item.getExpiryDate());
                node.put("expires_in_days", daysLeft);
            } else {
                node.put("expires_in_days", 999);
            }

            fridgeArray.add(node);
        }

        ObjectNode userInput = objectMapper.createObjectNode();
        userInput.set("FRIDGE_ITEMS", fridgeArray);
        userInput.put("MAX_RECIPES", maxRecipes);

        return objectMapper.writeValueAsString(userInput);
    }

    private String buildRequestBody(String userMessage) throws Exception {
        ObjectNode systemPart = objectMapper.createObjectNode();
        systemPart.put("text", SYSTEM_PROMPT);
        ArrayNode systemParts = objectMapper.createArrayNode();
        systemParts.add(systemPart);
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        systemInstruction.set("parts", systemParts);

        ObjectNode userPart = objectMapper.createObjectNode();
        userPart.put("text", userMessage);
        ArrayNode userParts = objectMapper.createArrayNode();
        userParts.add(userPart);
        ObjectNode userContent = objectMapper.createObjectNode();
        userContent.put("role", "user");
        userContent.set("parts", userParts);
        ArrayNode contents = objectMapper.createArrayNode();
        contents.add(userContent);

        ObjectNode responseMimeType = objectMapper.createObjectNode();
        responseMimeType.put("response_mime_type", "application/json");

        ObjectNode root = objectMapper.createObjectNode();
        root.set("system_instruction", systemInstruction);
        root.set("contents", contents);
        root.set("generationConfig", responseMimeType);

        return objectMapper.writeValueAsString(root);
    }
}