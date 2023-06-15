package org.mskcc.cbio.oncokb.api.pub.v1;

import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.mskcc.cbio.oncokb.cache.CacheFetcher;
import org.mskcc.cbio.oncokb.config.annotation.PremiumPublicApi;
import org.mskcc.cbio.oncokb.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.mskcc.cbio.oncokb.model.SpecialTumorType;
import org.mskcc.cbio.oncokb.model.TumorForm;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.mskcc.cbio.oncokb.model.clinicalTrialsMatching.*;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;


/**
 * Created by Yifu Yao on 2020-09-08
 */
@Api(tags = "Trials", description = "Clinical Trials Matching")
@Controller
public class TrialsApiController {

    @PremiumPublicApi
    @ApiOperation("Return a list of trials using OncoTree Code and/or treatment")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", responseContainer = "List"),
        @ApiResponse(code = 400, message = "Error", response = String.class)})
    @RequestMapping(value = "/trials", produces = {"application/json"}, method = RequestMethod.GET)
    public ResponseEntity<List<Trial>> trialsMatchingGet(
            @ApiParam(value = "", required = false) @RequestParam(value = "", required = false) String treatment,
            @ApiParam(value = "", required = true) @RequestParam(value = "", required = true) String cancerType)
            throws IOException, ParseException {
        HttpStatus status = HttpStatus.OK;

        Map<String, Trial> trialsMapping = CacheUtils.getTrialsMapping();
        Map<String, Tumor>  oncotreeMapping = CacheUtils.getOncoTreeMappingTrials();

        SpecialTumorType specialTumorType = SpecialTumorType.getByTumorType(cancerType);
        if(specialTumorType != null) {
            List<Trial> trials = ClinicalTrialsUtils.getTrialsForSpecialCancerType(specialTumorType);
            if (treatment != null) {
                trials = ClinicalTrialsUtils.getTrialsByTreatment(trials,treatment);
            }
            return new ResponseEntity<List<Trial>>(trials, status);
        } else {
            List<TumorType> tumorTypes = TumorTypeUtils.findRelevantTumorTypes(cancerType);
            List<Trial> trials = new ArrayList<>();
            Boolean cancerTypeInTumorTypes = false;
            for (TumorType tumorType: tumorTypes) {
                String mainType = tumorType.getMainType();
                trials.addAll(ClinicalTrialsUtils.getTrialsByCancerType(oncotreeMapping, trialsMapping, mainType));
                if (mainType == cancerType) {
                    cancerTypeInTumorTypes = true;
                }
            }
            if (!cancerTypeInTumorTypes) {
                trials.addAll(ClinicalTrialsUtils.getTrialsByCancerType(oncotreeMapping, trialsMapping, cancerType.toLowerCase()));
            }
            if (treatment != null) {
                trials = ClinicalTrialsUtils.getTrialsByTreatment(trials,treatment);
            }
            return new ResponseEntity<List<Trial>>(trials, status);
        }
    }

    @PremiumPublicApi
    @ApiOperation("Return a list of trials using cancer types")
    @ApiResponses(value = {@ApiResponse(code = 200, message = "OK", responseContainer = "Map"),
        @ApiResponse(code = 400, message = "Error", response = String.class)})
    @RequestMapping(value = "/trials/cancerTypes", produces = {"application/json"}, method = RequestMethod.POST)
    public ResponseEntity<Map<String, List<Trial>>> getTrialsByCancerTypes(
        @ApiParam(value = "", required = true) @RequestBody() CancerTypesQuery body)
        throws UnsupportedEncodingException, IOException, ParseException {
        HttpStatus status = HttpStatus.OK;
        Map<String, List<Trial>> result = new HashMap<>();

        if (body == null) {
            status = HttpStatus.BAD_REQUEST;
        } else {
            Map<String, Trial> trialsMapping = CacheUtils.getTrialsMapping();
            Map<String, Tumor>  oncotreeMapping = CacheUtils.getOncoTreeMappingTrials();

            Set<String> cancerTypes = new HashSet<>(body.getCancerTypes());
            if (cancerTypes.contains(SpecialTumorType.ALL_TUMORS.getTumorType())) {
                List<Trial> trials = new ArrayList<>();
                Set<String> nctIDSet = new HashSet<>();
                for (Object item : oncotreeMapping.keySet()) {
                    String oncoTreeCode = (String) item;
                    Tumor tumor = ClinicalTrialsUtils.getTumor(oncotreeMapping, trialsMapping, oncoTreeCode);

                    for (Trial curTrial : tumor.getTrials()) {
                        if (!nctIDSet.contains(curTrial.getNctId())) {
                            nctIDSet.add(curTrial.getNctId());
                            trials.add(curTrial);
                        }
                    }
                }
                result.put(SpecialTumorType.ALL_TUMORS.getTumorType(), trials);
                return new ResponseEntity<>(result, status);
            }

            for (String cancerType : cancerTypes) {
                Set<String> nctIDSet = new HashSet<>();
                List<Trial> addTrials = new ArrayList<>();
                List<Trial> trials = new ArrayList<>();
                SpecialTumorType specialTumorType = ApplicationContextSingleton.getTumorTypeBo().getSpecialTumorTypeByName(cancerType);
                if (specialTumorType != null) {
                    trials = ClinicalTrialsUtils.getTrialsForSpecialCancerType(specialTumorType);
                } else {
                    trials = ClinicalTrialsUtils.getTrialsByCancerType(oncotreeMapping, trialsMapping, cancerType);
                }
                for (Trial trial : trials) {
                    if (!nctIDSet.contains(trial.getNctId())) {
                        nctIDSet.add(trial.getNctId());
                        addTrials.add(trial);
                    }
                }
                result.put(cancerType, addTrials);
            }

            if (result.isEmpty()) {
                status = HttpStatus.NOT_FOUND;
                return new ResponseEntity<>(result, status);
            }
        }
        return new ResponseEntity<>(result, status);
    }
}
