package org.mskcc.cbio.oncokb.api.pub.v1;

import io.swagger.annotations.*;
import io.swagger.models.auth.In;
import org.mskcc.cbio.oncokb.config.annotation.PremiumPublicApi;
import org.mskcc.cbio.oncokb.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2016-10-19T19:28:21.941Z")

@Api(tags = "Evidences", description = "OncoKB Evidence")
public interface EvidencesApi {

    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Get specific evidence.", response = Evidence.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = Evidence.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ResponseEntity.class)})
    @RequestMapping(value = "/evidences/{uuid}",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Set<Evidence>> evidencesUUIDGet(
        @ApiParam(value = "Universally Unique identifier.", required = true) @PathVariable("uuid") String uuid
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    );

    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Get specific evidences.", response = Evidence.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = Evidence.class),
        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ResponseEntity.class)})
    @RequestMapping(value = "/evidences",
        produces = {"application/json"},
        method = RequestMethod.POST)
    ResponseEntity<Set<Evidence>> evidencesUUIDsGet(
        @ApiParam(value = "Universally Unique identifier list.", required = true) @RequestBody(required = true) Set<String> uuids
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    );


    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Search evidences. Multi-queries are supported.", response = Evidence.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = Evidence.class, responseContainer = "List")})
    @RequestMapping(value = "/evidences/lookup",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<List<Evidence>> evidencesLookupGet(
        @ApiParam(value = "The entrez gene ID.") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "The gene symbol used in Human Genome Organisation.") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "Variant name.") @RequestParam(value = "variant", required = false) String variant
        , @ApiParam(value = "Tumor type name. OncoTree code is supported.") @RequestParam(value = "tumorType", required = false) String tumorType
        , @ApiParam(value = "Consequence. Possible value: feature_truncation, frameshift_variant, inframe_deletion, inframe_insertion, start_lost, missense_variant, splice_region_variant, stop_gained, synonymous_variant") @RequestParam(value = "consequence", required = false) String consequence
        , @ApiParam(value = "Protein Start.") @RequestParam(value = "proteinStart", required = false) String proteinStart
        , @ApiParam(value = "Protein End.") @RequestParam(value = "proteinEnd", required = false) String proteinEnd
        , @ApiParam(value = "Only show highest level evidences") @RequestParam(value = "highestLevelOnly", required = false, defaultValue = "FALSE") Boolean highestLevelOnly
        , @ApiParam(value = "Separate by comma. LEVEL_1, LEVEL_2A, LEVEL_2B, LEVEL_3A, LEVEL_3B, LEVEL_4, LEVEL_R1, LEVEL_R2, LEVEL_R3") @RequestParam(value = "levelOfEvidence", required = false) String levels
        , @ApiParam(value = "Separate by comma. Evidence type includes GENE_SUMMARY, GENE_BACKGROUND, MUTATION_SUMMARY, ONCOGENIC, MUTATION_EFFECT, VUS, PROGNOSTIC_IMPLICATION, DIAGNOSTIC_IMPLICATION, TUMOR_TYPE_SUMMARY, DIAGNOSTIC_SUMMARY, PROGNOSTIC_SUMMARY, STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY, STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY, INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE") @RequestParam(value = "evidenceTypes", required = false) String evidenceTypes
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
        , @ApiParam(value = "false") @RequestParam(value = "germline", required = false) Boolean germline
    );

    @PremiumPublicApi
    @ApiOperation(value = "", notes = "Search evidences.", response = EvidenceQueryRes.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK", response = EvidenceQueryRes.class, responseContainer = "List")})
    @RequestMapping(value = "/evidences/lookup",
        consumes = {"application/json"},
        produces = {"application/json"},
        method = RequestMethod.POST)
    ResponseEntity<List<EvidenceQueryRes>> evidencesLookupPost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format. Please use JSON string.", required = true) @RequestBody(required = true) EvidenceQueries body
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    );

//    @ApiOperation(value = "", notes = "Get specific evidences.", response = Evidence.class, responseContainer = "List")
//    @ApiResponses(value = {
//        @ApiResponse(code = 200, message = "OK", response = Evidence.class, responseContainer = "List"),
//        @ApiResponse(code = 400, message = "Error, error message will be given.", response = ApiErrorResp.class)})
//    @RequestMapping(value = "/evidences",
//        consumes = {"application/json"},
//        produces = {"application/json"},
//        method = RequestMethod.POST)
//    ResponseEntity<ApiListResp> evidencesPost(@ApiParam(value = "List of unique identifier for each model. Separated by comma.", required = true) @RequestParam(value = "ids", required = true) String ids
//    );
}
