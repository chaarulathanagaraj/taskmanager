package com.aios.ai.agents;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatbotAiAgent {

    private final ChatLanguageModel model;

    public ChatbotAiAgent(@Value("${gemini.api.key:}") String apiKey) {
        ChatLanguageModel tempModel = null;
        if (apiKey != null && !apiKey.isBlank() && !apiKey.equals("your_api_key_here")) {
            try {
                tempModel = GoogleAiGeminiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("gemini-2.5-pro")
                        .temperature(0.7)
                        .build();
                log.info("Initialized Google AI Gemini model for Chatbot");
            } catch (Exception e) {
                log.warn("Failed to initialize Google AI Gemini model: {}", e.getMessage());
            }
        } else {
            log.warn("No Gemini API key provided. Using rule-based fallback for Chatbot.");
        }
        this.model = tempModel;
    }

    public String generateResponse(String userMessage, String context) {
        if (model != null) {
            try {
                String promptParams = String.format("A user is interacting with an AI Operations System. Context of the issue: %s.\nUser says: %s\nProvide a technical but helpful response.", context, userMessage);
                return model.generate(promptParams);
            } catch (Exception e) {
                log.error("Failed to generate AI response", e);
                return ruleBasedResponse(userMessage);
            }
        } else {
            return ruleBasedResponse(userMessage);
        }
    }

    private String ruleBasedResponse(String userMessage) {
        String msg = userMessage.toLowerCase().trim();
        if (msg.contains("hi") || msg.contains("hello")) {
            return "Hello! I am the AIOS Chatbot system. Since AI is not fully configured with an API key, I am in offline-mode. I can help with fundamental troubleshooting!";
        }
        if (msg.contains("fix") || msg.contains("resolve")) {
            return "I recommend reviewing the memory or CPU thresholds to see which remediation plan applies. You can execute safe rules automatically.";
        }
        return "I understand your query: '" + userMessage + "'. Please provide more context or configure my API key for advanced generative diagnostics.";
    }
}
