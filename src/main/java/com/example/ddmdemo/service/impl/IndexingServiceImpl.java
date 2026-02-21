package com.example.ddmdemo.service.impl;

import ai.djl.translate.TranslateException;
import com.example.ddmdemo.dto.IndexDocumentResponseDTO;
import com.example.ddmdemo.exceptionhandling.exception.LoadingException;
import com.example.ddmdemo.exceptionhandling.exception.StorageException;
import com.example.ddmdemo.indexmodel.DummyIndex;
import com.example.ddmdemo.indexrepository.DummyIndexRepository;
import com.example.ddmdemo.model.DummyTable;
import com.example.ddmdemo.model.MalwareAnalysis;
import com.example.ddmdemo.respository.DummyRepository;
import com.example.ddmdemo.service.interfaces.FileService;
import com.example.ddmdemo.service.interfaces.IndexingService;
import com.example.ddmdemo.util.MalwareAnalysisParser;
import com.example.ddmdemo.util.VectorizationUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.language.detect.LanguageDetector;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final DummyIndexRepository dummyIndexRepository;

    private final DummyRepository dummyRepository;

    private final FileService fileService;

    private final LanguageDetector languageDetector;

    private final MalwareAnalysisParser malwareAnalysisParser;


    @Override
    @Transactional
    public MalwareAnalysis indexDocument(MultipartFile documentFile) {
        var newEntity = new DummyTable();
        var newIndex = new DummyIndex();

        var title = Objects.requireNonNull(documentFile.getOriginalFilename()).split("\\.")[0];
        newIndex.setTitle(title);
        newEntity.setTitle(title);

        // 1) Extract text
        var documentContent = extractDocumentContent(documentFile);

        // 2) Parse malware analysis fields
        var analysis = malwareAnalysisParser.parse(documentContent);
        log.info("Parsed MalwareAnalysis from report '{}': {}", title, analysis);

        // Keep SR/EN logic
        if (detectLanguage(documentContent).equals("SR")) {
            newIndex.setContentSr(documentContent);
        } else {
            newIndex.setContentEn(documentContent);
        }
        newEntity.setContent(documentContent);

        // Store file
        var serverFilename = fileService.store(documentFile, UUID.randomUUID().toString());
        newIndex.setServerFilename(serverFilename);
        newEntity.setServerFilename(serverFilename);

        // Mime type + save entity
        newEntity.setMimeType(detectMimeType(documentFile));
        var savedEntity = dummyRepository.save(newEntity);

        // Vectorize
        try {
            newIndex.setVectorizedContent(VectorizationUtil.getEmbedding(title));
        } catch (TranslateException e) {
            log.error("Could not calculate vector representation for document with ID: {}", savedEntity.getId(), e);
        }

        newIndex.setDatabaseId(savedEntity.getId());
        dummyIndexRepository.save(newIndex);

        return analysis;
    }

    private String extractDocumentContent(MultipartFile multipartPdfFile) {
        if (multipartPdfFile == null || multipartPdfFile.isEmpty()) {
            throw new LoadingException("PDF file is missing or empty.");
        }

        try (var pdfStream = multipartPdfFile.getInputStream();
             var pdDocument = PDDocument.load(pdfStream)) {

            // Optional: remove this if you expect encrypted PDFs and handle them differently
            if (pdDocument.isEncrypted()) {
                throw new LoadingException("PDF file is encrypted and cannot be parsed.");
            }

            var stripper = new PDFTextStripper();

            // Keep reading order more natural for many reports
            stripper.setSortByPosition(true);

            // Optional: if you want all pages explicitly
            stripper.setStartPage(1);
            stripper.setEndPage(pdDocument.getNumberOfPages());

            var text = stripper.getText(pdDocument);

            if (text == null || text.isBlank()) {
                throw new LoadingException("PDF file contains no extractable text.");
            }

            return text.trim();
        } catch (IOException e) {
            throw new LoadingException("Error while trying to load PDF file content.");
        }
    }

    private String detectLanguage(String text) {
        var detectedLanguage = languageDetector.detect(text).getLanguage().toUpperCase();
        if (detectedLanguage.equals("HR")) {
            detectedLanguage = "SR";
        }

        return detectedLanguage;
    }

    private String detectMimeType(MultipartFile file) {
        var contentAnalyzer = new Tika();

        String trueMimeType;
        String specifiedMimeType;
        try {
            trueMimeType = contentAnalyzer.detect(file.getBytes());
            specifiedMimeType =
                Files.probeContentType(Path.of(Objects.requireNonNull(file.getOriginalFilename())));
        } catch (IOException e) {
            throw new StorageException("Failed to detect mime type for file.");
        }

        if (!trueMimeType.equals(specifiedMimeType) &&
            !(trueMimeType.contains("zip") && specifiedMimeType.contains("zip"))) {
            throw new StorageException("True mime type is different from specified one, aborting.");
        }

        return trueMimeType;
    }
}