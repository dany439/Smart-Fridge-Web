package com.smartfridge.entity;

import com.smartfridge.util.ShelfLifeMap;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fridge_items")
public class FridgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY,
            cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH })
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 25, nullable = false)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unit;

    @Column(name = "storage_date")
    private LocalDateTime storageDate;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Column(name = "added_via", length = 10)
    private String addedVia;

    public FridgeItem() {
    }

    public FridgeItem(User user, String name, String category,
                      String unit, BigDecimal quantity,
                      LocalDateTime expiryDate, String addedVia) {
        this.user = user;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.addedVia = addedVia;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public LocalDateTime getStorageDate() {
        return storageDate;
    }

    public void setStorageDate(LocalDateTime storageDate) {
        this.storageDate = storageDate;
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

    @PrePersist
    private void onCreate() {
        this.storageDate = LocalDateTime.now();

        if (this.expiryDate == null) {
            Integer days = ShelfLifeMap.getDays(this.name);
            if (days != null) {
                this.expiryDate = this.storageDate.plusDays(days);
            }
        }
    }

    @Override
    public String toString() {
        return "FridgeItem{" +
                "id=" + id +
                ", user=" + user +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                ", storageDate=" + storageDate +
                ", expiryDate=" + expiryDate +
                ", addedVia='" + addedVia + '\'' +
                '}';
    }
}
