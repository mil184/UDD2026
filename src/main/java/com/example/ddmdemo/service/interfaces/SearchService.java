package com.example.ddmdemo.service.interfaces;

import com.example.ddmdemo.indexmodel.DummyIndex;
import java.util.List;

import com.example.ddmdemo.indexmodel.MalwareAnalysisIndex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public interface SearchService {

    Page<DummyIndex> simpleSearch(List<String> keywords, Pageable pageable, boolean isKNN);

    Page<DummyIndex> advancedSearch(List<String> expression, Pageable pageable);

    Page<MalwareAnalysisIndex> searchReports(String q, Pageable pageable);
}
