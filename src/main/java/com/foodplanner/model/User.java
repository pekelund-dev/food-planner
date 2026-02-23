package com.foodplanner.model;

import java.time.Instant;
import java.util.List;

public class User {

    private String id;
    private String email;
    private String name;
    private String pictureUrl;
    private MenuConfig menuConfig;
    private List<String> selectedStoreIds;
    private List<Store> selectedStores;
    private Instant createdAt;
    private Instant updatedAt;

    public User() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPictureUrl() { return pictureUrl; }
    public void setPictureUrl(String pictureUrl) { this.pictureUrl = pictureUrl; }

    public MenuConfig getMenuConfig() { return menuConfig; }
    public void setMenuConfig(MenuConfig menuConfig) { this.menuConfig = menuConfig; }

    public List<String> getSelectedStoreIds() { return selectedStoreIds; }
    public void setSelectedStoreIds(List<String> selectedStoreIds) { this.selectedStoreIds = selectedStoreIds; }

    public List<Store> getSelectedStores() { return selectedStores; }
    public void setSelectedStores(List<Store> selectedStores) { this.selectedStores = selectedStores; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
