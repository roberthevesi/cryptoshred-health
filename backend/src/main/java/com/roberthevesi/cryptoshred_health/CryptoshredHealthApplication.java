package com.roberthevesi.cryptoshred_health;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoshredHealthApplication {

	public static void main(String[] args) {
		SpringApplication.run(CryptoshredHealthApplication.class, args);
	}

}

