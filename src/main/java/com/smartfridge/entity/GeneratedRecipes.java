package com.smartfridge.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recipes")
public class GeneratedRecipes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Direct link to the user so we know who owns this saved state
    @OneToOne(fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH, CascadeType.REFRESH})
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    // Forces database to support large markdown text output lengths
    @Column(columnDefinition = "TEXT")
    private String savedResponse;

    private LocalDateTime lastUpdated;

    public GeneratedRecipes() {
    }

    public GeneratedRecipes(User user, String savedResponse) {
        this.user = user;
        this.savedResponse = savedResponse;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSavedResponse() {
        return savedResponse;
    }

    public void setSavedResponse(String savedResponse) {
        this.savedResponse = savedResponse;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public String toString() {
        return "Recipes{" +
                "id=" + id +
                ", user=" + user +
                ", savedResponse='" + savedResponse + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
