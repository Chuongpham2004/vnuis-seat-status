package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.vnuis.library.domain.Enums.Role;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_code", nullable = false, unique = true)
    private String studentCode;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.STUDENT;

    @Column(name = "credit_score", nullable = false)
    private int creditScore = 100;

    @Column(name = "banned_until")
    private Instant bannedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    public User(String studentCode, String email, String passwordHash, String fullName, Role role, int creditScore) {
        this.studentCode = studentCode;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.creditScore = creditScore;
    }

    public boolean isBannedAt(Instant now) {
        return bannedUntil != null && bannedUntil.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public int getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(int creditScore) {
        this.creditScore = creditScore;
    }

    public Instant getBannedUntil() {
        return bannedUntil;
    }

    public void setBannedUntil(Instant bannedUntil) {
        this.bannedUntil = bannedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
