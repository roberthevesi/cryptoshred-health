package com.roberthevesi.cryptoshred_health.repository;

import com.roberthevesi.cryptoshred_health.model.GP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GpRepository extends JpaRepository<GP, UUID> {
    List<GP> findByIsActiveTrue();
    List<GP> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(String first, String last);
    Optional<GP> findByGmcNumber(String gmcNumber);
    boolean existsByGmcNumber(String gmcNumber);
    boolean existsByEmail(String email);
}
