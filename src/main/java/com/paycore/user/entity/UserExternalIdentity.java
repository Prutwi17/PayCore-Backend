package com.paycore.user.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Maps external OAuth / Identity Provider accounts (e.g., Google sub) to internal PayCore users.
 * Prevents account duplication and enforces safe identity linking.
 */
@Entity
@Table(
        name = "user_external_identities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_external_identity_provider_subject",
                        columnNames = {"provider", "provider_subject"}
                )
        },
        indexes = {
                @Index(name = "idx_external_identities_user_id", columnList = "user_id"),
                @Index(name = "idx_external_identities_email", columnList = "email")
        }
)
public class UserExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider = "GOOGLE";

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt = Instant.now();

    public UserExternalIdentity() {
    }

    public UserExternalIdentity(String provider, String providerSubject, User user, String email) {
        this.provider = provider != null ? provider.toUpperCase() : "GOOGLE";
        this.providerSubject = providerSubject;
        this.user = user;
        this.email = email != null ? email.trim().toLowerCase() : "";
        this.linkedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = providerSubject;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }
}
