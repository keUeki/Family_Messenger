package com.shanyangcode.aiservice.job;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * Loads the RAG documents at startup
 */
@Component
@Slf4j
public class RagDataLoader implements CommandLineRunner {

    @Value("${rag.docs-path}")
    private String docsPath;

    @Resource
    private EmbeddingStoreIngestor embeddingStoreIngestor;

    @Override
    public void run(String... args) {
        log.info("RAG - loading the local base documents, path: {}", docsPath);
        try {

            List<Document> documents = FileSystemDocumentLoader.loadDocuments(docsPath);

            if (!documents.isEmpty()) {
                embeddingStoreIngestor.ingest(documents);
                log.info("RAG - finished loading the local documents; {} document(s) loaded", documents.size());
            } else {
                log.warn("RAG - no documents found at the configured path");
            }
        } catch (Exception e) {
            log.error("RAG - failed to load the local documents", e);
        }
    }
}