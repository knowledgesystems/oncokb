package org.mskcc.cbio.oncokb.api.pub.v1;

import io.swagger.annotations.ApiParam;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.service.JsonResultFactory;
import org.mskcc.cbio.oncokb.util.EvidenceUtils;
import org.mskcc.cbio.oncokb.util.MainUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.Constants.DEFAULT_REFERENCE_GENOME;


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2016-10-19T19:28:21.941Z")

@Controller
public class EvidencesApiController implements EvidencesApi {

    public ResponseEntity<Set<Evidence>> evidencesUUIDGet(
        @ApiParam(value = "Unique identifier.", required = true) @PathVariable("uuid") String uuid
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) {
        HttpStatus status = HttpStatus.OK;
        Set<Evidence> evidences = null;
        if (uuid == null) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            evidences = EvidenceUtils.getEvidencesByUUID(uuid);
        }
        return ResponseEntity.status(status.value()).body(JsonResultFactory.getEvidence(evidences, fields));
    }

    public ResponseEntity<Set<Evidence>> evidencesUUIDsGet(
        @ApiParam(value = "Unique identifier list.", required = true) @RequestBody(required = true) Set<String> uuids
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) {
        HttpStatus status = HttpStatus.OK;
        Set<Evidence> evidences = null;
        if (uuids == null) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            evidences = EvidenceUtils.getEvidencesByUUIDs(uuids);
        }
        return ResponseEntity.status(status.value()).body(JsonResultFactory.getEvidence(evidences, fields));
    }

    public ResponseEntity<List<Evidence>> evidencesLookupGet(
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
    ) {
        HttpStatus status = HttpStatus.OK;
        List<Evidence> evidences = new ArrayList<>();

        Query tmpQuery = new Query(null, DEFAULT_REFERENCE_GENOME, entrezGeneId,
            hugoSymbol, variant, null, null,
            tumorType, consequence, proteinStart == null ? null : Integer.parseInt(proteinStart),
            proteinEnd == null ? null : Integer.parseInt(proteinEnd), null, germline, null, null);

        List<EvidenceQueryRes> evidenceQueries = EvidenceUtils.processRequest(
            Collections.singletonList(tmpQuery),
            evidenceTypes == null ? null : Arrays.stream(evidenceTypes.split(",")).map(evidence -> EvidenceType.valueOf(evidence.trim())).collect(Collectors.toSet()),
            levels == null ? null : Arrays.stream(levels.split(",")).map(level -> LevelOfEvidence.getByName(level.trim())).collect(Collectors.toSet()), highestLevelOnly, false);

        if (evidenceQueries != null) {
            for (EvidenceQueryRes query : evidenceQueries) {
                evidences.addAll(query.getEvidences());
            }
        }

        return ResponseEntity.ok().body(JsonResultFactory.getEvidence(evidences, fields));
    }

    public ResponseEntity<List<EvidenceQueryRes>> evidencesLookupPost(
        @ApiParam(value = "List of queries. Please see swagger.json for request body format. Please use JSON string.", required = true) @RequestBody(required = true) EvidenceQueries body
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) {
        HttpStatus status = HttpStatus.OK;
        List<EvidenceQueryRes> result = new ArrayList<>();
        if (body.getQueries().size() > 0) {
            List<Query> requestQueries = body.getQueries();
            Set<EvidenceType> evidenceTypes = new HashSet<>();

            if (body.getEvidenceTypes() != null) {
                for (String type : body.getEvidenceTypes().split("\\s*,\\s*")) {
                    EvidenceType et = EvidenceType.valueOf(type);
                    evidenceTypes.add(et);
                }
            } else {
                evidenceTypes.add(EvidenceType.GENE_SUMMARY);
                evidenceTypes.add(EvidenceType.GENE_BACKGROUND);
            }

            result = EvidenceUtils.processRequest(requestQueries, evidenceTypes,
                body.getLevels(), body.getHighestLevelOnly(), false);
        }

        return ResponseEntity.ok().body(JsonResultFactory.getEvidenceQueryRes(result, fields));
    }

    public ResponseEntity<List<Evidence>> evidencesPost(
        @ApiParam(value = "List of unique identifier for each model. Separated by comma.", required = true) @RequestParam(value = "ids", required = true) String ids
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) {
        HttpStatus status = HttpStatus.OK;
        List<Evidence> evidenceList = new ArrayList<>();
        if (ids == null) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            List<Integer> matchIds = MainUtils.stringToIntegers(ids);
            if (matchIds != null) {
                Set<Evidence> evidences = EvidenceUtils.getEvidenceByEvidenceIds(new HashSet<Integer>(matchIds));
                if (evidences != null) {
                    evidenceList = new ArrayList<>(evidences);
                }
            }
        }
        return ResponseEntity.ok().body(JsonResultFactory.getEvidence(evidenceList, fields));
    }
}
