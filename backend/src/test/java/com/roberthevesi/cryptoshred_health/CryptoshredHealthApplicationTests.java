package com.roberthevesi.cryptoshred_health;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires live PostgreSQL, Vault, and Kafka containers running on Docker")
@SpringBootTest
class CryptoshredHealthApplicationTests {

	@Test
	void contextLoads() {
	}

}

