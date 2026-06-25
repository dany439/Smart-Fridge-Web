package com.smartfridge.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public class ConsumeItemDTO {

    @NotBlank(message = "Item name is required")
    @Size(min = 2, max = 50, message = "Item name must be between 2 and 50 characters")
    private String itemName;

    @PositiveOrZero(message = "ID must be greater than or equal to 0")
    private Integer id;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than 0")
    @Digits(integer = 5, fraction = 2, message = "Quantity must have up to 2 decimal places")
    private BigDecimal quantity;

    public ConsumeItemDTO() {
    }

    public ConsumeItemDTO(String itemName, BigDecimal quantity) {
        this.itemName = itemName;
        this.quantity = quantity;
    }

    public ConsumeItemDTO(String itemName, Integer id, BigDecimal quantity) {
        this.itemName = itemName;
        this.id = id;
        this.quantity = quantity;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "ConsumeItemDTO{" +
                "itemName='" + itemName + '\'' +
                ", id=" + id +
                ", quantity=" + quantity +
                '}';
    }
}
