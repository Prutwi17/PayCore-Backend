package com.paycore.auth.dto;

public class GoogleIdTokenPayload {

    private final String subject;
    private final String email;
    private final boolean emailVerified;
    private final String name;
    private final String picture;

    public GoogleIdTokenPayload(String subject, String email, boolean emailVerified, String name, String picture) {
        this.subject = subject;
        this.email = email != null ? email.trim().toLowerCase() : "";
        this.emailVerified = emailVerified;
        this.name = name;
        this.picture = picture;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getName() {
        return name;
    }

    public String getPicture() {
        return picture;
    }
}
