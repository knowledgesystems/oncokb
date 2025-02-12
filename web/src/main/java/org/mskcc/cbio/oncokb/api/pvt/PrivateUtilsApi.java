package org.mskcc.cbio.oncokb.api.pvt;

import io.swagger.annotations.*;
import org.mskcc.cbio.oncokb.apiModels.*;
import org.mskcc.cbio.oncokb.apiModels.annotation.AnnotateMutationByGenomicChangeQuery;
import org.mskcc.cbio.oncokb.apiModels.annotation.AnnotateMutationByHGVSQuery;
import org.mskcc.cbio.oncokb.apiModels.download.DownloadAvailability;
import org.mskcc.cbio.oncokb.apiModels.ensembl.EnsemblGene;
import org.mskcc.cbio.oncokb.controller.advice.ApiHttpError;
import org.mskcc.cbio.oncokb.controller.advice.ApiHttpErrorException;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.oncokb.oncokb_transcript.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.util.MimeTypeUtils.TEXT_PLAIN_VALUE;

/**
 * Created by Hongxin on 12/12/16.
 */

@Api(tags = "Utils", description = "The utils API")
public interface PrivateUtilsApi {
    @ApiOperation(value = "", notes = "Get All Suggested Variants.", response = String.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = AnnotatedVariant.class, responseContainer = "List")})
    @RequestMapping(value = "/utils/suggestedVariants", produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<String>> utilsSuggestedVariantsGet();

    @ApiOperation(value = "", notes = "Determine whether variant is hotspot mutation.", response = Boolean.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = Boolean.class)})
    @RequestMapping(value = "/utils/isHotspot", produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Boolean> utilsHotspotMutationGet(
        @ApiParam(value = "Gene hugo symbol") @RequestParam(value = "hugoSymbol") String hugoSymbol
        , @ApiParam(value = "Variant name") @RequestParam(value = "variant") String variant
    );

    @ApiOperation(value = "", notes = "Get gene related numbers", response = GeneNumber.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/numbers/gene/{hugoSymbol}",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<GeneNumber> utilsNumbersGeneGet(
        @ApiParam(value = "The gene symbol used in Human Genome Organisation.", required = true) @PathVariable("hugoSymbol") String hugoSymbol
    );

    @ApiOperation(value = "", notes = "Get gene related numbers of all genes. This is for main page word cloud.", response = GeneNumber.class, responseContainer = "Set")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/numbers/genes/",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Set<GeneNumber>> utilsNumbersGenesGet();

    @ApiOperation(value = "", notes = "Get numbers served for the main page dashboard.", response = MainNumber.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/numbers/main/",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<MainNumber> utilsNumbersMainGet();

    @ApiOperation(value = "", notes = "Get gene related numbers of all genes. This is for main page word cloud.", response = LevelNumber.class, responseContainer = "Set")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/numbers/levels/",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Set<LevelNumber>> utilsNumbersLevelsGet();

    @ApiOperation(value = "", notes = "Check if clinical trials are valid or not by nctId.", response = Map.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/validation/trials",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Map<String, Boolean>> validateTrials(@ApiParam(value = "NCT ID list") @RequestParam(value = "nctIds") List<String> nctIds) throws ParserConfigurationException, SAXException, IOException;

    @ApiOperation(value = "", notes = "Check if the genomic example will be mapped to OncoKB variant.", response = Map.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/match/variant",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Map<String, Boolean>> validateVariantExampleGet(
        @ApiParam(value = "Gene Hugo Symbol") @RequestParam(value = "hugoSymbol") String hugoSymbol
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "The OncoKB variant") @RequestParam(value = "variant") String variant
        , @ApiParam(value = "The genomic examples.") @RequestParam(value = "examples") String examples
    ) throws ParserConfigurationException, SAXException, IOException;

    @ApiOperation(value = "", notes = "Check which OncoKB variants can be mapped on genomic examples.", response = MatchVariantResult.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/match/variant",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    ResponseEntity<List<MatchVariantResult>> validateVariantExamplePost(@ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody(required = true) MatchVariantRequest body
    );

    @ApiOperation(value = "", notes = "Get the full list of TumorTypes.", response = TumorType.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/tumorTypes",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<TumorType>> utilsTumorTypesGet();

    @ApiOperation(value = "", notes = "Get the list of Ensembl genes.", response = EnsemblGene.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/ensembleGenes",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<EnsemblGene>> utilsEnsemblGenesGet(
        @ApiParam(value = "Gene entrez id", required = true) @RequestParam(value = "entrezGeneId") Integer entrezGeneId
    );

    @ApiOperation(value = "", notes = "Get the list of evidences by levels.", response = Map.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/evidences/levels",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Map<LevelOfEvidence, Set<Evidence>>> utilsEvidencesByLevelsGet();

    @ApiOperation(value = "", notes = "Get the list of relevant tumor types.", response = TumorType.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = TumorType.class, responseContainer = "List")})
    @RequestMapping(value = "/utils/relevantTumorTypes",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<TumorType>> utilRelevantTumorTypesGet(
        @ApiParam(value = "OncoTree tumor type name/main type/code") @RequestParam(value = "tumorType") String tumorType
    );

    @ApiOperation(value = "", notes = "Get the list of relevant tumor types.", response = TumorType.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/relevantCancerTypes",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    ResponseEntity<List<TumorType>> utilRelevantCancerTypesPost(
        @ApiParam(value = "Level of Evidence") @RequestParam(value = "levelOfEvidence", required = false) LevelOfEvidence levelOfEvidence,
        @ApiParam(value = "List of queries.", required = true) @RequestBody List<RelevantCancerTypeQuery> body
    );

    @ApiOperation(value = "", notes = "Get the list of relevant alterations", response = Alteration.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/relevantAlterations",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<Alteration>> utilRelevantAlterationsGet(
        @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "alteration") @RequestParam(value = "entrezGeneId") Integer entrezGeneId
        , @ApiParam(value = "alteration") @RequestParam(value = "alteration") String alteration
    );

    @ApiOperation(value = "", notes = "Get all the info for the query", response = VariantAnnotation.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = VariantAnnotation.class)})
    @RequestMapping(value = "/utils/variantAnnotation",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<VariantAnnotation> utilVariantAnnotationGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "entrezGeneId") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "Alteration") @RequestParam(value = "alteration", required = false) String alteration
        , @ApiParam(value = "HGVS genomic format. Example: 7:g.140453136A>T") @RequestParam(value = "hgvsg", required = false) String hgvsg
        , @ApiParam(value = "Genomic change format. Example: 7,140453136,140453136,A,T") @RequestParam(value = "genomicChange", required = false) String genomicChange
        , @ApiParam(value = "OncoTree tumor type name/main type/code") @RequestParam(value = "tumorType", required = false) String tumorType
    ) throws ApiException, org.genome_nexus.ApiException;

    @ApiOperation(value = "", notes = "", response = CancerTypeCount.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = CancerTypeCount.class, responseContainer = "List")})
    @RequestMapping(value = "/utils/portalAlterationSampleCount",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<CancerTypeCount>> utilPortalAlterationSampleCountGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
    );

    @ApiOperation(value = "", notes = "", response = PortalAlteration.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = PortalAlteration.class, responseContainer = "List")})
    @RequestMapping(value = "/utils/mutationMapperData",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<PortalAlteration>> utilMutationMapperDataGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
    );

    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/updateTranscript",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Void> utilUpdateTranscriptGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "entrezGeneId") @RequestParam(required = false) Integer entrezGeneId
        , @ApiParam(value = "grch37Isoform") @RequestParam(required = false) String grch37Isoform
        , @ApiParam(value = "grch37RefSeq") @RequestParam(required = false) String grch37RefSeq
        , @ApiParam(value = "grch38Isoform") @RequestParam(required = false) String grch38Isoform
        , @ApiParam(value = "grch38RefSeq") @RequestParam(required = false) String grch38RefSeq
    ) throws ApiException, IOException;

    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/utils/validateTranscriptUpdate",
        produces = TEXT_PLAIN_VALUE,
        method = RequestMethod.GET)
    ResponseEntity<String> utilValidateTranscriptUpdateGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "entrezGeneId") @RequestParam(required = false) Integer entrezGeneId
        , @ApiParam(value = "grch37Isoform") @RequestParam(required = false) String grch37Isoform
        , @ApiParam(value = "grch38Isoform") @RequestParam(required = false) String grch38Isoform
    ) throws ApiException;

    @ApiOperation(value = "", notes = "Get information about what files are available by data version", response = DownloadAvailability.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = DownloadAvailability.class, responseContainer = "List"),
        @ApiResponse(code = 503, message = "Service Unavailable")
    })
    @RequestMapping(value = "/utils/data/availability",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<DownloadAvailability>> utilDataAvailabilityGet();

    @ApiOperation(value = "", notes = "Get readme info for specific data release version", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = String.class),
        @ApiResponse(code = 404, message = "Not Found"),
        @ApiResponse(code = 503, message = "Service Unavailable")
    })
    @RequestMapping(value = "/utils/data/readme",
        produces = TEXT_PLAIN_VALUE,
        method = RequestMethod.GET)
    ResponseEntity<String> utilDataReadmeGet(
        @ApiParam(value = "version", required = true) @RequestParam(value = "version") String version
    );

    @RequestMapping(value = "/utils/data/sqlDump",
        produces = {"application/gz"},
        method = RequestMethod.GET)
    ResponseEntity<byte[]> utilDataSqlDumpGet(
        @ApiParam(value = "version", required = true) @RequestParam(value = "version") String version
    );

    @RequestMapping(value = "/utils/data/transcriptSqlDump",
        produces = {"application/gz"},
        method = RequestMethod.GET)
    ResponseEntity<byte[]> utilDataTranscriptSqlDump(
        @ApiParam(value = "version", required = true) @RequestParam(value = "version") String version
    );

    @ApiOperation(value = "", notes = "Filter HGVSg based on oncokb coverage", response = String.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = String.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/utils/filterHgvsgBasedOnCoverage",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    ResponseEntity<List<TranscriptCoverageFilterResult>> utilFilterHgvsgBasedOnCoveragePost(
        @ApiParam(value = "List of queries.", required = true) @RequestBody List<AnnotateMutationByHGVSQuery> body
    ) throws ApiException, org.genome_nexus.ApiException, ApiHttpErrorException;

    @ApiOperation(value = "", notes = "Filter genomic change based on oncokb coverage", response = String.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = String.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiHttpError.class)})
    @RequestMapping(value = "/utils/filterGenomicChangeBasedOnCoverage",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    ResponseEntity<List<TranscriptCoverageFilterResult>> utilFilterGenomicChangeBasedOnCoveragePost(
        @ApiParam(value = "List of queries.", required = true) @RequestBody List<AnnotateMutationByGenomicChangeQuery> body
    ) throws ApiException, org.genome_nexus.ApiException, ApiHttpErrorException;

}

