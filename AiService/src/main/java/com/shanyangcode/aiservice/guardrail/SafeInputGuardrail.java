package com.shanyangcode.aiservice.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.Set;

public class SafeInputGuardrail implements InputGuardrail {


    private static final Set<String> sensitiveWords = Set.of("die", "kill");

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String inputText = userMessage.singleText();


        for (String keyword : sensitiveWords) {
            if (!keyword.isEmpty() && inputText.contains(keyword)) {
                return fatal("Your question must not contain sensitive words!!!!!");
            }
        }

        return success();
    }
}