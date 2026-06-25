package com.smartfridge.service;

import com.smartfridge.entity.FridgeItem;
import com.smartfridge.records.RecipesResponse;
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
import java.util.List;


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

    // -------------------------------------------------
    // Private helpers
    // -------------------------------------------------

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
                node.put("expires_in_days", 999); // no expiry known
            }

            fridgeArray.add(node);
        }

        ObjectNode userInput = objectMapper.createObjectNode();
        userInput.set("FRIDGE_ITEMS", fridgeArray);
        userInput.put("MAX_RECIPES", maxRecipes);

        return objectMapper.writeValueAsString(userInput);
    }

    private String buildRequestBody(String userMessage) throws Exception {
        // Gemini request structure:
        // { "system_instruction": { "parts": [{ "text": "..." }] },
        //   "contents": [{ "parts": [{ "text": "..." }] }] }

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

        // Tell Gemini to respond in JSON
        ObjectNode responseMimeType = objectMapper.createObjectNode();
        responseMimeType.put("response_mime_type", "application/json");

        ObjectNode root = objectMapper.createObjectNode();
        root.set("system_instruction", systemInstruction);
        root.set("contents", contents);
        root.set("generationConfig", responseMimeType);

        return objectMapper.writeValueAsString(root);
    }

    private JsonNode parseRecipesFromResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Gemini wraps the output: candidates[0].content.parts[0].text
        String text = root
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();

        // Strip markdown fences if Gemini adds them despite response_mime_type
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

        return objectMapper.readTree(text);
    }
}