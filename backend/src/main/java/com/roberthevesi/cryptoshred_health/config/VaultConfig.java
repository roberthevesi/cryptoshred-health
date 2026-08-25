package com.roberthevesi.cryptoshred_health.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.config.AbstractVaultConfiguration;

@Configuration
public class VaultConfig extends AbstractVaultConfiguration {

    @Value("${spring.vault.host:localhost}")
    private String host;

    @Value("${spring.vault.port:8200}")
    private int port;

    @Value("${spring.vault.token}")
    private String token;

    @Value("${spring.vault.scheme:http}")
    private String scheme;

    @Override
    public VaultEndpoint vaultEndpoint() {
        VaultEndpoint endpoint = VaultEndpoint.create(host, port);
        endpoint.setScheme(scheme);
        return endpoint;
    }

    @Override
    public ClientAuthentication clientAuthentication() {
        return new TokenAuthentication(token);
    }
}
