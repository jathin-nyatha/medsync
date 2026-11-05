package com.medsync.patient.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OllamaChatClientIntegrationTest {

	@Autowired
	private ChatClient chatClient;

	@Test
	void shouldGenerateResponseUsingOllama() {

		String response = chatClient.prompt().user("Explain what a balanced diet is in two simple sentences.").call()
				.content();

		assertNotNull(response, "Ollama response should not be null");
		assertFalse(response.isBlank(), "Ollama response should not be empty");

		System.out.println("Ollama response:");
		System.out.println(response);
	}
}