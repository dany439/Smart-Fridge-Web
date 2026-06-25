package com.smartfridge.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {

    @EmbeddedId
    private RoleId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Role() {}

    public Role(User user, String role) {
        this.user = user;
        this.id = new RoleId(user.getId(), role);
    }

    public RoleId getId() {
        return id;
    }

    public void setId(RoleId id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}