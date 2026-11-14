package com.popman.arca.entity;

import jakarta.persistence.*;

/** Stable logical identity; AUTO_INCREMENT allocates IDs safely across transactions. */
@Entity
@Table(name = "post_identities")
public class PostIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    public PostIdentity() {}

    public PostIdentity(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
}
