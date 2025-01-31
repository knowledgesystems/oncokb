package org.mskcc.cbio.oncokb.controller;

import com.mysql.jdbc.StringUtils;
import io.swagger.annotations.ApiParam;
import org.apache.poi.ss.formula.functions.T;
import org.json.JSONArray;
import org.json.JSONException;
import org.mskcc.cbio.oncokb.bo.EvidenceBo;
import org.mskcc.cbio.oncokb.bo.TreatmentBo;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author jgao
 */
@Controller
public class EvidenceController {

    @RequestMapping(value = "/legacy-api/evidences/update/{uuid}", method = RequestMethod.POST)
    public
    @ResponseBody
    synchronized ResponseEntity updateEvidence(@ApiParam(value = "uuid", required = true) @PathVariable("uuid") String uuid,
                                               @RequestBody Evidence queryEvidence) throws ParserConfigurationException, IOException {

        List<Evidence> updatedEvidences = updateEvidenceBasedOnUuid(uuid, queryEvidence);

        if (updatedEvidences != null) {
            updateCacheBasedOnEvidences(new HashSet<>(updatedEvidences));
        }

        return new ResponseEntity(HttpStatus.OK);
    }

    @RequestMapping(value = "/legacy-api/evidences/update", method = RequestMethod.POST)
    public
    @ResponseBody
    synchronized ResponseEntity updateEvidence(@RequestBody Map<String, Evidence> queryEvidences) throws ParserConfigurationException, IOException {
        Set<Evidence> updatedEvidenceSet = new HashSet<>();

        for (Map.Entry<String, Evidence> entry : queryEvidences.entrySet()) {

            List<Evidence> updatedEvidences = updateEvidenceBasedOnUuid(entry.getKey(), entry.getValue());

            if (updatedEvidences != null) {
                updatedEvidenceSet.addAll(updatedEvidences);
            }
        }
        updateCacheBasedOnEvidences(updatedEvidenceSet);

        return new ResponseEntity(HttpStatus.OK);
    }

    @RequestMapping(value = "/legacy-api/evidences/updateRelevantCancerTypes", method = RequestMethod.POST)
    public
    @ResponseBody
    synchronized ResponseEntity updateEvidence(@RequestBody Set<Evidence> evidences) throws IOException {
        Set<Evidence> updatedEvidenceSet = new HashSet<>();
        evidences.forEach(evidence -> {
            Set<TumorType> cancerTypes = evidence.getRelevantCancerTypes().stream().map(tumorType -> StringUtils.isNullOrEmpty(tumorType.getCode()) ? ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType.getMainType()) : ApplicationContextSingleton.getTumorTypeBo().getByCode(tumorType.getCode())).collect(Collectors.toSet());
            List<Evidence> persistenceEvidences = ApplicationContextSingleton.getEvidenceBo().findEvidenceByUUIDs(Collections.singletonList(evidence.getUuid()));
            persistenceEvidences.forEach(persistenceEvidence -> {
                persistenceEvidence.setRelevantCancerTypes(cancerTypes);
                updatedEvidenceSet.add(persistenceEvidence);
                ApplicationContextSingleton.getEvidenceBo().update(persistenceEvidence);
            });
        });
        updateCacheBasedOnEvidences(updatedEvidenceSet);
        return new ResponseEntity(HttpStatus.OK);
    }

    @RequestMapping(value = "/legacy-api/evidences/{uuid}/priority/update/{newPriority}", method = RequestMethod.POST)
    public
    @ResponseBody
    synchronized ResponseEntity updateEvidencePriority(@ApiParam(value = "uuid", required = true) @PathVariable("uuid") String uuid,
                                                       @ApiParam(value = "newPriority", required = true) @PathVariable("newPriority") Map<String, Integer> newPriority
    ) throws ParserConfigurationException, IOException {

        Map<String, Map<String, Integer>> map = new HashMap<>();
        map.put(uuid, newPriority);
        Set<Evidence> updatedEvidences = updateEvidencePriorityBasedOnUuid(map);

        if (updatedEvidences != null) {
            updateCacheBasedOnEvidences(updatedEvidences);
        }

        return new ResponseEntity(HttpStatus.OK);
    }

    @RequestMapping(value = "/legacy-api/evidences/priority/update", method = RequestMethod.POST)
    public
    @ResponseBody
    synchronized ResponseEntity updateEvidencesPriority(@RequestBody Map<String, Map<String, Integer>> priorities
    ) throws ParserConfigurationException, IOException {
        Set<Evidence> updatedEvidences = updateEvidencePriorityBasedOnUuid(priorities);

        if (updatedEvidences != null) {
            updateCacheBasedOnEvidences(updatedEvidences);
        }

        return new ResponseEntity(HttpStatus.OK);
    }

    @RequestMapping(value = "/legacy-api/evidences/delete", method = RequestMethod.POST)
    public
    @ResponseBody
    synchronized String deleteEvidences(@RequestBody List<String> uuids) throws IOException {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        if (uuids != null) {
            List<Evidence> evidences = evidenceBo.findEvidenceByUUIDs(uuids);
            Set<Gene> genes = deleteEvidencesAndAlts(evidences);
            updateCacheBasedOnGenes(genes);
        }
        return "";
    }

    @RequestMapping(value = "/legacy-api/evidences/delete/{uuid}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    synchronized String deleteEvidence(@ApiParam(value = "uuid", required = true) @PathVariable("uuid") String uuid) throws IOException {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        List<Evidence> evidences = evidenceBo.findEvidenceByUUIDs(Collections.singletonList(uuid));

        Set<Gene> genes = deleteEvidencesAndAlts(evidences);
        updateCacheBasedOnGenes(genes);
        return "";
    }

    @RequestMapping(value = "/legacy-api/vus/update/{hugoSymbol}", method = RequestMethod.POST)
    public
    @ResponseBody
    synchronized ResponseEntity updateVUS(
        @ApiParam(value = "germline", required = true) @PathVariable("germline") Boolean germline,
        @ApiParam(value = "hugoSymbol", required = true) @PathVariable("hugoSymbol") String hugoSymbol,
                                          @RequestBody String vus) throws JSONException, IOException {

        HttpStatus status = HttpStatus.OK;
        if (hugoSymbol != null && vus != null) {
            Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
            if (gene != null) {
                List<Evidence> evidences = EvidenceUtils.getEvidence(
                    new ArrayList<>(AlterationUtils.getAllAlterations(null, gene)),
                    Collections.singleton(EvidenceType.VUS), null);
                deleteEvidencesAndAlts(evidences);
                DriveAnnotationParser driveAnnotationParser = new DriveAnnotationParser();
                driveAnnotationParser.parseVUS(germline, gene, new JSONArray(vus), 1);
                updateCacheBasedOnGenes(Collections.singleton(gene));
            }
        }

        return new ResponseEntity(status);
    }

    private Set<Gene> deleteEvidencesAndAlts(List<Evidence> evidences) {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        List<Alteration> alts = new ArrayList<>();
        List<Alteration> removedAlts = new ArrayList<>();

        // The sample solution for now is updating all gene related evidences and alterations.
        Set<Gene> genes = new HashSet<>();

        for (Evidence evidence : evidences) {
            alts.addAll(evidence.getAlterations());
            genes.add(evidence.getGene());
        }
        evidenceBo.deleteAll(evidences);

        for (Alteration alt : alts) {
            List<Evidence> altEvidences = evidenceBo.findEvidencesByAlteration(Collections.singletonList(alt));
            if (altEvidences == null || altEvidences.isEmpty()) {
                removedAlts.add(alt);
            }
        }
        ApplicationContextSingleton.getAlterationBo().deleteAll(removedAlts);

        return genes;
    }

    private String specialTypeCheck(Evidence queryEvidence) {
        if (queryEvidence.getAlterations() != null) return "MUTATION_NAME_CHANGE";
        if (!queryEvidence.getCancerTypes().isEmpty()) return "TUMOR_NAME_CHANGE";
        if (queryEvidence.getTreatments() != null) return "TREATMENT_NAME_CHANGE";
        return "";
    }

    private Boolean isEmptyEvidence(Evidence queryEvidence) {
        EvidenceType evidenceType = queryEvidence.getEvidenceType();
        String knownEffect = queryEvidence.getKnownEffect();
        String description = queryEvidence.getDescription();
        LevelOfEvidence level = queryEvidence.getLevelOfEvidence();
        Set<Treatment> treatments = queryEvidence.getTreatments();
        if (description != null) {
            description = description.trim();
        }
        Boolean isEmpty = false;
        if (evidenceType.equals(EvidenceType.ONCOGENIC) || evidenceType.equals(EvidenceType.MUTATION_EFFECT)) {
            if (StringUtils.isNullOrEmpty(knownEffect) && StringUtils.isNullOrEmpty(description)) isEmpty = true;
        } else if (EvidenceTypeUtils.getTreatmentEvidenceTypes().contains(evidenceType)) {
            if (treatments == null && StringUtils.isNullOrEmpty(description)) isEmpty = true;
        } else if (evidenceType.equals(EvidenceType.DIAGNOSTIC_IMPLICATION) || evidenceType.equals(EvidenceType.PROGNOSTIC_IMPLICATION)) {
            if (level == null && StringUtils.isNullOrEmpty(description)) isEmpty = true;
        } else if (StringUtils.isNullOrEmpty(description)) {
            isEmpty = true;
        }
        return isEmpty;
    }

    private List<Evidence> updateEvidenceBasedOnUuid(String uuid, Evidence queryEvidence) throws ParserConfigurationException {
        EvidenceUtils.annotateEvidence(queryEvidence, null);
        Gene gene = null;

        if (queryEvidence.getGene() != null) {
            gene = GeneUtils.getGene(queryEvidence.getGene().getEntrezGeneId(), queryEvidence.getGene().getHugoSymbol());
        }
        Set<Alteration> alterations = queryEvidence.getAlterations();
        String knownEffect = queryEvidence.getKnownEffect();
        LevelOfEvidence level = queryEvidence.getLevelOfEvidence();
        String description = queryEvidence.getDescription();
        String additionalInfo = queryEvidence.getAdditionalInfo();
        Date lastEdit = queryEvidence.getLastEdit();
//        Date lastReview = queryEvidence.getLastReview();
        List<Treatment> treatments = queryEvidence.getSortedTreatment();
        Set<Article> articles = queryEvidence.getArticles();
        LevelOfEvidence solidPropagation = queryEvidence.getSolidPropagationLevel();
        LevelOfEvidence liquidPropagation = queryEvidence.getLiquidPropagationLevel();
        LevelOfEvidence fdaLevel = queryEvidence.getFdaLevel();

        // if the gene does not exist, return null
        if (gene == null) {
            return new ArrayList<>();
        }

        // if the level is not allowed, skip the update
        if (level != null && !LevelUtils.getAllowedCurationLevels().contains(level)) {
            return new ArrayList<>();
        }

        Set<TumorType> cancerTypes = queryEvidence.getCancerTypes().stream().map(tumorType -> StringUtils.isNullOrEmpty(tumorType.getCode()) ? ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType.getMainType()) : ApplicationContextSingleton.getTumorTypeBo().getByCode(tumorType.getCode())).collect(Collectors.toSet());
        Set<TumorType> excludedCancerTypes = queryEvidence.getExcludedCancerTypes().stream().map(tumorType -> StringUtils.isNullOrEmpty(tumorType.getCode()) ? ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType.getMainType()) : ApplicationContextSingleton.getTumorTypeBo().getByCode(tumorType.getCode())).collect(Collectors.toSet());
        Set<TumorType> relevantCancerTypes = queryEvidence.getRelevantCancerTypes().stream().map(tumorType -> StringUtils.isNullOrEmpty(tumorType.getCode()) ? ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType.getMainType()) : ApplicationContextSingleton.getTumorTypeBo().getByCode(tumorType.getCode())).collect(Collectors.toSet());
        Boolean isCancerEvidence = true;
        if (queryEvidence.getCancerTypes().isEmpty()) {
            isCancerEvidence = false;
        }
        // if cancerTypes and subTypes are not the same length, return null
        if (cancerTypes.size() != queryEvidence.getCancerTypes().size()) {
            return new ArrayList<>();
        }
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        TreatmentBo treatmentBo = ApplicationContextSingleton.getTreatmentBo();
        List<Evidence> evidences = evidenceBo.findEvidenceByUUIDs(Collections.singletonList(uuid));

        EvidenceType evidenceType = queryEvidence.getEvidenceType();
        // Three special evidences update, which are mutation name change, tumor name change and treatment name change
        if (evidenceType == null) {
            // If evidences not exist, return empty list
            if (evidences.isEmpty()) return evidences;
            String specialChangeType = specialTypeCheck(queryEvidence);
            if (specialChangeType.equals("MUTATION_NAME_CHANGE")) {
                Evidence oldEvidence = new Evidence(evidences.get(0), null);
                if (oldEvidence.getEvidenceType().equals(EvidenceType.ONCOGENIC)) {
                    List<Evidence> newEvidences = new ArrayList<>();
                    for (Alteration alteration : alterations) {
                        Evidence tempEvidence = new Evidence(oldEvidence, null);
                        tempEvidence.setAlterations(Collections.singleton(alteration));
                        evidenceBo.save(tempEvidence);
                        newEvidences.add(tempEvidence);
                    }
                    evidenceBo.deleteAll(evidences);
                    evidences = newEvidences;
                } else {
                    for (Evidence evidence : evidences) {
                        evidence.setAlterations(alterations);
                        evidenceBo.update(evidence);
                    }
                }
            } else if (specialChangeType.equals("TUMOR_NAME_CHANGE")) {
                Evidence oldEvidence = new Evidence(evidences.get(0), evidences.get(0).getId());
                evidenceBo.deleteAll(evidences);
                evidences.removeAll(evidences);

                Evidence tempEvidence = new Evidence(oldEvidence, null);
                tempEvidence.setCancerTypes(queryEvidence.getCancerTypes().stream().map(tumorType -> StringUtils.isNullOrEmpty(tumorType.getCode()) ? ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType.getMainType()) : ApplicationContextSingleton.getTumorTypeBo().getByCode(tumorType.getCode())).filter(tumorType -> tumorType != null).collect(Collectors.toSet()));
                tempEvidence.setExcludedCancerTypes(queryEvidence.getExcludedCancerTypes().stream().map(tumorType -> StringUtils.isNullOrEmpty(tumorType.getCode()) ? ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType.getMainType()) : ApplicationContextSingleton.getTumorTypeBo().getByCode(tumorType.getCode())).filter(tumorType -> tumorType != null).collect(Collectors.toSet()));
                if (queryEvidence.getRelevantCancerTypes() != null) {
                    tempEvidence.setRelevantCancerTypes(queryEvidence.getRelevantCancerTypes());
                } else if (LevelOfEvidence.LEVEL_Dx1.equals(tempEvidence.getLevelOfEvidence())) {
                    tempEvidence.setRelevantCancerTypes(TumorTypeUtils.getDxOneRelevantCancerTypes(tempEvidence.getCancerTypes()));
                }

                initEvidence(tempEvidence, new ArrayList<>(tempEvidence.getTreatments()));

                evidenceBo.save(tempEvidence);
                evidences.add(tempEvidence);
            } else if (specialChangeType.equals("TREATMENT_NAME_CHANGE")) {
                for (Evidence evidence : evidences) {
                    List<Treatment> oldTreatment = evidence.getSortedTreatment();

                    initEvidence(evidence, treatments);
                    evidenceBo.update(evidence);

                    for(Treatment treatment : oldTreatment) {
                        treatment.setEvidence(null);
                        treatmentBo.delete(treatment);
                    }
                }
            }
            return evidences;
        }
        // if passed in evidence is empty, we delete them in the database and return empty list
        if (isEmptyEvidence(queryEvidence)) {
            evidenceBo.deleteAll(evidences);
            return new ArrayList<>(evidences);
        }
        // common cases for evidence update
        // Use controlled vocabulary to update oncogenic knowneffect
        if (evidenceType.equals(EvidenceType.ONCOGENIC)) {
            DriveAnnotationParser driveAnnotationParser = new DriveAnnotationParser();
            Oncogenicity oncogenicity = driveAnnotationParser.getOncogenicityByString(knownEffect);
            if (oncogenicity != null) {
                knownEffect = oncogenicity.getOncogenic();
            }
        }
        // save newly added evidence
        if (evidences.isEmpty()) {
            if (evidenceType.equals(EvidenceType.ONCOGENIC) && alterations.size() > 1) {
                // save duplicated evidence record for string alteration oncogenic
                for (Alteration alteration : alterations) {
                    Evidence evidence = new Evidence(
                        uuid, evidenceType, new HashSet<>(), new HashSet<>(), new HashSet<>(), gene, Collections.singleton(alteration),
                        description, additionalInfo, treatments, knownEffect, null, lastEdit, null,
                        level, fdaLevel, solidPropagation, liquidPropagation, articles
                    );
                    initEvidence(evidence, new ArrayList<>(evidence.getTreatments()));
                    evidences.add(evidence);
                    evidenceBo.save(evidence);
                }
            } else if (!isCancerEvidence) {
                Evidence evidence = new Evidence(
                    uuid, evidenceType, new HashSet<>(), new HashSet<>(), new HashSet<>(), gene, alterations,
                    description, additionalInfo, treatments, knownEffect, null, lastEdit, null,
                    level, fdaLevel, solidPropagation, liquidPropagation, articles
                );
                initEvidence(evidence, new ArrayList<>(evidence.getTreatments()));
                evidenceBo.save(evidence);
                evidences.add(evidence);
            } else {
                Evidence evidence = new Evidence(
                    uuid, evidenceType, cancerTypes, excludedCancerTypes, relevantCancerTypes, gene, alterations,
                    description, additionalInfo, treatments, knownEffect, null, lastEdit, null,
                    level, fdaLevel, solidPropagation, liquidPropagation, articles
                );
                initEvidence(evidence, new ArrayList<>(evidence.getTreatments()));
                evidences.add(evidence);
                evidenceBo.save(evidence);
            }
        } else if (!isCancerEvidence) {
            // For the evidences which tumor type information is not involved, update it directly
            for (Evidence evidence : evidences) {
                if (evidence.getAlterations() == null || evidence.getAlterations().isEmpty()) {
                    evidence.setAlterations(alterations);
                }
                evidence.setEvidenceType(evidenceType);
                evidence.setCancerTypes(new HashSet<>());
                evidence.setExcludedCancerTypes(new HashSet<>());
                evidence.setKnownEffect(knownEffect);
                evidence.setLevelOfEvidence(level);
                evidence.setDescription(description);
                evidence.setAdditionalInfo(additionalInfo);
                evidence.setLastEdit(lastEdit);
                evidence.setTreatments(new ArrayList<>(treatments));
                evidence.setArticles(articles);
                evidence.setSolidPropagationLevel(solidPropagation);
                evidence.setLiquidPropagationLevel(liquidPropagation);
                evidence.setFdaLevel(fdaLevel);
                evidenceBo.update(evidence);
            }
        } else {
            // remove all old evidences
            evidenceBo.deleteAll(evidences);
            evidences.removeAll(evidences);
            // insert cancer type information and save it
            // create a new evidence based on input passed in, and gene and alterations information from the current evidences
            Evidence evidence = new Evidence(
                uuid, evidenceType, cancerTypes, excludedCancerTypes, relevantCancerTypes, gene, alterations,
                description, additionalInfo, treatments, knownEffect, null, lastEdit, null,
                level, fdaLevel, solidPropagation, liquidPropagation, articles
            );

            initEvidence(evidence, new ArrayList<>(evidence.getTreatments()));

            evidenceBo.save(evidence);
            evidences.add(evidence);
        }
        return evidences;
    }

    private Set<Evidence> updateEvidencePriorityBasedOnUuid(Map<String, Map<String, Integer>> newPriorities) {
        Set<Evidence> evidences = new HashSet<>();
        if (newPriorities != null) {
            for (Map.Entry<String, Map<String, Integer>> map : newPriorities.entrySet()) {
                Set<Evidence> evidenceSet = EvidenceUtils.getEvidencesByUUID(map.getKey());
                for (Evidence evidence : evidenceSet) {
                    for (Treatment treatment : evidence.getTreatments()) {
                        String name = treatment.getName().toLowerCase();
                        Set<String> keys = map.getValue().keySet();
                        String matchedKey = null;
                        for (String key : keys) {
                            if (key.toLowerCase().equals(name)) {
                                matchedKey = key;
                            }
                        }
                        if (matchedKey != null) {
                            Integer newPriority = map.getValue().get(matchedKey);
                            if (!newPriority.equals(treatment.getPriority())) {
                                treatment.setPriority(newPriority);
                            }
                        }
                    }
                    ApplicationContextSingleton.getEvidenceBo().saveOrUpdate(evidence);
                }
                evidences.addAll(evidenceSet);
            }
        }
        return evidences;
    }

    private void updateCacheBasedOnEvidences(Set<Evidence> evidences) throws IOException {
        // The sample solution for now is updating all gene related evidences.
        Set<Gene> genes = new HashSet<>();
        for (Evidence evidence : evidences) {
            genes.add(evidence.getGene());
        }
        updateCacheBasedOnGenes(genes);
    }

    private void updateCacheBasedOnGenes(Set<Gene> genes) throws IOException {
        // The sample solution for now is updating all gene related evidences.
        for (Gene gene : genes) {
            ApplicationContextSingleton.getAlterationBo().deleteMutationsWithoutEvidenceAssociatedByGene(gene);
            CacheUtils.updateGene(Collections.singleton(gene.getEntrezGeneId()), true);
        }
    }

    private void initEvidence(Evidence evidence, List<Treatment> treatments) {
        List<Treatment> newTreatmentList = new ArrayList<>();
        for(Treatment treatment : treatments) {
            Treatment newTreatment = new Treatment();
            newTreatment.setDrugs(treatment.getDrugs());
            newTreatment.setPriority(treatment.getPriority());
            newTreatment.setApprovedIndications(treatment.getApprovedIndications());
            newTreatment.setEvidence(evidence);
            newTreatmentList.add(newTreatment);
        }
        evidence.setTreatments(newTreatmentList);
    }
}
