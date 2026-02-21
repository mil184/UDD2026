package com.example.ddmdemo.service.interfaces;

import com.example.ddmdemo.dto.IndexDocumentResponseDTO;
import com.example.ddmdemo.model.MalwareAnalysis;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface IndexingService {

    MalwareAnalysis indexDocument(MultipartFile documentFile);
}
