package com.example.ddmdemo.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.ddmdemo.exceptionhandling.exception.MalformedQueryException;
import com.example.ddmdemo.indexmodel.MalwareAnalysisIndex;
import com.example.ddmdemo.service.interfaces.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.unit.Fuzziness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchTemplate;

    public Page<MalwareAnalysisIndex> searchReports(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return Page.empty(pageable);
        }

        String queryText = q.trim();

        NativeQuery searchQuery = new NativeQueryBuilder()
                .withQuery(buildSingleBarQuery(queryText))
                .withHighlightQuery(buildHighlightQuery())
                .withPageable(pageable)
                .build();

        return runMalwareQuery(searchQuery);
    }

    private HighlightQuery buildHighlightQuery() {
        HighlightParameters params = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withFragmentSize(180)
                .withNumberOfFragments(3)
                .build();

        Highlight highlight = new Highlight(params,
                List.of(
                        new HighlightField("analystFullName"),
                        new HighlightField("sampleHash"),
                        new HighlightField("threatClassification"),
                        new HighlightField("securityOrganization"),
                        new HighlightField("malwareName"),
                        new HighlightField("behaviorDescriptionSr"),
                        new HighlightField("behaviorDescriptionEn")
                )
        );

        return new HighlightQuery(highlight, MalwareAnalysisIndex.class);
    }

    private Query buildSingleBarQuery(String q) {
        if (q == null) {
            throw new MalformedQueryException("Query is null.");
        }

        String raw = q.trim();
        if (raw.isEmpty()) {
            throw new MalformedQueryException("Query is empty.");
        }

        boolean isPhrase = raw.length() >= 2 && raw.startsWith("'") && raw.endsWith("'");

        String phraseValue = isPhrase ? raw.substring(1, raw.length() - 1).trim() : raw;
        if (phraseValue.isEmpty()) {
            throw new MalformedQueryException("Phrase query is empty.");
        }

        String lowered = phraseValue.toLowerCase().trim();

        if (isPhrase) {
            return BoolQuery.of(b -> {
                b.should(s -> s.matchPhrase(mp -> mp.field("analystFullName").query(phraseValue).boost(3.0f)));

                b.should(s -> s.term(t -> t.field("sampleHash").value(phraseValue).boost(5.0f)));
                b.should(s -> s.term(t -> t.field("sampleHash").value(lowered).boost(5.0f)));

                b.should(s -> s.term(t -> t.field("threatClassification").value(phraseValue).boost(4.0f)));
                b.should(s -> s.term(t -> t.field("threatClassification").value(lowered).boost(4.0f)));
                b.should(s -> s.matchPhrase(mp -> mp.field("threatClassification").query(phraseValue).boost(1.5f)));

                b.should(s -> s.matchPhrase(mp -> mp.field("securityOrganization").query(phraseValue).boost(2.5f)));
                b.should(s -> s.term(t -> t.field("securityOrganization").value(phraseValue).boost(3.0f)));
                b.should(s -> s.term(t -> t.field("securityOrganization").value(lowered).boost(3.0f)));

                b.should(s -> s.matchPhrase(mp -> mp.field("malwareName").query(phraseValue).boost(2.5f)));
                b.should(s -> s.term(t -> t.field("malwareName").value(phraseValue).boost(3.0f)));
                b.should(s -> s.term(t -> t.field("malwareName").value(lowered).boost(3.0f)));

                b.should(s -> s.matchPhrase(mp -> mp.field("behaviorDescriptionSr").query(phraseValue).boost(1.5f)));
                b.should(s -> s.matchPhrase(mp -> mp.field("behaviorDescriptionEn").query(phraseValue).boost(1.5f)));

                b.minimumShouldMatch("1");
                return b;
            })._toQuery();
        }

        return BoolQuery.of(b -> {
            b.should(s -> s.matchPhrase(mp -> mp.field("analystFullName").query(raw).boost(3.0f)));
            b.should(s -> s.match(m -> m.field("analystFullName")
                    .query(raw)
                    .fuzziness(Fuzziness.ONE.asString())
                    .boost(2.0f)
            ));

            b.should(s -> s.term(t -> t.field("sampleHash").value(raw).boost(5.0f)));
            b.should(s -> s.term(t -> t.field("sampleHash").value(raw.toLowerCase().trim()).boost(5.0f)));

            b.should(s -> s.term(t -> t.field("threatClassification").value(raw).boost(4.0f)));
            b.should(s -> s.term(t -> t.field("threatClassification").value(raw.toLowerCase().trim()).boost(4.0f)));
            b.should(s -> s.match(m -> m.field("threatClassification").query(raw).boost(1.5f)));

            b.should(s -> s.matchPhrase(mp -> mp.field("securityOrganization").query(raw).boost(2.5f)));
            b.should(s -> s.match(m -> m.field("securityOrganization")
                    .query(raw)
                    .fuzziness(Fuzziness.ONE.asString())
                    .boost(2.0f)
            ));
            b.should(s -> s.term(t -> t.field("securityOrganization").value(raw).boost(3.0f)));
            b.should(s -> s.term(t -> t.field("securityOrganization").value(raw.toLowerCase().trim()).boost(3.0f)));

            b.should(s -> s.matchPhrase(mp -> mp.field("malwareName").query(raw).boost(2.5f)));
            b.should(s -> s.match(m -> m.field("malwareName")
                    .query(raw)
                    .fuzziness(Fuzziness.ONE.asString())
                    .boost(2.0f)
            ));
            b.should(s -> s.term(t -> t.field("malwareName").value(raw).boost(3.0f)));
            b.should(s -> s.term(t -> t.field("malwareName").value(raw.toLowerCase().trim()).boost(3.0f)));

            b.should(s -> s.match(m -> m.field("behaviorDescriptionSr").query(raw).boost(1.2f)));
            b.should(s -> s.match(m -> m.field("behaviorDescriptionEn").query(raw).boost(1.0f)));
            b.should(s -> s.matchPhrase(mp -> mp.field("behaviorDescriptionSr").query(raw).boost(1.5f)));
            b.should(s -> s.matchPhrase(mp -> mp.field("behaviorDescriptionEn").query(raw).boost(1.5f)));

            b.minimumShouldMatch("1");
            return b;
        })._toQuery();
    }

    private Page<MalwareAnalysisIndex> runMalwareQuery(NativeQuery searchQuery) {
        SearchHits<MalwareAnalysisIndex> hits = elasticsearchTemplate.search(
                searchQuery,
                MalwareAnalysisIndex.class,
                IndexCoordinates.of("malware_analysis")
        );

        SearchPage<MalwareAnalysisIndex> searchPage =
                SearchHitSupport.searchPageFor(hits, searchQuery.getPageable());

        List<SearchHit<MalwareAnalysisIndex>> rawHits =
                searchPage.getSearchHits().getSearchHits();

        List<MalwareAnalysisIndex> content = new ArrayList<>(rawHits.size());
        for (SearchHit<MalwareAnalysisIndex> hit : rawHits) {
            MalwareAnalysisIndex doc = hit.getContent();
            doc.setHighlights(hit.getHighlightFields()); // Map<String, List<String>>
            content.add(doc);
        }

        return new PageImpl<>(content, searchQuery.getPageable(), searchPage.getTotalElements());
    }
}