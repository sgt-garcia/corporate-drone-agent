package ai.corporatedroneagent.service;

import ai.corporatedroneagent.config.StorageProperties;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceChunk;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceIndex;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexingService {

    private static final String FIELD_DOCUMENT_ID = "documentId";
    private static final String FIELD_RESOURCE_ID = "resourceId";
    private static final String FIELD_CHUNK_ID = "chunkId";
    private static final String FIELD_CHUNK_INDEX = "chunkIndex";
    private static final String FIELD_RESOURCE_REFERENCE = "resourceReference";
    private static final String FIELD_CONTENT = "content";

    private final KnowledgeResourcePipelineRepository pipelineRepository;
    private final Path indexPath;

    public KnowledgeIndexingService(
            KnowledgeResourcePipelineRepository pipelineRepository,
            StorageProperties storageProperties
    ) {
        this.pipelineRepository = pipelineRepository;
        this.indexPath = storageProperties.getRoot().resolve("lucene");
    }

    public synchronized void index(
            KnowledgeResource resource,
            KnowledgeResourceConversion conversion,
            List<KnowledgeResourceChunk> chunks
    ) {
        if (!Boolean.TRUE.equals(conversion.getSuccess()) || chunks.isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(indexPath);
            try (StandardAnalyzer analyzer = new StandardAnalyzer();
                    FSDirectory directory = FSDirectory.open(indexPath);
                    IndexWriter writer = new IndexWriter(directory, indexWriterConfig(analyzer))) {
                for (KnowledgeResourceChunk chunk : chunks) {
                    indexChunk(writer, resource, conversion, chunk);
                }
                writer.commit();
            }
        } catch (IOException | RuntimeException exception) {
            chunks.forEach(chunk -> markFailed(chunk, "Could not index resource"));
        }
    }

    public synchronized void deleteResource(KnowledgeResource resource) {
        try {
            Files.createDirectories(indexPath);
            try (StandardAnalyzer analyzer = new StandardAnalyzer();
                    FSDirectory directory = FSDirectory.open(indexPath);
                    IndexWriter writer = new IndexWriter(directory, indexWriterConfig(analyzer))) {
                writer.deleteDocuments(new Term(FIELD_RESOURCE_ID, resource.getId().toString()));
                writer.commit();
            }
        } catch (IOException | RuntimeException exception) {
            // A cleanup miss should not prevent the scan from recording the current filesystem state.
        }
    }

    public List<String> searchTerm(String term, int limit) {
        String normalizedTerm = term == null ? "" : term.toLowerCase(Locale.ROOT).trim();
        if (normalizedTerm.isEmpty() || limit <= 0 || !Files.exists(indexPath)) {
            return List.of();
        }

        try (FSDirectory directory = FSDirectory.open(indexPath);
                DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            StoredFields storedFields = reader.storedFields();
            ScoreDoc[] hits = searcher.search(
                    new TermQuery(new Term(FIELD_CONTENT, normalizedTerm)),
                    limit
            ).scoreDocs;
            List<String> documentIds = new ArrayList<>();
            for (ScoreDoc hit : hits) {
                Document document = storedFields.document(hit.doc);
                documentIds.add(document.get(FIELD_DOCUMENT_ID));
            }
            return documentIds;
        } catch (IndexNotFoundException exception) {
            return List.of();
        } catch (IOException exception) {
            throw new KnowledgeIndexException("Could not search knowledge index", exception);
        }
    }

    private IndexWriterConfig indexWriterConfig(StandardAnalyzer analyzer) {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        return config;
    }

    private void indexChunk(
            IndexWriter writer,
            KnowledgeResource resource,
            KnowledgeResourceConversion conversion,
            KnowledgeResourceChunk chunk
    ) throws IOException {
        String documentId = documentId(resource, chunk);
        KnowledgeResourceIndex index = markInProgress(chunk, documentId);
        try {
            writer.updateDocument(
                    new Term(FIELD_DOCUMENT_ID, documentId),
                    document(resource, conversion, chunk, documentId)
            );
            index.setStatus(WorkStatus.DONE);
            index.setSuccess(true);
            index.setMessage("");
            index.setIndexedAt(Instant.now());
            pipelineRepository.saveIndex(index);
        } catch (IOException | RuntimeException exception) {
            index.setStatus(WorkStatus.DONE);
            index.setSuccess(false);
            index.setMessage("Could not index chunk");
            index.setIndexedAt(Instant.now());
            pipelineRepository.saveIndex(index);
            throw exception;
        }
    }

    private KnowledgeResourceIndex markInProgress(KnowledgeResourceChunk chunk, String documentId) {
        KnowledgeResourceIndex index = pipelineRepository.findIndexByChunkId(chunk.getId())
                .orElseGet(KnowledgeResourceIndex::new);
        index.setChunkId(chunk.getId());
        index.setStatus(WorkStatus.IN_PROGRESS);
        index.setSuccess(null);
        index.setMessage("");
        index.setIndexReference(documentId);
        index.setIndexedAt(null);
        return pipelineRepository.saveIndex(index);
    }

    private void markFailed(KnowledgeResourceChunk chunk, String message) {
        KnowledgeResourceIndex index = pipelineRepository.findIndexByChunkId(chunk.getId())
                .orElseGet(KnowledgeResourceIndex::new);
        index.setChunkId(chunk.getId());
        index.setStatus(WorkStatus.DONE);
        index.setSuccess(false);
        index.setMessage(message);
        index.setIndexedAt(Instant.now());
        pipelineRepository.saveIndex(index);
    }

    private Document document(
            KnowledgeResource resource,
            KnowledgeResourceConversion conversion,
            KnowledgeResourceChunk chunk,
            String documentId
    ) {
        Document document = new Document();
        document.add(new StringField(FIELD_DOCUMENT_ID, documentId, Field.Store.YES));
        document.add(new StringField(FIELD_RESOURCE_ID, resource.getId().toString(), Field.Store.YES));
        document.add(new StringField(FIELD_CHUNK_ID, chunk.getId().toString(), Field.Store.YES));
        document.add(new StringField(FIELD_CHUNK_INDEX, String.valueOf(chunk.getChunkIndex()), Field.Store.YES));
        document.add(new StringField(FIELD_RESOURCE_REFERENCE, resource.getReference(), Field.Store.YES));
        document.add(new TextField(
                FIELD_CONTENT,
                conversion.getValue().substring(chunk.getStartOffset(), chunk.getEndOffset()),
                Field.Store.NO
        ));
        return document;
    }

    private String documentId(KnowledgeResource resource, KnowledgeResourceChunk chunk) {
        return resource.getId() + ":" + chunk.getChunkIndex();
    }

    public static class KnowledgeIndexException extends RuntimeException {

        public KnowledgeIndexException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
