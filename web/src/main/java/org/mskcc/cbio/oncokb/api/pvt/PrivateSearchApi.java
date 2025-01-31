package org.mskcc.cbio.oncokb.api.pvt;

import io.swagger.annotations.*;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.BiologicalVariant;
import org.oncokb.oncokb_transcript.ApiException;
import org.oncokb.oncokb_transcript.client.Drug;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by Hongxin on 12/12/16.
 */

@Api(tags = "Search", description = "The utils API")
public interface PrivateSearchApi {
    @ApiOperation(value = "", notes = "Get annotated variants information for specified gene.", response = BiologicalVariant.class, responseContainer = "Set")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/search/variants/biological",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Set<BiologicalVariant>> searchVariantsBiologicalGet(@ApiParam(value = "") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        ,@ApiParam(value = "false") @RequestParam(value = "germline", required = false) Boolean germline
    );


    @ApiOperation(value = "", notes = "Get list of variant clinical information for specified gene.", response = ClinicalVariant.class, responseContainer = "Set")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/search/variants/clinical",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Set<ClinicalVariant>> searchVariantsClinicalGet(@ApiParam(value = "") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        ,@ApiParam(value = "false") @RequestParam(value = "germline", required = false) Boolean germline
    );

    @ApiOperation(value = "", notes = "Search to find treatments.", response = Treatment.class, responseContainer = "Set")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/search/treatments",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<Set<Treatment>> searchTreatmentsGet(
        @ApiParam(value = "The search query, it could be hugoSymbol or entrezGeneId.", required = true) @RequestParam(value = "gene", required = false) String queryGene,
        @ApiParam(value = "The level of evidence.", defaultValue = "false") @RequestParam(value = "level", required = false) String queryLevel
        ,@ApiParam(value = "false") @RequestParam(value = "germline", required = false) Boolean germline
    );

    @ApiOperation(value = "", notes = "Find matches based on blur query.", response = TypeaheadSearchResp.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/search/typeahead",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<LinkedHashSet<TypeaheadSearchResp>> searchTypeAheadGet(
        @ApiParam(value = "The search query, it could be hugoSymbol, entrezGeneId or variant. At least two characters. Maximum two keywords are supported, separated by space", required = true) @RequestParam(value = "query") String query,
        @ApiParam(value = "The limit of returned result.") @RequestParam(value = "limit", defaultValue = "5", required = false) Integer limit
    );

    @ApiOperation(value = "", notes = "Find NCIT matches based on blur query. This is not for search OncoKB curated drugs. Please use drugs/lookup for that purpose.", response = Drug.class, responseContainer = "List", tags = "Drugs")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "OK")})
    @RequestMapping(value = "/search/ncitDrugs",
        produces = {"application/json"},
        method = RequestMethod.GET)
    ResponseEntity<LinkedHashSet<Drug>> searchDrugGet(
        @ApiParam(value = "The search query, it could be drug name, NCIT code", required = true) @RequestParam(value = "query") String query,
        @ApiParam(value = "The limit of returned result.") @RequestParam(value = "limit", defaultValue = "5", required = false) Integer limit
    ) throws ApiException;
}

