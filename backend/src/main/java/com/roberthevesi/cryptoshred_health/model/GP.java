package com.roberthevesi.cryptoshred_health.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gps")
@Getter
@Setter
@NoArgsConstructor
public class GP {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(unique = true, length = 255)
    private String email;

    @Column(length = 20)
    private String phoneNumber;

    @Column(unique = true, length = 20)
    private String gmcNumber;

    @Column(length = 100)
    private String specialisation;

    @Column(length = 200)
    private String practiceName;

    private boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
