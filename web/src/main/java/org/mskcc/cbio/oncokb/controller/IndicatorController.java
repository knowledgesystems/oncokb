/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.controller;

import io.swagger.annotations.ApiParam;
import org.apache.commons.collections.CollectionUtils;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.service.JsonResultFactory;
import org.mskcc.cbio.oncokb.util.IndicatorUtils;
import org.mskcc.cbio.oncokb.util.LevelUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mskcc.cbio.oncokb.util.MainUtils.stringToEvidenceTypes;

/**
 * @author jgao
 */
@Controller
@RequestMapping(value = "/legacy-api/indicator.json")
public class IndicatorController {
    @RequestMapping(method = RequestMethod.GET)
    public
    @ResponseBody
    IndicatorQueryResp getEvidence(
        HttpMethod method,
        @RequestParam(value = "id", required = false) String id,
        @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") ReferenceGenome referenceGenome,
        @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId,
        @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol,
        @RequestParam(value = "alteration", required = false) String alteration,
        @RequestParam(value = "alterationType", required = false) String alterationType,
        @RequestParam(value = "svType", required = false) StructuralVariantType svType,
        @RequestParam(value = "tumorType", required = false) String tumorType,
        @RequestParam(value = "consequence", required = false) String consequence,
        @RequestParam(value = "proteinStart", required = false) Integer proteinStart,
        @RequestParam(value = "proteinEnd", required = false) Integer proteinEnd,
        @RequestParam(value = "levels", required = false) String levels,
        @RequestParam(value = "highestLevelOnly", required = false) Boolean highestLevelOnly,
        @RequestParam(value = "fields", required = false) String fields,
        @RequestParam(value = "hgvs", required = false) String hgvs,
        @RequestParam(value = "germline", required = false) Boolean germline,
        @RequestParam(value = "alleleState", required = false) String alleleState,
        @RequestParam(value = "pathogenicity", required = false) Pathogenicity pathogenicity
    ) {
        Query query = new Query(id, referenceGenome, entrezGeneId, hugoSymbol, alteration, alterationType, svType, tumorType, consequence, proteinStart, proteinEnd, hgvs, germline, alleleState, pathogenicity);
        Set<LevelOfEvidence> levelOfEvidences = levels == null ? null : LevelUtils.parseStringLevelOfEvidences(levels);
        IndicatorQueryResp resp = IndicatorUtils.processQuery(query, levelOfEvidences, highestLevelOnly, null, false);

        return JsonResultFactory.getIndicatorQueryResp(resp, fields);
    }

    @RequestMapping(method = RequestMethod.POST)
    public
    @ResponseBody
    List<IndicatorQueryResp> getResult(
        @RequestBody EvidenceQueries body,
        @RequestParam(value = "fields", required = false) String fields
    ) {

        List<IndicatorQueryResp> result = new ArrayList<>();

        if (body == null || body.getQueries() == null || body.getQueries().size() == 0) {
            return result;
        }

        for (Query query : body.getQueries()) {
            result.add(IndicatorUtils.processQuery(query,
                body.getLevels() == null ? null : body.getLevels(),
                body.getHighestLevelOnly(),  new HashSet<>(stringToEvidenceTypes(body.getEvidenceTypes(), ",")), false));
        }

        return JsonResultFactory.getIndicatorQueryResp(result, fields);
    }


}
