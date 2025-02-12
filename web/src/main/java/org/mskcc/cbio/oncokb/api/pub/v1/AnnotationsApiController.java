package org.mskcc.cbio.oncokb.api.pub.v1;

import static org.mskcc.cbio.oncokb.util.AnnotationSearchUtils.annotationSearch;

import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.genome_nexus.client.GenomicLocation;
import org.mskcc.cbio.oncokb.apiModels.VariantAnnotation;
import org.mskcc.cbio.oncokb.apiModels.annotation.*;
import org.mskcc.cbio.oncokb.cache.CacheFetcher;
import org.mskcc.cbio.oncokb.config.annotation.PremiumPublicApi;
import org.mskcc.cbio.oncokb.config.annotation.PublicApi;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.util.*;
import org.oncokb.oncokb_transcript.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.mskcc.cbio.oncokb.controller.advice.ApiHttpError;
import org.mskcc.cbio.oncokb.controller.advice.ApiHttpErrorException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Hongxin Zhang on 2019-03-25.
 */
@Api(tags = "Annotations", description = "Providing annotation services")
@Controller
public class AnnotationsApiController {
    final String EVIDENCE_TYPES_DESCRIPTION = "DEPRECATED. We do not recommend using this parameter and it will eventually be removed.";

    @Autowired
    CacheFetcher cacheFetcher;

    // Annotate mutations by protein change
    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate mutation by protein change.", response = IndicatorQueryResp.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/mutations/byProteinChange",
        produces = {"application/json"},
        method = RequestMethod.GET)
    public ResponseEntity<IndicatorQueryResp> annotateMutationsByProteinChangeGet(
        @ApiParam(value = "The gene symbol used in Human Genome Organisation. Example: BRAF") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "The entrez gene ID. (Higher priority than hugoSymbol). Example: 673") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "Protein Change. Example: V600E") @RequestParam(value = "alteration", required = false) String proteinChange
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "Consequence. Exacmple: missense_variant", allowableValues = "feature_truncation, frameshift_variant, inframe_deletion, inframe_insertion, start_lost, missense_variant, splice_region_variant, stop_gained, synonymous_variant, intron_variant") @RequestParam(value = "consequence", required = false) String consequence
        , @ApiParam(value = "Protein Start. Example: 600") @RequestParam(value = "proteinStart", required = false) Integer proteinStart
        , @ApiParam(value = "Protein End. Example: 600") @RequestParam(value = "proteinEnd", required = false) Integer proteinEnd
        , @ApiParam(value = "OncoTree(http://oncotree.info) tumor type name. The field supports OncoTree Code, OncoTree Name and OncoTree Main type. Example: Melanoma") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = EVIDENCE_TYPES_DESCRIPTION) @RequestParam(value = "evidenceType", required = false) String evidenceTypes
    ) throws ApiHttpErrorException {
        IndicatorQueryResp indicatorQueryResp = null;

        if (entrezGeneId != null && hugoSymbol != null && !GeneUtils.isSameGene(entrezGeneId, hugoSymbol)) {
            throw new ApiHttpErrorException("entrezGeneId \"" + entrezGeneId + "\"" + " and hugoSymbol \"" + hugoSymbol + "\" are not the same gene.", HttpStatus.BAD_REQUEST);
        } else {
            ReferenceGenome matchedRG = resolveMatchedRG(referenceGenome);
            Query query = new Query(null, matchedRG, entrezGeneId, hugoSymbol, proteinChange, null, null, tumorType, consequence, proteinStart, proteinEnd, null);
            indicatorQueryResp = this.cacheFetcher.processQuery(
                query.getReferenceGenome(),
                query.getEntrezGeneId(),
                query.getHugoSymbol(),
                query.getAlteration(),
                null,
                query.getTumorType(),
                query.getConsequence(),
                query.getProteinStart(),
                query.getProteinEnd(),
                null,
                null,
                null,
                false,
                new HashSet<>(MainUtils.stringToEvidenceTypes(evidenceTypes, ",")),
                false
            );
        }

        return new ResponseEntity<>(indicatorQueryResp, HttpStatus.OK);
    }

    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate mutations by protein change.", response = IndicatorQueryResp.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/mutations/byProteinChange",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    public ResponseEntity<List<IndicatorQueryResp>> annotateMutationsByProteinChangePost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody() List<AnnotateMutationByProteinChangeQuery> body
    ) throws ApiHttpErrorException {
        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        } 
        return new ResponseEntity<>(annotateMutationsByProteinChange(body), HttpStatus.OK);
    }

    // Annotate mutations by genomic change
    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate mutation by genomic change.", response = IndicatorQueryResp.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/mutations/byGenomicChange",
        produces = {"application/json"},
        method = RequestMethod.GET)
    public ResponseEntity<IndicatorQueryResp> annotateMutationsByGenomicChangeGet(
        @ApiParam(value = "Genomic location following TCGA MAF format. Example: 7,140453136,140453136,A,T", required = true) @RequestParam(value = "genomicLocation", required = true) String genomicLocation
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "OncoTree(http://oncotree.info) tumor type name. The field supports OncoTree Code, OncoTree Name and OncoTree Main type. Example: Melanoma") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = EVIDENCE_TYPES_DESCRIPTION) @RequestParam(value = "evidenceType", required = false) String evidenceTypes
    ) throws ApiException, org.genome_nexus.ApiException, ApiHttpErrorException {
        IndicatorQueryResp indicatorQueryResp = null;

        if (StringUtils.isEmpty(genomicLocation)) {
            throw new ApiHttpErrorException("genomicLocation is missing.", HttpStatus.BAD_REQUEST);
        }
        ReferenceGenome matchedRG = resolveMatchedRG(referenceGenome);
        List<Alteration> alterations = getAlterationsFromGenomeNexusByGenomicLocation(matchedRG, Collections.singletonList(GenomeNexusUtils.convertGenomicLocation(genomicLocation)));
        indicatorQueryResp = this.getIndicatorQueryFromGenomicLocation(
            matchedRG,
            alterations.isEmpty() ? new Alteration() : alterations.get(0),
            tumorType,
            new HashSet<>(MainUtils.stringToEvidenceTypes(evidenceTypes, ","))
        );
        return new ResponseEntity<>(indicatorQueryResp, HttpStatus.OK);
    }

    private ReferenceGenome resolveMatchedRG(String referenceGenome) throws ApiHttpErrorException {
        ReferenceGenome matchedRG = null;
        if (!StringUtils.isEmpty(referenceGenome)) {
            matchedRG = MainUtils.searchEnum(ReferenceGenome.class, referenceGenome);
            if (matchedRG == null) {
                throw new ApiHttpErrorException("referenceGenome \"" + referenceGenome + "\" is an invalid Reference Genome value.", HttpStatus.BAD_REQUEST);
            }
        }
        return matchedRG;
    }

    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate mutations by genomic change.", response = IndicatorQueryResp.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/mutations/byGenomicChange",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    public ResponseEntity<List<IndicatorQueryResp>> annotateMutationsByGenomicChangePost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody() List<AnnotateMutationByGenomicChangeQuery> body
    ) throws ApiException, org.genome_nexus.ApiException, ApiHttpErrorException {
        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(annotateMutationsByGenomicChange(body), HttpStatus.OK);
    }

    // Annotate mutations by HGVSg
    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate mutation by HGVSg.", response = IndicatorQueryResp.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/mutations/byHGVSg",
        produces = {"application/json"},
        method = RequestMethod.GET)
    public ResponseEntity<IndicatorQueryResp> annotateMutationsByHGVSgGet(
        @ApiParam(value = "HGVS genomic format following HGVS nomenclature. Example: 7:g.140453136A>T", required = true) @RequestParam(value = "hgvsg", required = true) String hgvsg
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "OncoTree(http://oncotree.info) tumor type name. The field supports OncoTree Code, OncoTree Name and OncoTree Main type. Example: Melanoma") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = EVIDENCE_TYPES_DESCRIPTION) @RequestParam(value = "evidenceType", required = false) String evidenceTypes
    ) throws ApiException, org.genome_nexus.ApiException, ApiHttpErrorException {
        IndicatorQueryResp indicatorQueryResp = null;

        if (StringUtils.isEmpty(hgvsg)) {
            throw new ApiHttpErrorException("hgvsg is missing.", HttpStatus.BAD_REQUEST);
        } else {
            ReferenceGenome matchedRG = resolveMatchedRG(referenceGenome);

            if (!AlterationUtils.isValidHgvsg(hgvsg)) {
                throw new ApiHttpErrorException("hgvsg is invalid.", HttpStatus.BAD_REQUEST);
            }

            List<Alteration> alterations = getAlterationsFromGenomeNexusByHgvs(matchedRG, Collections.singletonList(hgvsg));
            Alteration alteration = alterations.isEmpty() ? new Alteration() : alterations.get(0);
            indicatorQueryResp = this.getIndicatorQueryFromHGVS(
                matchedRG,
                alteration,
                hgvsg,
                tumorType,
                new HashSet<>(MainUtils.stringToEvidenceTypes(evidenceTypes, ","))
            );
        }
        return new ResponseEntity<>(indicatorQueryResp, HttpStatus.OK);
    }

    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate mutations by HGVSg.", response = IndicatorQueryResp.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/mutations/byHGVSg",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    public ResponseEntity<List<IndicatorQueryResp>> annotateMutationsByHGVSgPost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody() List<AnnotateMutationByHGVSQuery> body
    ) throws ApiException, org.genome_nexus.ApiException, ApiHttpErrorException {
        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(annotateMutationsByHGVS(body), HttpStatus.OK);
    }

    // Annotate copy number alterations
    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate copy number alteration.", response = IndicatorQueryResp.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/copyNumberAlterations",
        produces = {"application/json"},
        method = RequestMethod.GET)
    public ResponseEntity<IndicatorQueryResp> annotateCopyNumberAlterationsGet(
        @ApiParam(value = "The gene symbol used in Human Genome Organisation. Example: BRAF") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "The entrez gene ID. (Higher priority than hugoSymbol). Example: 673") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "Copy number alteration type", required = true) @RequestParam(value = "copyNameAlterationType", required = true) CopyNumberAlterationType copyNameAlterationType
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "OncoTree(http://oncotree.info) tumor type name. The field supports OncoTree Code, OncoTree Name and OncoTree Main type. Example: Melanoma") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = EVIDENCE_TYPES_DESCRIPTION) @RequestParam(value = "evidenceType", required = false) String evidenceTypes
    ) throws ApiHttpErrorException {
        IndicatorQueryResp indicatorQueryResp = null;

        if (entrezGeneId != null && hugoSymbol != null && !GeneUtils.isSameGene(entrezGeneId, hugoSymbol)) {
            throw new ApiHttpErrorException("entrezGeneId \"" + entrezGeneId + "\"" + " and hugoSymbol \"" + hugoSymbol + "\" are not the same gene.", HttpStatus.BAD_REQUEST);
        } else {
            if (copyNameAlterationType == null) {
                throw new ApiHttpErrorException("copyNameAlterationType is missing.", HttpStatus.BAD_REQUEST);
            }
            ReferenceGenome matchedRG = resolveMatchedRG(referenceGenome);
            indicatorQueryResp = this.cacheFetcher.processQuery(
                matchedRG,
                entrezGeneId,
                hugoSymbol,
                StringUtils.capitalize(copyNameAlterationType.name().toLowerCase()),
                null,
                tumorType,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                new HashSet<>(MainUtils.stringToEvidenceTypes(evidenceTypes, ",")),
                false);
        }
        return new ResponseEntity<>(indicatorQueryResp, HttpStatus.OK);
    }

    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate copy number alterations.", response = IndicatorQueryResp.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/copyNumberAlterations",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    public ResponseEntity<List<IndicatorQueryResp>> annotateCopyNumberAlterationsPost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody() List<AnnotateCopyNumberAlterationQuery> body
    ) throws ApiHttpErrorException {
        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(annotateCopyNumberAlterations(body), HttpStatus.OK);
    }

    // Annotate structural variants
    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate structural variant.", response = IndicatorQueryResp.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/structuralVariants",
        produces = {"application/json"},
        method = RequestMethod.GET)
    public ResponseEntity<IndicatorQueryResp> annotateStructuralVariantsGet(
        @ApiParam(value = "The gene symbol A used in Human Genome Organisation. Example: ABL1") @RequestParam(value = "hugoSymbolA", required = false) String hugoSymbolA
        , @ApiParam(value = "The entrez gene ID A. (Higher priority than hugoSymbolA) Example: 25") @RequestParam(value = "entrezGeneIdA", required = false) Integer entrezGeneIdA
        , @ApiParam(value = "The gene symbol B used in Human Genome Organisation.Example: BCR ") @RequestParam(value = "hugoSymbolB", required = false) String hugoSymbolB
        , @ApiParam(value = "The entrez gene ID B. (Higher priority than hugoSymbolB) Example: 613") @RequestParam(value = "entrezGeneIdB", required = false) Integer entrezGeneIdB
        , @ApiParam(value = "Structural variant type", required = true) @RequestParam(value = "structuralVariantType", required = true) StructuralVariantType structuralVariantType
        , @ApiParam(value = "Whether is functional fusion", required = true) @RequestParam(value = "isFunctionalFusion", defaultValue = "FALSE", required = true) Boolean isFunctionalFusion
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "OncoTree(http://oncotree.info) tumor type name. The field supports OncoTree Code, OncoTree Name and OncoTree Main type. Example: Melanoma") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = EVIDENCE_TYPES_DESCRIPTION) @RequestParam(value = "evidenceType", required = false) String evidenceTypes
    ) throws ApiHttpErrorException {
        IndicatorQueryResp indicatorQueryResp = null;

        if (structuralVariantType == null) {
            throw new ApiHttpErrorException("structuralVariantType is missing.", HttpStatus.BAD_REQUEST);
        } else if (isFunctionalFusion == null) {
            throw new ApiHttpErrorException("isFunctionalFusion is missing.", HttpStatus.BAD_REQUEST);
        }
        if (entrezGeneIdA != null && hugoSymbolA != null && !GeneUtils.isSameGene(entrezGeneIdA, hugoSymbolA)) {
            throw new ApiHttpErrorException("entrezGeneIdA \"" + entrezGeneIdA + "\"" + " and hugoSymbolA \"" + hugoSymbolA + "\" are not the same gene.", HttpStatus.BAD_REQUEST);
        } else if (entrezGeneIdB != null && hugoSymbolB != null && !GeneUtils.isSameGene(entrezGeneIdB, hugoSymbolB)) {
            throw new ApiHttpErrorException("entrezGeneIdB \"" + entrezGeneIdB + "\"" + " and hugoSymbolB \"" + hugoSymbolB + "\" are not the same gene.", HttpStatus.BAD_REQUEST);
        } else {
            Gene geneA = new Gene();
            try {
                geneA = this.cacheFetcher.findGeneBySymbol(entrezGeneIdA == null ? hugoSymbolA : entrezGeneIdA.toString());
                if (geneA == null) {
                    geneA = new Gene();
                }
            } catch (ApiException e) {
            }
            if (geneA.getEntrezGeneId() == null && StringUtils.isEmpty(geneA.getHugoSymbol())) {
                geneA.setEntrezGeneId(entrezGeneIdA);
                geneA.setHugoSymbol(hugoSymbolA == null ? "" : hugoSymbolA);
            }
            Gene geneB = new Gene();
            try {
                geneB = this.cacheFetcher.findGeneBySymbol(entrezGeneIdB == null ? hugoSymbolB : entrezGeneIdB.toString());
                if (geneB == null) {
                    geneB = new Gene();
                }
            } catch (ApiException e) {
            }
            if (geneB.getEntrezGeneId() == null && StringUtils.isEmpty(geneB.getHugoSymbol())) {
                geneB.setEntrezGeneId(entrezGeneIdB);
                geneB.setHugoSymbol(hugoSymbolB == null ? "" : hugoSymbolB);
            }

            ReferenceGenome matchedRG = resolveMatchedRG(referenceGenome);

            String fusionName = FusionUtils.getFusionName(geneA, geneB);
            indicatorQueryResp = this.cacheFetcher.processQuery(
                matchedRG, null, fusionName, null, AlterationType.STRUCTURAL_VARIANT.name(), tumorType, isFunctionalFusion ? "fusion" : null, null, null, structuralVariantType, null,
                null, false, new HashSet<>(MainUtils.stringToEvidenceTypes(evidenceTypes, ",")), false);
        }
        return new ResponseEntity<>(indicatorQueryResp, HttpStatus.OK);
    }

    @PublicApi
    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate structural variants.", response = IndicatorQueryResp.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = IndicatorQueryResp.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/structuralVariants",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    public ResponseEntity<List<IndicatorQueryResp>> annotateStructuralVariantsPost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody(required = true) List<AnnotateStructuralVariantQuery> body
    ) throws ApiHttpErrorException {
        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(annotateStructuralVariants(body), HttpStatus.OK);
    }

    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Get annotations based on search", response = AnnotationSearchResult.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/annotation/search",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<LinkedHashSet<AnnotationSearchResult>> annotationSearchGet(
        @ApiParam(value = "The search query, it could be hugoSymbol, variant or cancer type. At least two characters. Maximum two keywords are supported, separated by space", required = true) @RequestParam(value = "query") String query,
        @ApiParam(value = "The limit of returned result.") @RequestParam(value = "limit", defaultValue = "10", required = false) Integer limit
    ) {
        final int DEFAULT_LIMIT = 10;
        final int QUERY_MIN_LENGTH = 2;
        Set<AnnotationSearchResult> result = new TreeSet<>();
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
        if (query != null && query.length() >= QUERY_MIN_LENGTH) {
            result = annotationSearch(query);
        }

        LinkedHashSet<AnnotationSearchResult> orderedResult = new LinkedHashSet<>();
        orderedResult.addAll(result);

        return new ResponseEntity<>(MainUtils.getLimit(orderedResult, limit), HttpStatus.OK);
    }

    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Annotate sample.", response = SampleQueryResp.class, responseContainer = "Map")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = SampleQueryResp.class, responseContainer = "Map"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/annotate/sample",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    public ResponseEntity<SampleQueryResp> annotateSamplePost(
        @ApiParam(value = "Sample query. Please see swagger.json for request body format.", required = true) @RequestBody() AnnotateSampleQuery body
    ) throws ApiHttpErrorException, ApiException, org.genome_nexus.ApiException {
        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(annotateSample(body), HttpStatus.OK);
    }

    private IndicatorQueryResp getIndicatorQueryFromGenomicLocation(
        ReferenceGenome referenceGenome,
        Alteration alteration,
        String tumorType,
        Set<EvidenceType> evidenceTypes
    ) {
        Query query = QueryUtils.getQueryFromAlteration(referenceGenome, tumorType, alteration, null);
        return this.cacheFetcher.processQuery(
            referenceGenome,
            query.getEntrezGeneId(),
            query.getHugoSymbol(),
            query.getAlteration(),
            null,
            query.getTumorType(),
            query.getConsequence(),
            query.getProteinStart(),
            query.getProteinEnd(),
            null,
            null,
            null,
            false,
            evidenceTypes,
            false
        );
    }

    private List<Alteration> getAlterationsFromGenomeNexusByHgvs(ReferenceGenome referenceGenome, List<String> queries) throws ApiException, org.genome_nexus.ApiException {
        List<String> queriesToGN = new ArrayList<>();
        Map<String, Integer> queryIndexMap = new HashMap<>();

        for (String query : queries) {
            if (this.cacheFetcher.hgvsgShouldBeAnnotated(query, referenceGenome) || AlterationUtils.isValidHgvsc(query)) {
                if (!queryIndexMap.containsKey(query)) {
                    queryIndexMap.put(query, queriesToGN.size());
                    queriesToGN.add(query);
                }
            }
        }
        // query to GN needs to be a list of uniq queries
        List<org.genome_nexus.client.VariantAnnotation> variantAnnotations = GenomeNexusUtils.getHgvsVariantsAnnotation(queriesToGN, referenceGenome);
        if(variantAnnotations.size() != queriesToGN.size()){
            throw new ApiException("Number of variants that have been annotated by GenomeNexus is not equal to the number of queries");
        }
        List<Alteration> annotatedAlterations = new ArrayList<>();
        if (!queriesToGN.isEmpty()) {
            annotatedAlterations = AlterationUtils.getAlterationsFromGenomeNexus(variantAnnotations, referenceGenome);
        }
        List<Alteration> result = new ArrayList<>();
        for (String query : queries) {
            if (queryIndexMap.containsKey(query)) {
                result.add(annotatedAlterations.get(queryIndexMap.get(query)));
            } else {
                result.add(new Alteration());
            }
        }
        return result;
    }

    private List<Alteration> getAlterationsFromGenomeNexusByGenomicLocation(ReferenceGenome referenceGenome, List<GenomicLocation> queries) throws ApiException, org.genome_nexus.ApiException {
        List<GenomicLocation> queriesToGN = new ArrayList<>();
        Map<GenomicLocation, Integer> queryIndexMap = new HashMap<>();
        for (GenomicLocation query : queries) {
            if (this.cacheFetcher.genomicLocationShouldBeAnnotated(query, referenceGenome)) {
                if (!queryIndexMap.containsKey(query)) {
                    queryIndexMap.put(query, queriesToGN.size());
                    queriesToGN.add(query);
                }
            }
        }
        List<org.genome_nexus.client.VariantAnnotation> variantAnnotations = GenomeNexusUtils.getGenomicLocationVariantsAnnotation(queriesToGN, referenceGenome);
        if(variantAnnotations.size() != queriesToGN.size()){
            throw new ApiException("Number of variants that have been annotated by GenomeNexus is not equal to the number of queries");
        }
        List<Alteration> annotatedAlterations = new ArrayList<>();
        if (!queriesToGN.isEmpty()) {
            annotatedAlterations = AlterationUtils.getAlterationsFromGenomeNexus(variantAnnotations, referenceGenome);
        }
        List<Alteration> result = new ArrayList<>();
        for (GenomicLocation query : queries) {
            if (queryIndexMap.containsKey(query)) {
                result.add(annotatedAlterations.get(queryIndexMap.get(query)));
            } else {
                result.add(new Alteration());
            }
        }
        return result;
    }

    private IndicatorQueryResp getIndicatorQueryFromHGVS(
        ReferenceGenome referenceGenome,
        Alteration hgvsAlt,
        String hgvs,
        String tumorType,
        Set<EvidenceType> evidenceTypes
    ) {
        Query query = QueryUtils.getQueryFromAlteration(referenceGenome, tumorType, hgvsAlt, hgvs);

        return this.cacheFetcher.processQuery(
            referenceGenome,
            query.getEntrezGeneId(),
            query.getHugoSymbol(),
            query.getAlteration(),
            null,
            query.getTumorType(),
            query.getConsequence(),
            query.getProteinStart(),
            query.getProteinEnd(),
            null,
            query.getHgvs(),
            null,
            false,
            evidenceTypes,
            false
        );
    }

    private SampleQueryResp annotateSample(AnnotateSampleQuery sample) throws ApiException, org.genome_nexus.ApiException {
        SampleQueryResp annotatedSample = new SampleQueryResp();

        List<IndicatorQueryResp> structuralVariants = new ArrayList<>();
        List<IndicatorQueryResp> copyNumberAlterations = new ArrayList<>();
        List<IndicatorQueryResp> genomicChange = new ArrayList<>();
        List<IndicatorQueryResp> cDnaChange = new ArrayList<>();
        List<IndicatorQueryResp> proteinChange = new ArrayList<>();
        List<IndicatorQueryResp> hgvsg = new ArrayList<>();

        if (sample.getStructuralVariants() != null) {
            structuralVariants = annotateStructuralVariants(sample.getStructuralVariants());
        }
        if (sample.getCopyNumberAlterations() != null) {
            copyNumberAlterations = annotateCopyNumberAlterations(sample.getCopyNumberAlterations());
        }
        if (sample.getMutations() != null) {
            if (sample.getMutations().getGenomicChange() != null) {
                genomicChange = annotateMutationsByGenomicChange(sample.getMutations().getGenomicChange());
            }
            if (sample.getMutations().getProteinChange() != null) {
                proteinChange = annotateMutationsByProteinChange(sample.getMutations().getProteinChange());
            }
            if (sample.getMutations().getHgvsg() != null) {
                hgvsg = annotateMutationsByHGVS(sample.getMutations().getHgvsg());
            }
            if (sample.getMutations().getcDnaChange() != null) {
                cDnaChange = annotateMutationsByHGVS(sample.getMutations().getcDnaChange());
            }
        }

        annotatedSample.setStructuralVariants(structuralVariants);
        annotatedSample.setCopyNumberAlterations(copyNumberAlterations);
        annotatedSample.setMutations(Stream.of(genomicChange, cDnaChange, proteinChange, hgvsg)
            .flatMap(Collection::stream)
            .collect(Collectors.toList())
        );
        return annotatedSample;
    }

    private List<IndicatorQueryResp> annotateStructuralVariants(List<AnnotateStructuralVariantQuery> structuralVariants) {
        List<IndicatorQueryResp> result = new ArrayList<>();
        for (AnnotateStructuralVariantQuery query : structuralVariants) {
            Gene geneA = new Gene();
            if (query.getGeneA() != null) {
                try {
                    geneA = this.cacheFetcher.findGeneBySymbol(
                        query.getGeneA().getEntrezGeneId() != null ?
                            query.getGeneA().getEntrezGeneId().toString() :
                            query.getGeneA().getHugoSymbol()
                    );
                    if (geneA == null) {
                        geneA = new Gene();
                    }
                } catch (ApiException e) {
                }
            }
            if (StringUtils.isEmpty(geneA.getHugoSymbol()) && geneA.getEntrezGeneId() == null && query.getGeneA() != null) {
                geneA.setHugoSymbol(query.getGeneA().getHugoSymbol() == null ? "" : query.getGeneA().getHugoSymbol());
                geneA.setEntrezGeneId(query.getGeneA().getEntrezGeneId());
            }

            Gene geneB = new Gene();
            if (query.getGeneB() != null) {
                try {
                    geneB = this.cacheFetcher.findGeneBySymbol(
                        query.getGeneB().getEntrezGeneId() != null ?
                            query.getGeneB().getEntrezGeneId().toString() :
                            query.getGeneB().getHugoSymbol()
                    );
                    if (geneB == null) {
                        geneB = new Gene();
                    }
                } catch (ApiException e) {
                }
            }
            if (StringUtils.isEmpty(geneB.getHugoSymbol()) && geneB.getEntrezGeneId() == null && query.getGeneB() != null) {
                geneB.setHugoSymbol(query.getGeneB().getHugoSymbol() == null ? "" : query.getGeneB().getHugoSymbol());
                geneB.setEntrezGeneId(query.getGeneB().getEntrezGeneId());
            }

            String fusionName = FusionUtils.getFusionName(geneA, geneB);

            IndicatorQueryResp resp = this.cacheFetcher.processQuery(
                query.getReferenceGenome(),
                null,
                fusionName,
                null,
                AlterationType.STRUCTURAL_VARIANT.name(),
                query.getTumorType(),
                query.getFunctionalFusion() ? "fusion" : "",
                null,
                null,
                query.getStructuralVariantType(),
                null,
                null,
                false, 
                query.getEvidenceTypes(), 
                false
            );
            resp.getQuery().setId(query.getId());
            result.add(resp);
        }
        return result;
    }

    private List<IndicatorQueryResp> annotateCopyNumberAlterations(List<AnnotateCopyNumberAlterationQuery> copyNumberAlterations) {
        List<IndicatorQueryResp> result = new ArrayList<>();
        for (AnnotateCopyNumberAlterationQuery query : copyNumberAlterations) {
            Gene gene = new Gene();
            if (query.getGene() != null) {
                try {
                    gene = this.cacheFetcher.findGeneBySymbol(
                        query.getGene().getEntrezGeneId() != null ?
                            query.getGene().getEntrezGeneId().toString() :
                            query.getGene().getHugoSymbol()
                    );
                    if (gene == null) {
                        gene = new Gene();
                        gene.setEntrezGeneId(query.getGene().getEntrezGeneId());
                        gene.setHugoSymbol(query.getGene().getHugoSymbol());
                    }
                } catch (ApiException e) {
                }
            }
            IndicatorQueryResp resp = this.cacheFetcher.processQuery(
                query.getReferenceGenome(),
                gene.getEntrezGeneId(),
                gene.getHugoSymbol(),
                StringUtils.capitalize(query.getCopyNameAlterationType().name().toLowerCase()),
                null,
                query.getTumorType(),
                null, 
                null, 
                null, 
                null,
                null, 
                null, 
                false, 
                query.getEvidenceTypes(),
                false
            );
            resp.getQuery().setId(query.getId());
            result.add(resp);
        }
        return result;
    }

    private List<IndicatorQueryResp> annotateMutationsByGenomicChange(List<AnnotateMutationByGenomicChangeQuery> mutations) throws ApiException, org.genome_nexus.ApiException {
        List<IndicatorQueryResp> result = new ArrayList<>();
        List<GenomicLocation> grch37Queries = new ArrayList<>();
        List<GenomicLocation> grch38Queries = new ArrayList<>();
        Map<Integer, Integer> grch37Map = new HashMap<>();
        Map<Integer, Integer> grch38Map = new HashMap<>();

        for (int i = 0; i < mutations.size(); i++) {
            AnnotateMutationByGenomicChangeQuery query = mutations.get(i);
            ReferenceGenome referenceGenome = query.getReferenceGenome();
            if (referenceGenome == null) {
                query.setReferenceGenome(ReferenceGenome.GRCh37);
            }
            GenomicLocation genomicLocation = GenomeNexusUtils.convertGenomicLocation(query.getGenomicLocation());
            if (referenceGenome == ReferenceGenome.GRCh38) {
                grch38Map.put(i, grch38Queries.size());
                grch38Queries.add(genomicLocation);
            } else {
                grch37Map.put(i, grch37Queries.size());
                grch37Queries.add(genomicLocation);
            }
        }

        List<Alteration> grch37Alts = getAlterationsFromGenomeNexusByGenomicLocation(ReferenceGenome.GRCh37, grch37Queries);
        List<Alteration> grch38Alts = getAlterationsFromGenomeNexusByGenomicLocation(ReferenceGenome.GRCh38, grch38Queries);

        for (int i = 0; i < mutations.size(); i++) {
            AnnotateMutationByGenomicChangeQuery query = mutations.get(i);
            Alteration alteration = query.getReferenceGenome() == ReferenceGenome.GRCh37 ? grch37Alts.get(grch37Map.get(i)) : grch38Alts.get(grch38Map.get(i));
            if (alteration == null) alteration = new Alteration();
            IndicatorQueryResp resp = this.getIndicatorQueryFromGenomicLocation(query.getReferenceGenome(), alteration, query.getTumorType(), query.getEvidenceTypes());
            resp.getQuery().setId(query.getId());
            result.add(resp);
        }
        return result;
    }

    private List<IndicatorQueryResp> annotateMutationsByProteinChange(List<AnnotateMutationByProteinChangeQuery> mutations) {
        List<IndicatorQueryResp> result = new ArrayList<>();
        for (AnnotateMutationByProteinChangeQuery query : mutations) {
            IndicatorQueryResp resp = this.cacheFetcher.processQuery(
                query.getReferenceGenome(),
                query.getGene() == null ? null : query.getGene().getEntrezGeneId(),
                query.getGene() == null ? null : query.getGene().getHugoSymbol(),
                query.getAlteration(),
                null,
                query.getTumorType(),
                query.getConsequence(),
                query.getProteinStart(),
                query.getProteinEnd(),
                null,
                null,
                null,
                false,
                query.getEvidenceTypes(),
                false
            );
            resp.getQuery().setId(query.getId());
            result.add(resp);
        }
        return result;
    }

    private List<IndicatorQueryResp> annotateMutationsByHGVS(List<AnnotateMutationByHGVSQuery> mutations) throws ApiException, org.genome_nexus.ApiException {
        List<IndicatorQueryResp> result = new ArrayList<>();
        List<String> grch37Queries = new ArrayList<>();
        List<String> grch38Queries = new ArrayList<>();
        Map<Integer, Integer> grch37Map = new HashMap<>();
        Map<Integer, Integer> grch38Map = new HashMap<>();

        for (int i = 0; i < mutations.size(); i++) {
            AnnotateMutationByHGVSQuery query = mutations.get(i);
            ReferenceGenome referenceGenome = query.getReferenceGenome();
            if (referenceGenome == null) {
                referenceGenome = ReferenceGenome.GRCh37;
            }
            query.setReferenceGenome(referenceGenome);
            if (referenceGenome == ReferenceGenome.GRCh38) {
                grch38Map.put(i, grch38Queries.size());
                grch38Queries.add(query.getHgvs());
            } else {
                grch37Map.put(i, grch37Queries.size());
                grch37Queries.add(query.getHgvs());
            }
        }

        List<Alteration> grch37Alts = getAlterationsFromGenomeNexusByHgvs(ReferenceGenome.GRCh37, grch37Queries);
        List<Alteration> grch38Alts = getAlterationsFromGenomeNexusByHgvs(ReferenceGenome.GRCh38, grch38Queries);

        for (int i = 0; i < mutations.size(); i++) {
            AnnotateMutationByHGVSQuery query = mutations.get(i);
            Alteration alteration = query.getReferenceGenome() == ReferenceGenome.GRCh37 ? grch37Alts.get(grch37Map.get(i)) : grch38Alts.get(grch38Map.get(i));
            if (alteration == null) alteration = new Alteration();

            IndicatorQueryResp resp = this.getIndicatorQueryFromHGVS(
                query.getReferenceGenome(),
                alteration,
                query.getHgvs(),
                query.getTumorType(),
                query.getEvidenceTypes()
            );
            resp.getQuery().setId(query.getId());
            result.add(resp);
        }
        return result;
    }
}
