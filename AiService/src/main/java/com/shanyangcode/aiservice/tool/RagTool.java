package com.shanyangcode.aiservice.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RagTool {

    @Resource
    private EmbeddingStoreIngestor embeddingStoreIngestor;

    @Value("${rag.docs-path}")
    private String docsPath;

    /**
     * Tool definition.
     * The model decides when to call it based on the @Tool description and the parameter names.
     */
    @Tool("Call this tool when the user wants to save a question-and-answer pair or add new information to the knowledge base. Takes the question, the answer and the target file name as parameters.")
    public String addKnowledgeToRag(String question, String answer, String fileName) {
        log.info("Tool invoked: saving knowledge - Q: {}, file: {}", question, fileName);

        // 1. Format the entry
        String formattedContent = String.format("### Q: %s\n\nA: %s", question, answer);

        // 2. Normalise the file name (guard against a missing extension)
        if (fileName == null || fileName.isBlank()) {
            fileName = "InfiniteChat.md"; // default file
        }
        if (!fileName.endsWith(".md")) {
            fileName = fileName + ".md";
        }

        // 3. Append it to the file on disk
        boolean writeSuccess = appendToFile(formattedContent, fileName);
        if (!writeSuccess) {
            return "Save failed: could not write to the local file system, please check the logs.";
        }

        // 4. Store it in the vector database
        try {
            // Set the source metadata
            Metadata metadata = Metadata.from("file_name", fileName);

            // Build the document and embed it
            Document document = Document.from(formattedContent, metadata);
            embeddingStoreIngestor.ingest(document);

            log.info("Tool finished: the knowledge entry was synchronised to RAG");
            return "Done! The knowledge entry was saved to the document [" + fileName + "] and synchronised to the vector database.";
        } catch (Exception e) {
            log.error("RAG - embedding failed", e);
            return "The file was written, but the vector database update failed: " + e.getMessage();
        }
    }

    /**
     * Helper that appends the entry to a file
     */
    private synchronized boolean appendToFile(String content, String fileName) {
        try {
            Path filePath = Paths.get(docsPath, fileName);
            
            // Create the file first if it does not exist
            if (!Files.exists(filePath)) {
                if (filePath.getParent() != null) {
                    Files.createDirectories(filePath.getParent());
                }
                Files.createFile(filePath);
                log.info("Tool created new file: {}", filePath.toAbsolutePath());
            }

            // Wrap it in newlines
            String textToAppend = "\n\n" + content;

            Files.writeString(
                    filePath,
                    textToAppend,
                    StandardOpenOption.APPEND
            );
            return true;
        } catch (IOException e) {
            log.error("RAG Tool - failed to write the file: {}", e.getMessage(), e);
            return false;
        }
    }
}