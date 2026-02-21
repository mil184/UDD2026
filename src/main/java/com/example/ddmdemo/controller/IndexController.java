package com.example.ddmdemo.controller;

import com.example.ddmdemo.dto.DummyDocumentFileDTO;
import com.example.ddmdemo.dto.DummyDocumentFileResponseDTO;
import com.example.ddmdemo.model.MalwareAnalysis;
import com.example.ddmdemo.service.interfaces.IndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/index")
@RequiredArgsConstructor
public class IndexController {

    private final IndexingService indexingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MalwareAnalysis addDocumentFile(@ModelAttribute DummyDocumentFileDTO documentFile) {
        return indexingService.indexDocument(documentFile.file());
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public MalwareAnalysis confirmAndIndex(@RequestBody MalwareAnalysis analysis) {
        return indexingService.confirmAndIndex(analysis);
    }
}
