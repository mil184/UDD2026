package com.example.ddmdemo.service.impl;

import ai.djl.translate.TranslateException;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.ddmdemo.exceptionhandling.exception.MalformedQueryException;
import com.example.ddmdemo.indexmodel.DummyIndex;
import com.example.ddmdemo.indexmodel.MalwareAnalysisIndex;
import com.example.ddmdemo.service.interfaces.SearchService;
import com.example.ddmdemo.util.VectorizationUtil;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import joptsimple.internal.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.unit.Fuzziness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final ElasticsearchOperations elasticsearchTemplate;


    @Override
    public Page<DummyIndex> simpleSearch(List<String> keywords, Pageable pageable, boolean isKNN) {
        if (isKNN) {
            try {
                return searchByVector(VectorizationUtil.getEmbedding(Strings.join(keywords, " ")));
            } catch (TranslateException e) {
                log.error("Vectorization failed");
                return Page.empty();
            }
        }

//        System.out.println(buildSimpleSearchQuery(keywords).toString());
        var searchQueryBuilder =
            new NativeQueryBuilder().withQuery(buildSimpleSearchQuery(keywords))
                .withPageable(pageable);

        return runQuery(searchQueryBuilder.build());
    }

    public Page<DummyIndex> searchByVector(float[] queryVector) {
        Float[] floatObjects = new Float[queryVector.length];
        for (int i = 0; i < queryVector.length; i++) {
            floatObjects[i] = queryVector[i];
        }
        List<Float> floatList = Arrays.stream(floatObjects).collect(Collectors.toList());

        var knnQuery = new KnnQuery.Builder()
            .field("vectorizedContent")
            .queryVector(floatList)
            .numCandidates(100)
            .k(10)
            .boost(10.0f)
            .build();

        var searchQuery = NativeQuery.builder()
            .withKnnQuery(knnQuery)
            .withMaxResults(5)
            .withSearchType(null)
            .build();

        var searchHitsPaged =
            SearchHitSupport.searchPageFor(
                elasticsearchTemplate.search(searchQuery, DummyIndex.class),
                searchQuery.getPageable());

        return (Page<DummyIndex>) SearchHitSupport.unwrapSearchHits(searchHitsPaged);
    }

    @Override
    public Page<DummyIndex> advancedSearch(List<String> expression, Pageable pageable) {
        if (expression.size() != 3) {
            throw new MalformedQueryException("Search query malformed.");
        }

        String operation = expression.get(1);
        expression.remove(1);
        var searchQueryBuilder =
            new NativeQueryBuilder().withQuery(buildAdvancedSearchQuery(expression, operation))
                .withPageable(pageable);

        return runQuery(searchQueryBuilder.build());
    }

    private Query buildSimpleSearchQuery(List<String> tokens) {
        return BoolQuery.of(q -> q.must(mb -> mb.bool(b -> {
            tokens.forEach(token -> {
                // Term Query - simplest
                // Matches documents with exact term in "title" field
                b.should(sb -> sb.term(m -> m.field("title").value(token)));

                // Terms Query
                // Matches documents with any of the specified terms in "title" field
//                var terms = new ArrayList<>(List.of("dummy1", "dummy2"));
//                var titleTerms = new TermsQueryField.Builder()
//                    .value(terms.stream().map(FieldValue::of).toList())
//                    .build();
//                b.should(sb -> sb.terms(m -> m.field("title").terms(titleTerms)));

                // Match Query - full-text search with fuzziness
                // Matches documents with fuzzy matching in "title" field
                b.should(sb -> sb.match(
                    m -> m.field("title").fuzziness(Fuzziness.ONE.asString()).query(token)));

                // Match Query - full-text search in other fields
                // Matches documents with full-text search in other fields
                b.should(sb -> sb.match(m -> m.field("content_sr").query(token).boost(0.5f)));
                b.should(sb -> sb.match(m -> m.field("content_en").query(token)));

                // Wildcard Query - unsafe
                // Matches documents with wildcard matching in "title" field
                b.should(sb -> sb.wildcard(m -> m.field("title").value("*" + token + "*")));

                // Regexp Query - unsafe
                // Matches documents with regular expression matching in "title" field
//                b.should(sb -> sb.regexp(m -> m.field("title").value(".*" + token + ".*")));

                // Boosting Query - positive gives better score, negative lowers score
                // Matches documents with boosted relevance in "title" field
                b.should(sb -> sb.boosting(bq -> bq.positive(m -> m.match(ma -> ma.field("title").query(token)))
                                              .negative(m -> m.match(ma -> ma.field("description").query(token)))
                                              .negativeBoost(0.5f)));

                // Match Phrase Query - useful for exact-phrase search
                // Matches documents with exact phrase match in "title" field
                b.should(sb -> sb.matchPhrase(m -> m.field("title").query(token)));

                // Fuzzy Query - similar to Match Query with fuzziness, useful for spelling errors
                // Matches documents with fuzzy matching in "title" field
                b.should(sb -> sb.match(
                    m -> m.field("title").fuzziness(Fuzziness.ONE.asString()).query(token)));

                // Range query - not applicable for dummy index, searches in the range from-to

                // More Like This query - finds documents similar to the provided text
//                b.should(sb -> sb.moreLikeThis(mlt -> mlt
//                    .fields("title")
//                    .like(like -> like.text(token))
//                    .minTermFreq(1)
//                    .minDocFreq(1)));
            });
            return b;
        })))._toQuery();
    }

    private Query buildAdvancedSearchQuery(List<String> operands, String operation) {
        return BoolQuery.of(q -> q.must(mb -> mb.bool(b -> {
            var field1 = operands.get(0).split(":")[0];
            var value1 = operands.get(0).split(":")[1];
            var field2 = operands.get(1).split(":")[0];
            var value2 = operands.get(1).split(":")[1];

            switch (operation) {
                case "AND":
                    b.must(sb -> sb.match(
                        m -> m.field(field1).fuzziness(Fuzziness.ONE.asString()).query(value1)));
                    b.must(sb -> sb.match(m -> m.field(field2).query(value2)));
                    break;
                case "OR":
                    b.should(sb -> sb.match(
                        m -> m.field(field1).fuzziness(Fuzziness.ONE.asString()).query(value1)));
                    b.should(sb -> sb.match(m -> m.field(field2).query(value2)));
                    break;
                case "NOT":
                    b.must(sb -> sb.match(
                        m -> m.field(field1).fuzziness(Fuzziness.ONE.asString()).query(value1)));
                    b.mustNot(sb -> sb.match(m -> m.field(field2).query(value2)));
                    break;
            }
            return b;
        })))._toQuery();
    }

    private Page<DummyIndex> runQuery(NativeQuery searchQuery) {

        var searchHits = elasticsearchTemplate.search(searchQuery, DummyIndex.class,
            IndexCoordinates.of("dummy_index"));

        var searchHitsPaged = SearchHitSupport.searchPageFor(searchHits, searchQuery.getPageable());

        return (Page<DummyIndex>) SearchHitSupport.unwrapSearchHits(searchHitsPaged);
    }

    public Page<MalwareAnalysisIndex> searchReports(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return Page.empty(pageable);
        }

        String queryText = q.trim();

        NativeQuery searchQuery = new NativeQueryBuilder()
                .withQuery(buildSingleBarQuery(queryText))
                .withPageable(pageable)
                .build();

        return runMalwareQuery(searchQuery);
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

        // value without quotes if phrase
        String phraseValue = isPhrase ? raw.substring(1, raw.length() - 1).trim() : raw;
        if (phraseValue.isEmpty()) {
            throw new MalformedQueryException("Phrase query is empty.");
        }

        String lowered = phraseValue.toLowerCase().trim();

        // Phrase mode: NO fuzziness etc
        if (isPhrase) {
            return BoolQuery.of(b -> {
                // 1) Analyst full name (phrase only)
                b.should(s -> s.matchPhrase(mp -> mp.field("analystFullName").query(phraseValue).boost(3.0f)));

                // 2) Hash (term only)
                b.should(s -> s.term(t -> t.field("sampleHash").value(phraseValue).boost(5.0f)));
                b.should(s -> s.term(t -> t.field("sampleHash").value(lowered).boost(5.0f)));

                // 3) Threat classification (term + optional phrase-as-matchPhrase)
                b.should(s -> s.term(t -> t.field("threatClassification").value(phraseValue).boost(4.0f)));
                b.should(s -> s.term(t -> t.field("threatClassification").value(lowered).boost(4.0f)));
                b.should(s -> s.matchPhrase(mp -> mp.field("threatClassification").query(phraseValue).boost(1.5f)));

                // 4) Organization (phrase only)
                b.should(s -> s.matchPhrase(mp -> mp.field("securityOrganization").query(phraseValue).boost(2.5f)));
                b.should(s -> s.term(t -> t.field("securityOrganization").value(phraseValue).boost(3.0f)));
                b.should(s -> s.term(t -> t.field("securityOrganization").value(lowered).boost(3.0f)));

                // 5) Malware name (phrase only)
                b.should(s -> s.matchPhrase(mp -> mp.field("malwareName").query(phraseValue).boost(2.5f)));
                b.should(s -> s.term(t -> t.field("malwareName").value(phraseValue).boost(3.0f)));
                b.should(s -> s.term(t -> t.field("malwareName").value(lowered).boost(3.0f)));

                // 6) Description (phrase only)
                b.should(s -> s.matchPhrase(mp -> mp.field("behaviorDescription").query(phraseValue).boost(1.5f)));

                b.minimumShouldMatch("1");
                return b;
            })._toQuery();
        }

        // Normal mode: your existing logic (but use raw value)
        return BoolQuery.of(b -> {
            // 1) Analyst full name
            b.should(s -> s.matchPhrase(mp -> mp.field("analystFullName").query(raw).boost(3.0f)));
            b.should(s -> s.match(m -> m.field("analystFullName")
                    .query(raw)
                    .fuzziness(Fuzziness.ONE.asString())
                    .boost(2.0f)
            ));

            // 2) Hash
            b.should(s -> s.term(t -> t.field("sampleHash").value(raw).boost(5.0f)));
            b.should(s -> s.term(t -> t.field("sampleHash").value(raw.toLowerCase().trim()).boost(5.0f)));

            // 3) Threat classification
            b.should(s -> s.term(t -> t.field("threatClassification").value(raw).boost(4.0f)));
            b.should(s -> s.term(t -> t.field("threatClassification").value(raw.toLowerCase().trim()).boost(4.0f)));
            b.should(s -> s.match(m -> m.field("threatClassification").query(raw).boost(1.5f)));

            // 4) Organization
            b.should(s -> s.matchPhrase(mp -> mp.field("securityOrganization").query(raw).boost(2.5f)));
            b.should(s -> s.match(m -> m.field("securityOrganization")
                    .query(raw)
                    .fuzziness(Fuzziness.ONE.asString())
                    .boost(2.0f)
            ));
            b.should(s -> s.term(t -> t.field("securityOrganization").value(raw).boost(3.0f)));
            b.should(s -> s.term(t -> t.field("securityOrganization").value(raw.toLowerCase().trim()).boost(3.0f)));

            // 5) Malware name
            b.should(s -> s.matchPhrase(mp -> mp.field("malwareName").query(raw).boost(2.5f)));
            b.should(s -> s.match(m -> m.field("malwareName")
                    .query(raw)
                    .fuzziness(Fuzziness.ONE.asString())
                    .boost(2.0f)
            ));
            b.should(s -> s.term(t -> t.field("malwareName").value(raw).boost(3.0f)));
            b.should(s -> s.term(t -> t.field("malwareName").value(raw.toLowerCase().trim()).boost(3.0f)));

            // 6) Description
            b.should(s -> s.match(m -> m.field("behaviorDescription").query(raw).boost(1.0f)));
            b.should(s -> s.matchPhrase(mp -> mp.field("behaviorDescription").query(raw).boost(1.5f)));

            b.minimumShouldMatch("1");
            return b;
        })._toQuery();
    }

    private Page<MalwareAnalysisIndex> runMalwareQuery(NativeQuery searchQuery) {
        var hits = elasticsearchTemplate.search(
                searchQuery,
                MalwareAnalysisIndex.class,
                IndexCoordinates.of("malware_analysis")
        );

        var paged = SearchHitSupport.searchPageFor(hits, searchQuery.getPageable());
        return (Page<MalwareAnalysisIndex>) SearchHitSupport.unwrapSearchHits(paged);
    }
}
