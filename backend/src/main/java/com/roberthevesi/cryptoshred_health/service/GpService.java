package com.roberthevesi.cryptoshred_health.service;

import com.roberthevesi.cryptoshred_health.dto.GpRequest;
import com.roberthevesi.cryptoshred_health.dto.GpResponse;
import com.roberthevesi.cryptoshred_health.model.GP;
import com.roberthevesi.cryptoshred_health.repository.GpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GpService {

    private final GpRepository gpRepository;

    public List<GpResponse> findAll(boolean includeInactive) {
        List<GP> list = includeInactive ? gpRepository.findAll() : gpRepository.findByIsActiveTrue();
        return list.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<GpResponse> findAll() {
        return findAll(true);
    }

    public GpResponse findById(UUID id) {
        return gpRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("GP not found"));
    }

    public List<GpResponse> search(String query) {
        return gpRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public GpResponse create(GpRequest request) {
        GP gp = new GP();
        gp.setFirstName(request.getFirstName());
        gp.setLastName(request.getLastName());
        gp.setEmail(request.getEmail());
        gp.setPhoneNumber(request.getPhoneNumber());
        gp.setGmcNumber(request.getGmcNumber());
        gp.setSpecialisation(request.getSpecialisation());
        gp.setPracticeName(request.getPracticeName());
        gp.setActive(true);
        
        return toResponse(gpRepository.save(gp));
    }

    public GpResponse update(UUID id, GpRequest request) {
        GP gp = gpRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("GP not found"));
        
        gp.setFirstName(request.getFirstName());
        gp.setLastName(request.getLastName());
        gp.setEmail(request.getEmail());
        gp.setPhoneNumber(request.getPhoneNumber());
        gp.setGmcNumber(request.getGmcNumber());
        gp.setSpecialisation(request.getSpecialisation());
        gp.setPracticeName(request.getPracticeName());
        
        return toResponse(gpRepository.save(gp));
    }

    public void deactivate(UUID id) {
        GP gp = gpRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("GP not found"));
        gp.setActive(false);
        gpRepository.save(gp);
    }

    public GpResponse reactivate(UUID id) {
        GP gp = gpRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("GP not found"));
        gp.setActive(true);
        return toResponse(gpRepository.save(gp));
    }

    private GpResponse toResponse(GP gp) {
        return GpResponse.builder()
                .id(gp.getId())
                .firstName(gp.getFirstName())
                .lastName(gp.getLastName())
                .email(gp.getEmail())
                .phoneNumber(gp.getPhoneNumber())
                .gmcNumber(gp.getGmcNumber())
                .specialisation(gp.getSpecialisation())
                .practiceName(gp.getPracticeName())
                .isActive(gp.isActive())
                .createdAt(gp.getCreatedAt())
                .build();
    }
}
