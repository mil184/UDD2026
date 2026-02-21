package com.example.ddmdemo.dto;

import com.example.ddmdemo.model.MalwareAnalysis;

public record IndexDocumentResponseDTO(
        Long databaseId,
        String serverFilename,
        MalwareAnalysis analysis
) {}