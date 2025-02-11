package org.mskcc.cbio.oncokb.api.pvt;

import io.swagger.annotations.ApiParam;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.apiModels.CancerTypeMatch;
import org.mskcc.cbio.oncokb.apiModels.DrugMatch;
import org.mskcc.cbio.oncokb.apiModels.LevelsOfEvidenceMatch;
import org.mskcc.cbio.oncokb.bo.OncokbTranscriptService;
import org.mskcc.cbio.oncokb.cache.CacheFetcher;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.genomeNexus.TranscriptSummaryAlterationResult;
import org.mskcc.cbio.oncokb.util.*;
import org.oncokb.oncokb_transcript.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.Constants.DEFAULT_REFERENCE_GENOME;
import static org.mskcc.cbio.oncokb.util.AlterationUtils.GENOMIC_CHANGE_FORMAT;
import static org.mskcc.cbio.oncokb.util.AlterationUtils.HGVSG_FORMAT;
import static org.mskcc.cbio.oncokb.util.AnnotationSearchUtils.newTypeaheadAnnotation;
import static org.mskcc.cbio.oncokb.util.AnnotationSearchUtils.searchNonHgvsAnnotation;
import static org.mskcc.cbio.oncokb.util.LevelUtils.*;

/**
 * Created by Hongxin on 10/28/16.
 */
@Controller
public class PrivateSearchApiController implements PrivateSearchApi {
    private Integer DEFAULT_RETURN_LIMIT = 5;

    @Autowired
    CacheFetcher cacheFetcher;

    @Override
    public ResponseEntity<Set<BiologicalVariant>> searchVariantsBiologicalGet(
        @ApiParam(value = "") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
    ) {
        Set<BiologicalVariant> variants = new HashSet<>();
        HttpStatus status = HttpStatus.OK;

        if (hugoSymbol != null) {
            Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
            variants = MainUtils.getBiologicalVariants(gene);
        }
        return new ResponseEntity<>(variants, status);
    }

    @Override
    public ResponseEntity<Set<ClinicalVariant>> searchVariantsClinicalGet(
        @ApiParam(value = "") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
    ) {
        HttpStatus status = HttpStatus.OK;
        Set<ClinicalVariant> variants = new HashSet<>();

        if (hugoSymbol != null) {
            Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
            variants = MainUtils.getClinicalVariants(gene);
        }
        return new ResponseEntity<>(variants, status);
    }

    @Override
    public ResponseEntity<Set<Treatment>> searchTreatmentsGet(
        @ApiParam(value = "The search query, it could be hugoSymbol or entrezGeneId.", required = true) @RequestParam(value = "gene", required = false) String queryGene
        , @ApiParam(value = "The level of evidence.", defaultValue = "false") @RequestParam(value = "level", required = false) String queryLevel) {
        HttpStatus status = HttpStatus.OK;
        Gene gene = GeneUtils.getGene(queryGene);
        Set<Treatment> treatments = new HashSet<>();

        if (gene == null && queryLevel == null) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            if (queryLevel == null) {
                treatments = TreatmentUtils.getTreatmentsByGene(gene);
            } else {
                LevelOfEvidence level = LevelOfEvidence.getByLevel(queryLevel);
                if (level == null) {
                    status = HttpStatus.BAD_REQUEST;
                } else if (!LevelUtils.getPublicLevels().contains(level)) {
                    status = HttpStatus.BAD_REQUEST;
                } else if (gene == null) {
                    treatments = TreatmentUtils.getTreatmentsByLevels(Collections.singleton(level));
                } else {
                    treatments = TreatmentUtils.getTreatmentsByGeneAndLevels(gene, Collections.singleton(level));
                }
            }
        }
        return new ResponseEntity<>(treatments, status);
    }

    @Override
    public ResponseEntity<LinkedHashSet<TypeaheadSearchResp>> searchTypeAheadGet(
        @ApiParam(value = "The search query, it could be hugoSymbol, entrezGeneId, variant, or cancer type. At least two characters. Maximum two keywords are supported, separated by space", required = true) @RequestParam(value = "query") String query,
        @ApiParam(value = "The limit of returned result.") @RequestParam(value = "limit", defaultValue = "5", required = false) Integer limit) {
        final int QUERY_MIN_LENGTH = 2;
        LinkedHashSet<TypeaheadSearchResp> result = new LinkedHashSet<>();
        if (limit == null) {
            limit = DEFAULT_RETURN_LIMIT;
        }
        if (query != null && query.length() >= QUERY_MIN_LENGTH) {
            // genomic queries will not have space in the query
            String trimmedQuery = query.trim().replaceAll(" ", "");
            if (AlterationUtils.isValidHgvsg(trimmedQuery) || AlterationUtils.isValidGenomicChange(trimmedQuery)) {
                GNVariantAnnotationType type = null;

                if (AlterationUtils.isValidHgvsg(trimmedQuery)) {
                    type = GNVariantAnnotationType.HGVS_G;
                } else if (AlterationUtils.isValidGenomicChange(trimmedQuery)) {
                    type = GNVariantAnnotationType.GENOMIC_LOCATION;
                }
                if (type != null) {
                    TranscriptSummaryAlterationResult transcriptSummaryAlterationResult;
                    String refGenomeStr = "";
                    ReferenceGenome referenceGenome = ReferenceGenome.GRCh37;
                    Matcher rgm = (GNVariantAnnotationType.HGVS_G.equals(type) ? HGVSG_FORMAT : GENOMIC_CHANGE_FORMAT).matcher(trimmedQuery);
                    if (rgm.find()) {
                        refGenomeStr = rgm.group(2);
                        trimmedQuery = rgm.group(3);
                    }
                    if (StringUtils.isNotEmpty(refGenomeStr)) {
                        ReferenceGenome matchedReferenceGenome = MainUtils.searchEnum(ReferenceGenome.class, refGenomeStr);
                        if (matchedReferenceGenome != null) {
                            referenceGenome = matchedReferenceGenome;
                        }
                    }
                    try {
                        transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(type, referenceGenome, trimmedQuery);
                        if (transcriptSummaryAlterationResult.getAlteration().getGene() != null) {
                            Query annotationQuery = QueryUtils.getQueryFromAlteration(referenceGenome, "", transcriptSummaryAlterationResult, HGVSG_FORMAT.equals(type) ? trimmedQuery : "");
                            IndicatorQueryResp indicatorQueryResp = IndicatorUtils.processQuery(annotationQuery, null, false, null, true);
                            result.add(newTypeaheadAnnotation(trimmedQuery, type, referenceGenome, transcriptSummaryAlterationResult.getAlteration(), indicatorQueryResp));
                        }
                    } catch (org.genome_nexus.ApiException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else {
                result.addAll(searchNonHgvsAnnotation(query));
            }
        } else {
            TypeaheadSearchResp typeaheadSearchResp = new TypeaheadSearchResp();
            typeaheadSearchResp.setAnnotation("Please search at least " + QUERY_MIN_LENGTH + " characters.");
            typeaheadSearchResp.setQueryType(TypeaheadQueryType.TEXT);
            result.add(typeaheadSearchResp);
        }
        return new ResponseEntity<>(MainUtils.getLimit(result, limit), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<LinkedHashSet<org.oncokb.oncokb_transcript.client.Drug>> searchDrugGet(
        @ApiParam(value = "The search query, it could be drug name, NCIT code", required = true) @RequestParam(value = "query") String query,
        @ApiParam(value = "The limit of returned result.") @RequestParam(value = "limit", defaultValue = "5", required = false) Integer limit
    ) throws ApiException {
        if (limit == null) {
            limit = DEFAULT_RETURN_LIMIT;
        }
        OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();
        return new ResponseEntity<>(MainUtils.getLimit(new LinkedHashSet<>(oncokbTranscriptService.findDrugs(query)), limit), HttpStatus.OK);
    }
}
