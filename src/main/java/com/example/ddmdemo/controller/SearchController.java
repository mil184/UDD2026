package com.example.ddmdemo.controller;

import com.example.ddmdemo.dto.SearchQueryDTO;
import com.example.ddmdemo.indexmodel.DummyIndex;
import com.example.ddmdemo.indexmodel.MalwareAnalysisIndex;
import com.example.ddmdemo.service.interfaces.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
public class SearchController {

    private final SearchService searchService;

//    @PostMapping("/simple")
//    public Page<DummyIndex> simpleSearch(@RequestParam Boolean isKnn,
//                                         @RequestBody SearchQueryDTO simpleSearchQuery,
//                                         Pageable pageable) {
//        return searchService.simpleSearch(simpleSearchQuery.keywords(), pageable, isKnn);
//    }
//
//    @PostMapping("/advanced")
//    public Page<DummyIndex> advancedSearch(@RequestBody SearchQueryDTO advancedSearchQuery,
//                                           Pageable pageable) {
//        return searchService.advancedSearch(advancedSearchQuery.keywords(), pageable);
//    }

    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping()
    public Page<MalwareAnalysisIndex> search(
            @RequestParam("q") String q,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        return searchService.searchReports(q, pageable);
    }
}
