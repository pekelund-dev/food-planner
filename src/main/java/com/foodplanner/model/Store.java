package com.foodplanner.model;

/**
 * Represents a specific grocery store (e.g. "ICA Kvantum Malmborgs Caroli")
 * rather than a chain. Stored in the global "stores" Firestore collection
 * for autocomplete and in each user's selectedStores list.
 */
public class Store {

    private String id;
    private String name;
    private String chain; // chain key: "ica", "willys", "coop", etc.
    private String offersUrl; // store-specific offers page URL (optional; falls back to chain URL)

    public Store() {}

    public Store(String id, String name, String chain) {
        this.id = id;
        this.name = name;
        this.chain = chain;
    }

    public Store(String id, String name, String chain, String offersUrl) {
        this.id = id;
        this.name = name;
        this.chain = chain;
        this.offersUrl = offersUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChain() { return chain; }
    public void setChain(String chain) { this.chain = chain; }

    public String getOffersUrl() { return offersUrl; }
    public void setOffersUrl(String offersUrl) { this.offersUrl = offersUrl; }

    /** Returns true when this store has a non-blank ID suitable for offer lookups. */
    public boolean hasValidId() { return id != null && !id.isBlank(); }
}
