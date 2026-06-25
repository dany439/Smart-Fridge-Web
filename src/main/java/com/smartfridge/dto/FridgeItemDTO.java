package com.smartfridge.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FridgeItemDTO {

    @NotBlank(message = "Item name is required")
    @Size(max = 25, message = "Item name cannot exceed 25 characters")
    @Pattern(regexp = "^[a-zA-Z\\s\\-]+$", message = "Item name can only contain letters, spaces, and hyphens")
    private String name;

    @Size(max = 50, message = "Category cannot exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z\\s\\-]*$", message = "Category can only contain letters, spaces, and hyphens")
    private String category;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Quantity must have at most 8 digits and 2 decimal places")
    private BigDecimal quantity;

    @Size(max = 20, message = "Unit cannot exceed 20 characters")
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Unit can only contain letters and spaces")
    private String unit;

    @Future(message = "Expiry date must be in the future")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expiryDate;

    @Pattern(regexp = "^(MANUAL|IMAGE)$", message = "Added via must be MANUAL or IMAGE")
    private String addedVia;

    public FridgeItemDTO() {
    }

    public FridgeItemDTO(String name, String category, BigDecimal quantity, String unit, LocalDateTime expiryDate, String addedVia) {
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unit = unit;
        this.expiryDate = expiryDate;
        this.addedVia = addedVia;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getAddedVia() {
        return addedVia;
    }

    public void setAddedVia(String addedVia) {
        this.addedVia = addedVia;
    }

    @Override
    public String toString() {
        return "FridgeItemDTO{" +
                "name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                ", expiryDate=" + expiryDate +
                ", addedVia='" + addedVia + '\'' +
                '}';
    }
}