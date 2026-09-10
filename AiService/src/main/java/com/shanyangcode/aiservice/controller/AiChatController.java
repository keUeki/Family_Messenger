package com.shanyangcode.aiservice.controller;


import com.shanyangcode.aiservice.Monitor.MonitorContext;
import com.shanyangcode.aiservice.Monitor.MonitorContextHolder;
import com.shanyangcode.aiservice.ai.AiChat;
import com.shanyangcode.aiservice.model.dto.KnowledgeRequest;
import com.shanyangcode.common.model.dto.ChatRequest;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@RequestMapping("/api/ai")
@Slf4j
@RestController
public class AiChatController {

    @Resource
    private AiChat aiChat;


    @Resource
    private EmbeddingStoreIngestor embeddingStoreIngestor;

    @Value("${rag.docs-path}")
    private String docsPath;

    private final  String  TARGET_FILENAME = "InfiniteChat.md";


    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest chatRequest) {
        MonitorContextHolder.setContext(MonitorContext.builder().userId(chatRequest.getUserId()).sessionId(chatRequest.getSessionId()).build());
        String chat = aiChat.chat(chatRequest.getSessionId(), chatRequest.getPrompt());
        MonitorContextHolder.clearContext();
        return chat;
    }


    @PostMapping("/streamChat")
    public Flux<String> streamChat(@RequestBody ChatRequest chatRequest) {
        MonitorContext context = MonitorContext.builder()
                .userId(chatRequest.getUserId())
                .sessionId(chatRequest.getSessionId())
                .build();

        return Flux.defer(() -> {
            MonitorContextHolder.setContext(context);
            return aiChat.streamChat(chatRequest.getSessionId(), chatRequest.getPrompt())
                    .doFinally(signal -> MonitorContextHolder.clearContext());
        });
    }



    @PostMapping("/insert")
    public String insertKnowledge(@RequestBody KnowledgeRequest knowledgeRequest) {
        // 1. Format the entry
        String formattedContent = String.format("### Q: %s\n\nA: %s", knowledgeRequest.getQuestion(), knowledgeRequest.getAnswer());

        // 2. Append it to the file on disk (InfiniteChat.md)
        boolean writeSuccess = appendToFile(formattedContent, knowledgeRequest.getSourceName());
        if (!writeSuccess) {
            return "Insert failed: could not write to the local file";
        }

        // 3. Store it in the vector database (RAG)
        try {
            // Set the source metadata
            String sourceName = (knowledgeRequest.getSourceName() != null) ? knowledgeRequest.getSourceName() : TARGET_FILENAME;
            Metadata metadata = Metadata.from("file_name", sourceName);

            // Build the document and embed it
            Document document = Document.from(formattedContent, metadata);
            embeddingStoreIngestor.ingest(document);

            log.info("RAG - knowledge entry added: {}", knowledgeRequest.getQuestion());
            return "Insert succeeded: synchronised to " + knowledgeRequest.getSourceName() + " and the vector database";
        } catch (Exception e) {
            log.error("RAG - embedding failed", e);
            return "Insert partially succeeded: the file was written, but the vector store update failed";
        }
    }



    private synchronized boolean appendToFile(String content, String sourceName) {
        try {
            // Build the full path
            Path filePath = Paths.get(docsPath, sourceName);
            log.info("File written to: {}", filePath.toAbsolutePath());
            // Create the file first if it does not exist
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }

            // Wrap the text in newlines so the entry stays visually separate
            String textToAppend = "\n\n" + content;

            // Append it
            Files.writeString(
                    filePath,
                    textToAppend,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.CREATE
            );
            return true;
        } catch (IOException e) {
            log.error("RAG - failed to write the local file: {}", e.getMessage(), e);
            return false;
        }
    }

    @GetMapping("/summary")
    public String chatSummary(@RequestParam String historyLog) {
        return aiChat.chatSummary(historyLog);
    }
}



