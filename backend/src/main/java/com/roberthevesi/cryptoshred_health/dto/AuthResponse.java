package com.roberthevesi.cryptoshred_health.dto;

import com.roberthevesi.cryptoshred_health.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private Role role;
}
