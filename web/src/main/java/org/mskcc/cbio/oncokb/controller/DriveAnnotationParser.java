/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.controller;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mskcc.cbio.oncokb.bo.*;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.mskcc.cbio.oncokb.model.clinicalTrialsMatching.Tumor;
import org.mskcc.cbio.oncokb.bo.OncokbTranscriptService;
import org.mskcc.cbio.oncokb.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * @author jgao
 */
@Controller
public class DriveAnnotationParser {
    OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();

    @RequestMapping(value = "/legacy-api/driveAnnotation", method = POST)
    public
    @ResponseBody
    synchronized void getEvidence(
        @RequestParam(value = "gene") String gene,
        @RequestParam(value = "releaseGene", defaultValue = "FALSE") Boolean releaseGene,
        @RequestParam(value = "vus", required = false) String vus
    ) throws Exception {

        if (gene == null) {
            System.out.println("#No gene info available.");
        } else {
            JSONObject jsonObj = new JSONObject(gene);
            JSONArray jsonArray = null;
            if (vus != null) {
                jsonArray = new JSONArray(vus);
            }
            Gene persistenceGene = parseGene(jsonObj, releaseGene, jsonArray);

            if (releaseGene && persistenceGene != null) {
                this.oncokbTranscriptService.updateTranscriptUsage(
                    persistenceGene,
                    persistenceGene.getGrch37Isoform(),
                    persistenceGene.getGrch38Isoform()
                );
            }
        }
    }

    private static final String LAST_REVIEW_EXTENSION = "_validateTime";
    private static final String LAST_EDIT_EXTENSION = "_review";
    private static final String UUID_EXTENSION = "_uuid";
    private static final String SOLID_PROPAGATION_KEY = "propagation";
    private static final String LIQUID_PROPAGATION_KEY = "propagationLiquid";
    private static final String FDA_LEVEL_KEY = "fdaLevel";

    public void parseVUS(Gene gene, JSONArray vus, Integer nestLevel) throws JSONException {
        System.out.println(spaceStrByNestLevel(nestLevel) + "Variants of unknown significance");
        if (vus != null) {
            AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            AlterationType type = AlterationType.MUTATION; //TODO: cna and fusion

            System.out.println("\t" + vus.length() + " VUSs");
            for (int i = 0; i < vus.length(); i++) {
                JSONObject variant = vus.getJSONObject(i);
                String mutationStr = variant.has("name") ? variant.getString("name") : null;
                JSONObject time = variant.has("time") ? variant.getJSONObject("time") : null;
                Long lastEdit = null;
                if (time != null) {
                    lastEdit = time.has("value") ? time.getLong("value") : null;
                }
//                JSONArray nameComments = variant.has("nameComments") ? variant.getJSONArray("nameComments") : null;
                if (mutationStr != null) {
                    List<Alteration> mutations = AlterationUtils.parseMutationString(mutationStr, ",");
                    Set<Alteration> alterations = new HashSet<>();
                    for (Alteration mutation : mutations) {
                        Alteration alteration = alterationBo.findAlteration(gene, type, mutation.getAlteration());
                        if (alteration == null) {
                            alteration = new Alteration();
                            alteration.setGene(gene);
                            alteration.setAlterationType(type);
                            alteration.setAlteration(mutation.getAlteration());
                            alteration.setName(mutation.getName());
                            alteration.setReferenceGenomes(mutation.getReferenceGenomes());
                            AlterationUtils.annotateAlteration(alteration, mutation.getAlteration());
                            alterationBo.save(alteration);
                        } else if (!alteration.getReferenceGenomes().equals(mutation.getReferenceGenomes())) {
                            alteration.setReferenceGenomes(mutation.getReferenceGenomes());
                            alterationBo.save(alteration);
                        }
                        alterations.add(alteration);
                    }

                    Evidence evidence = new Evidence();
                    evidence.setEvidenceType(EvidenceType.VUS);
                    evidence.setGene(gene);
                    evidence.setAlterations(alterations);
                    if (lastEdit != null) {
                        Date date = new Date(lastEdit);
                        evidence.setLastEdit(date);
//                        evidence.setLastReview(date);
                    }
                    if (evidence.getLastEdit() == null) {
                        System.out.println(spaceStrByNestLevel(nestLevel + 1) + "WARNING: " + mutationStr + " do not have last update.");
                    }
                    evidenceBo.save(evidence);
                }
                if (i % 10 == 9)
                    System.out.println("\t\tImported " + (i + 1));
            }
        } else {
            if (vus == null) {
                System.out.println(spaceStrByNestLevel(nestLevel) + "No VUS available.");
            }
        }
    }

    private void updateGeneInfo(JSONObject geneInfo, Gene gene) {
        JSONObject geneType = geneInfo.has("type") ? geneInfo.getJSONObject("type") : null;
        String oncogene = geneType == null ? null : (geneType.has("ocg") ? geneType.getString("ocg").trim() : null);
        String tsg = geneType == null ? null : (geneType.has("tsg") ? geneType.getString("tsg").trim() : null);

        if (oncogene != null) {
            if (oncogene.equals("Oncogene")) {
                gene.setOncogene(true);
            } else {
                gene.setOncogene(false);
            }
        }
        if (tsg != null) {
            if (tsg.equals("Tumor Suppressor")) {
                gene.setTSG(true);
            } else {
                gene.setTSG(false);
            }
        }

        String grch37Isoform = geneInfo.has("isoform_override") ? geneInfo.getString("isoform_override") : null;
        String grch37RefSeq = geneInfo.has("dmp_refseq_id") ? geneInfo.getString("dmp_refseq_id") : null;
        String grch38Isoform = geneInfo.has("isoform_override_grch38") ? geneInfo.getString("isoform_override_grch38") : null;
        String grch38RefSeq = geneInfo.has("dmp_refseq_id_grch38") ? geneInfo.getString("dmp_refseq_id_grch38") : null;

        if (grch37Isoform != null) {
            gene.setGrch37Isoform(grch37Isoform);
        }
        if (grch37RefSeq != null) {
            gene.setGrch37RefSeq(grch37RefSeq);
        }
        if (grch38Isoform != null) {
            gene.setGrch38Isoform(grch38Isoform);
        }
        if (grch38RefSeq != null) {
            gene.setGrch38RefSeq(grch38RefSeq);
        }
    }

    private Gene parseGene(JSONObject geneInfo, Boolean releaseGene, JSONArray vus) throws Exception {
        GeneBo geneBo = ApplicationContextSingleton.getGeneBo();
        Integer nestLevel = 1;
        if (geneInfo.has("name") && !geneInfo.getString("name").trim().isEmpty()) {
            String hugo = geneInfo.has("name") ? geneInfo.getString("name").trim() : null;

            if (hugo != null) {
                Gene gene = geneBo.findGeneByHugoSymbol(hugo);

                if (gene == null) {
                    System.out.println(spaceStrByNestLevel(nestLevel) + "Gene " + hugo + " is not in the released list.");
                    if (releaseGene) {
                        OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();
                        gene = oncokbTranscriptService.findGeneBySymbol(hugo);
                        if (gene == null) {
                            System.out.println("!!!!!!!!!Could not find gene " + hugo + " either.");
                            throw new IOException("!!!!!!!!!Could not find gene " + hugo + ".");
                        } else {
                            updateGeneInfo(geneInfo, gene);
                            geneBo.saveOrUpdate(gene);
                        }
                    } else {
                        return null;
                    }
                }

                if (gene != null) {
                    System.out.println(spaceStrByNestLevel(nestLevel) + "Gene: " + gene.getHugoSymbol());
                    updateGeneInfo(geneInfo, gene);
                    geneBo.update(gene);

                    EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
                    AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
                    List<Evidence> evidences = evidenceBo.findEvidencesByGene(Collections.singleton(gene));
                    List<Alteration> alterations = alterationBo.findAlterationsByGene(Collections.singleton(gene));

                    for (Evidence evidence : evidences) {
                        evidenceBo.delete(evidence);
                    }

                    for (Alteration alteration : alterations) {
                        alterationBo.delete(alteration);
                    }

                    CacheUtils.updateGene(Collections.singleton(gene.getEntrezGeneId()), false);

                    // summary
                    parseSummary(gene, geneInfo.has("summary") ? geneInfo.getString("summary").trim() : null, getUUID(geneInfo, "summary"), getLastEdit(geneInfo, "summary"), nestLevel + 1);

                    // background
                    parseGeneBackground(gene, geneInfo.has("background") ? geneInfo.getString("background").trim() : null, getUUID(geneInfo, "background"), getLastEdit(geneInfo, "background"), nestLevel + 1);

                    // mutations
                    parseMutations(gene, geneInfo.has("mutations") ? geneInfo.getJSONArray("mutations") : null, nestLevel + 1);

                    // Variants of unknown significance
                    parseVUS(gene, vus, nestLevel + 1);

                    CacheUtils.updateGene(Collections.singleton(gene.getEntrezGeneId()), true);
                } else {
                    System.out.print(spaceStrByNestLevel(nestLevel) + "No info about " + hugo);
                }
                return gene;
            } else {
                System.out.println(spaceStrByNestLevel(nestLevel) + "No hugoSymbol available");
            }
        }
        return null;
    }

    private Date getUpdateTime(Object obj) throws JSONException {
        if (obj == null) return null;
        JSONObject reviewObj = new JSONObject(obj.toString());
        if (reviewObj.has("updateTime") && StringUtils.isNumeric(reviewObj.get("updateTime").toString())) {
            return new Date(reviewObj.getLong("updateTime"));
        }
        return null;
    }

    private void parseSummary(Gene gene, String geneSummary, String uuid, Date lastEdit, Integer nestLevel) {
        System.out.println(spaceStrByNestLevel(nestLevel) + "Summary");
        // gene summary
        if (geneSummary != null && !geneSummary.isEmpty()) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.GENE_SUMMARY);
            evidence.setGene(gene);
            evidence.setDescription(geneSummary);
            evidence.setUuid(uuid);
            evidence.setLastEdit(lastEdit);
//            evidence.setLastReview(lastReview);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) +
                    "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
//            if (lastReview != null) {
//                System.out.println(spaceStrByNestLevel(nestLevel + 1) +
//                    "Last review on: " + MainUtils.getTimeByDate(lastReview));
//            }
            setDocuments(geneSummary, evidence);
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidenceBo.save(evidence);
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Has description");
        }
    }

    private void parseGeneBackground(Gene gene, String bg, String uuid, Date lastEdit, Integer nestLevel) {
        System.out.println(spaceStrByNestLevel(nestLevel) + "Background");

        if (bg != null && !bg.isEmpty()) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.GENE_BACKGROUND);
            evidence.setGene(gene);
            evidence.setDescription(bg);
            evidence.setUuid(uuid);
            evidence.setLastEdit(lastEdit);
//            evidence.setLastReview(lastReview);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) +
                    "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
            setDocuments(bg, evidence);
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidenceBo.save(evidence);
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Has description");
        }
    }

    private void parseMutations(Gene gene, JSONArray mutations, Integer nestLevel) throws Exception {
        if (mutations != null) {
            System.out.println(spaceStrByNestLevel(nestLevel) + mutations.length() + " mutations.");
            for (int i = 0; i < mutations.length(); i++) {
                parseMutation(gene, mutations.getJSONObject(i), nestLevel + 1);
            }
        } else {
            System.out.println(spaceStrByNestLevel(nestLevel) + "No mutation.");
        }
    }

    private void parseMutation(Gene gene, JSONObject mutationObj, Integer nestLevel) throws Exception {
        String mutationStr = mutationObj.has("name") ? mutationObj.getString("name").trim() : null;

        if (mutationStr != null && !mutationStr.isEmpty() && !mutationStr.contains("?")) {
            System.out.println(spaceStrByNestLevel(nestLevel) + "Mutation: " + mutationStr);

            AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
            AlterationType type = AlterationType.MUTATION; //TODO: cna and fusion

            Set<Alteration> alterations = new HashSet<>();

            JSONObject mutationEffect = mutationObj.has("mutation_effect") ? mutationObj.getJSONObject("mutation_effect") : null;

            Oncogenicity oncogenic = getOncogenicity(mutationEffect);
            String oncogenic_uuid = getUUID(mutationEffect, "oncogenic");
            Date oncogenic_lastEdit = getLastEdit(mutationEffect, "oncogenic");
//            Date oncogenic_lastReview = getLastReview(mutationEffect, "oncogenic");

            Set<Date> lastEditDatesEffect = new HashSet<>();
            Set<Date> lastReviewDatesEffect = new HashSet<>();

            String effect = mutationEffect.has("effect") ? mutationEffect.getString("effect") : null;
            addDateToLastEditSetFromObject(lastEditDatesEffect, mutationEffect, "effect");
//            addDateToLastReviewSetFromLong(lastReviewDatesEffect, mutationEffect, "effect");
            String effect_uuid = getUUID(mutationEffect, "effect");

            List<Alteration> mutations = AlterationUtils.parseMutationString(mutationStr, ",");
            for (Alteration mutation : mutations) {
                Alteration alteration = alterationBo.findAlteration(gene, type, mutation.getAlteration());
                if (alteration == null) {
                    alteration = new Alteration();
                    alteration.setGene(gene);
                    alteration.setAlterationType(type);
                    alteration.setAlteration(mutation.getAlteration());
                    alteration.setName(mutation.getName());
                    alteration.setReferenceGenomes(mutation.getReferenceGenomes());
                    AlterationUtils.annotateAlteration(alteration, mutation.getAlteration());
                    alterationBo.save(alteration);
                } else if (!alteration.getReferenceGenomes().equals(mutation.getReferenceGenomes())) {
                    alteration.setReferenceGenomes(mutation.getReferenceGenomes());
                    alterationBo.save(alteration);
                }
                alterations.add(alteration);
                setOncogenic(gene, alteration, oncogenic, oncogenic_uuid, oncogenic_lastEdit);
            }

            // mutation effect
            String effectDesc = mutationEffect.has("description") ?
                (mutationEffect.getString("description").trim().isEmpty() ? null :
                    mutationEffect.getString("description").trim())
                : null;
            addDateToLastEditSetFromObject(lastEditDatesEffect, mutationEffect, "description");
//            addDateToLastReviewSetFromLong(lastReviewDatesEffect, mutationEffect, "description");
//            String additionalME = mutationEffect.has("short") ?
//                (mutationEffect.getString("short").trim().isEmpty() ? null : mutationEffect.getString("short").trim())
//                : null;

            if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(effect) || !com.mysql.jdbc.StringUtils.isNullOrEmpty(effectDesc)) {
                // save
                Evidence evidence = new Evidence();
                evidence.setEvidenceType(EvidenceType.MUTATION_EFFECT);
                evidence.setAlterations(alterations);
                evidence.setGene(gene);

                if ((effectDesc != null && !effectDesc.trim().isEmpty())) {
                    evidence.setDescription(effectDesc);
                    setDocuments(effectDesc, evidence);
                }

//                if ((additionalME != null && !additionalME.trim().isEmpty())) {
//                    evidence.setAdditionalInfo(additionalME);
//                }

                evidence.setKnownEffect(effect);
                evidence.setUuid(effect_uuid);

                Date effect_lastEdit = getMostRecentDate(lastEditDatesEffect);
                evidence.setLastEdit(effect_lastEdit);

//                Date effect_lastReview = getMostRecentDate(lastReviewDatesEffect);
//                evidence.setLastReview(effect_lastReview);

                EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
                evidenceBo.save(evidence);
            }

            // cancers
            if (mutationObj.has("tumors")) {
                JSONArray cancers = mutationObj.getJSONArray("tumors");
                if (cancers != null && cancers.length() > 0) {
                    System.out.println(spaceStrByNestLevel(nestLevel) + "Tumor Types");
                }
                for (int i = 0; i < cancers.length(); i++) {
                    JSONArray subTumorTypes = cancers.getJSONObject(i).getJSONArray("cancerTypes");
                    List<TumorType> tumorTypes = getTumorTypes(subTumorTypes);

                    List<TumorType> excludedCancerTypes = new ArrayList<>();
                    if (cancers.getJSONObject(i).has("excludedCancerTypes")) {
                        excludedCancerTypes = getTumorTypes(cancers.getJSONObject(i).getJSONArray("excludedCancerTypes"));
                    }

                    List<TumorType> relevantCancerTypes = new ArrayList<>();
                    if (cancers.getJSONObject(i).has("relevantCancerTypes")) {
                        relevantCancerTypes = getTumorTypes(cancers.getJSONObject(i).getJSONArray("relevantCancerTypes"));
                    }
                    parseCancer(gene, alterations, cancers.getJSONObject(i), tumorTypes, excludedCancerTypes, relevantCancerTypes, nestLevel + 1);
                }
            }
        } else {
            System.out.println(spaceStrByNestLevel(nestLevel) + "Mutation does not have name skip...");
        }
    }

    private List<TumorType> getTumorTypes(JSONArray tumorTypeJson) throws Exception {
        List<TumorType> tumorTypes = new ArrayList<>();
        for (int j = 0; j < tumorTypeJson.length(); j++) {
            JSONObject subTT = tumorTypeJson.getJSONObject(j);
            String code = (subTT.has("code") && !subTT.getString("code").equals("")) ? subTT.getString("code") : null;
            String mainType = subTT.has("mainType") ? subTT.getString("mainType") : null;
            if (code != null) {
                TumorType matchedTumorType = ApplicationContextSingleton.getTumorTypeBo().getByCode(code);
                if (matchedTumorType == null) {
                    throw new Exception("The tumor type code does not exist: " + code);
                } else {
                    tumorTypes.add(matchedTumorType);
                }
            } else if(mainType != null){
                TumorType matchedTumorType = ApplicationContextSingleton.getTumorTypeBo().getByMainType(mainType);
                if (matchedTumorType == null) {
                    throw new Exception("The tumor main type does not exist: " + mainType);
                } else {
                    tumorTypes.add(matchedTumorType);
                }
            } else {
                throw new Exception("The tumor type does not exist. Maintype: " + mainType + ". Subtype: " + code);
            }
        }
        return tumorTypes;
    }

    protected Oncogenicity getOncogenicityByString(String oncogenicStr) {
        Oncogenicity oncogenic = null;
        if (oncogenicStr != null) {
            oncogenicStr = oncogenicStr.toLowerCase();
            switch (oncogenicStr) {
                case "yes":
                    oncogenic = Oncogenicity.YES;
                    break;
                case "likely":
                    oncogenic = Oncogenicity.LIKELY;
                    break;
                case "likely neutral":
                    oncogenic = Oncogenicity.LIKELY_NEUTRAL;
                    break;
                case "resistance":
                    oncogenic = Oncogenicity.RESISTANCE;
                    break;
                case "inconclusive":
                    oncogenic = Oncogenicity.INCONCLUSIVE;
                    break;
                default:
                    break;
            }
        }
        return oncogenic;
    }

    private Oncogenicity getOncogenicity(JSONObject mutationEffect) throws JSONException {
        Oncogenicity oncogenic = null;
        if (mutationEffect.has("oncogenic") && !mutationEffect.getString("oncogenic").isEmpty()) {
            oncogenic = getOncogenicityByString(mutationEffect.getString("oncogenic"));
        }
        return oncogenic;
    }

    private void setOncogenic(Gene gene, Alteration alteration, Oncogenicity oncogenic, String uuid, Date lastEdit) {
        if (alteration != null && gene != null && oncogenic != null) {
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            List<Evidence> evidences = evidenceBo.findEvidencesByAlteration(Collections.singleton(alteration), Collections.singleton(EvidenceType.ONCOGENIC));
            if (evidences.isEmpty()) {
                Evidence evidence = new Evidence();
                evidence.setGene(gene);
                evidence.setAlterations(Collections.singleton(alteration));
                evidence.setEvidenceType(EvidenceType.ONCOGENIC);
                evidence.setKnownEffect(oncogenic.getOncogenic());
                evidence.setUuid(uuid);
                evidence.setLastEdit(lastEdit);
//                evidence.setLastReview(lastReview);
                evidenceBo.save(evidence);
            } else if (Oncogenicity.compare(oncogenic, Oncogenicity.getByEvidence(evidences.get(0))) > 0) {
                evidences.get(0).setKnownEffect(oncogenic.getOncogenic());
                evidences.get(0).setLastEdit(lastEdit);
                evidenceBo.update(evidences.get(0));
            }
        }
    }

    private void saveDxPxSummaries(JSONObject cancerObj, String summaryKey, Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, EvidenceType evidenceType, Integer nestLevel, LevelOfEvidence level) {
        List<TumorType> rcts = new ArrayList<>(relevantCancerTypes);
        if ((rcts == null || rcts.size() == 0) && LevelOfEvidence.LEVEL_Dx1.equals(level)) {
            rcts.addAll(TumorTypeUtils.getDxOneRelevantCancerTypes(new HashSet<>(tumorTypes)));
        }
        saveTumorLevelSummaries(
            cancerObj,
            summaryKey,
            gene,
            alterations,
            tumorTypes,
            excludedCancerTypes,
            rcts,
            evidenceType,
            nestLevel);
    }

    private void saveTumorLevelSummaries(JSONObject cancerObj, String summaryKey, Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, EvidenceType evidenceType, Integer nestLevel) {
        if (cancerObj.has(summaryKey) && !cancerObj.getString(summaryKey).isEmpty()) {
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + " " + summaryKey);
            Date lastEdit = getLastEdit(cancerObj, summaryKey);
//            Date lastReview = getLastReview(cancerObj, summaryKey);
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(evidenceType);
            evidence.setGene(gene);
            evidence.setDescription(cancerObj.getString(summaryKey));
            evidence.setUuid(getUUID(cancerObj, summaryKey));
            evidence.setAlterations(alterations);
            evidence.setLastEdit(lastEdit);
            if (excludedCancerTypes != null && !excludedCancerTypes.isEmpty()) {
                evidence.setExcludedCancerTypes(new HashSet<>(excludedCancerTypes));
            }
            if (relevantCancerTypes != null && !relevantCancerTypes.isEmpty()) {
                evidence.setRelevantCancerTypes(new HashSet<>(relevantCancerTypes));
            }
//            evidence.setLastReview(lastReview);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 2) +
                    "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
            if (!tumorTypes.isEmpty()) {
                evidence.setCancerTypes(new HashSet<>(tumorTypes));
            }
            setDocuments(cancerObj.getString(summaryKey), evidence);
            System.out.println(spaceStrByNestLevel(nestLevel + 2) +
                "Has description.");
            evidenceBo.save(evidence);
        }
    }

    private void parseCancer(Gene gene, Set<Alteration> alterations, JSONObject cancerObj, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, Integer nestLevel) throws Exception {
        if (tumorTypes.isEmpty()) {
            return;
        }

        System.out.println(spaceStrByNestLevel(nestLevel) + "Tumor types: " + tumorTypes.stream().map(TumorTypeUtils::getTumorTypeName).collect(Collectors.joining(", ")));

        // cancer type summary
        saveTumorLevelSummaries(cancerObj, "summary", gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes, EvidenceType.TUMOR_TYPE_SUMMARY, nestLevel);

        // Prognostic implications
        Evidence prognosticEvidence = parseImplication(gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes,
            cancerObj.has("prognostic") ? cancerObj.getJSONObject("prognostic") : null,
            getUUID(cancerObj, "prognostic"),
            EvidenceType.PROGNOSTIC_IMPLICATION, nestLevel + 1);

        // Diagnostic implications
        Evidence diagnosticEvidence = parseImplication(gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes,
            cancerObj.has("diagnostic") ? cancerObj.getJSONObject("diagnostic") : null,
            getUUID(cancerObj, "diagnostic"),
            EvidenceType.DIAGNOSTIC_IMPLICATION, nestLevel + 1);

        // diagnostic summary
        saveDxPxSummaries(
            cancerObj,
            "diagnosticSummary",
            gene,
            alterations,
            tumorTypes,
            excludedCancerTypes,
            cancerObj.has("diagnostic") && cancerObj.getJSONObject("diagnostic").has("relevantCancerTypes") ? getTumorTypes(cancerObj.getJSONObject("diagnostic").getJSONArray("relevantCancerTypes")) : relevantCancerTypes,
            EvidenceType.DIAGNOSTIC_SUMMARY,
            nestLevel,
            diagnosticEvidence == null ? null : diagnosticEvidence.getLevelOfEvidence()
        );

        // prognostic summary
        saveDxPxSummaries(cancerObj,
            "prognosticSummary",
            gene,
            alterations,
            tumorTypes,
            excludedCancerTypes,
            cancerObj.has("prognostic") && cancerObj.getJSONObject("prognostic").has("relevantCancerTypes") ? getTumorTypes(cancerObj.getJSONObject("prognostic").getJSONArray("relevantCancerTypes")) : relevantCancerTypes,
            EvidenceType.PROGNOSTIC_SUMMARY,
            nestLevel,
            prognosticEvidence == null ? null : prognosticEvidence.getLevelOfEvidence()
        );

        JSONArray implications = cancerObj.getJSONArray("TIs");

        for (int i = 0; i < implications.length(); i++) {
            JSONObject implication = implications.getJSONObject(i);
            if ((implication.has("description") && !implication.getString("description").trim().isEmpty()) || (implication.has("treatments") && implication.getJSONArray("treatments").length() > 0)) {
                EvidenceType evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY;
                String type = "";
                if (implication.has("type")) {
                    if (implication.getString("type").equals("SS")) {
                        evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY;
                        type = "Sensitive";
                    } else if (implication.getString("type").equals("SR")) {
                        evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE;
                        type = "Resistant";
                    }
                    if (implication.getString("type").equals("IS")) {
                        evidenceType = EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY;
                        type = "Sensitive";
                    } else if (implication.getString("type").equals("IR")) {
                        evidenceType = EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE;
                        type = "Resistant";
                    }
                    parseTherapeuticImplications(gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes, implication, evidenceType, type, nestLevel + 1);
                }
            }
        }
    }

    private void parseTherapeuticImplications(Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, JSONObject implicationObj,
                                                     EvidenceType evidenceType, String knownEffectOfEvidence, Integer nestLevel) throws Exception {
        System.out.println(spaceStrByNestLevel(nestLevel) + evidenceType);

        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

        if (implicationObj.has("description") && !implicationObj.getString("description").trim().isEmpty()) {
            // general description
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Has General Description.");
            Date lastEdit = getLastEdit(implicationObj, "description");
//            Date lastReview = getLastReview(implicationObj, "description");
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(evidenceType);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setCancerTypes(new HashSet<>(tumorTypes));
            evidence.setKnownEffect(knownEffectOfEvidence);
            evidence.setUuid(getUUID(implicationObj, "description"));
            evidence.setLastEdit(lastEdit);
//            evidence.setLastReview(lastReview);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) +
                    "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
//            if (lastReview != null) {
//                System.out.println(spaceStrByNestLevel(nestLevel + 1) +
//                    "Last review on: " + MainUtils.getTimeByDate(lastReview));
//            }
            String desc = implicationObj.getString("description");
            evidence.setDescription(desc);
            setDocuments(desc, evidence);

            if (excludedCancerTypes != null) {
                evidence.setExcludedCancerTypes(new HashSet<>(excludedCancerTypes));
            }

            if (implicationObj.has("relevantCancerTypes")) {
                evidence.setRelevantCancerTypes(new HashSet<>(getTumorTypes(implicationObj.getJSONArray("relevantCancerTypes"))));
            } else if (relevantCancerTypes != null) {
                evidence.setRelevantCancerTypes(new HashSet<>(relevantCancerTypes));
            }

            evidenceBo.save(evidence);
        }

        // specific evidence
        DrugBo drugBo = ApplicationContextSingleton.getDrugBo();
        JSONArray treatmentsArray = implicationObj.has("treatments") ? implicationObj.getJSONArray("treatments") : new JSONArray();
        int priorityCount = 1;
        for (int i = 0; i < treatmentsArray.length(); i++) {
            JSONObject drugObj = treatmentsArray.getJSONObject(i);
            if (!drugObj.has("name") || drugObj.getJSONArray("name").length() == 0) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Drug does not have name, skip... " + drugObj.toString());
                continue;
            }

            JSONArray therapiesArray = drugObj.getJSONArray("name");
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Drug(s): " + therapiesArray.length());

            Set<Date> lastEditDates = new HashSet<>();
            Set<Date> lastReviewDates = new HashSet<>();
            addDateToLastEditSetFromObject(lastEditDates, drugObj, "name");
//            addDateToLastReviewSetFromLong(lastReviewDates, drugObj, "name");

            Evidence evidence = new Evidence();
            evidence.setEvidenceType(evidenceType);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setCancerTypes(new HashSet<>(tumorTypes));
            evidence.setKnownEffect(knownEffectOfEvidence);
            evidence.setUuid(getUUID(drugObj, "name"));

            // approved indications
            Set<String> approvedIndications = new HashSet<>();
            if (drugObj.has("indication") && !drugObj.getString("indication").trim().isEmpty()) {
                approvedIndications = new HashSet<>(Arrays.asList(drugObj.getString("indication").split(";")));
                addDateToLastEditSetFromObject(lastEditDates, drugObj, "indication");
//                addDateToLastReviewSetFromLong(lastReviewDates, drugObj, "indication");
            }

            List<Treatment> treatments = new ArrayList<>();
            for (int j = 0; j < therapiesArray.length(); j++) {
                JSONArray drugsArray = therapiesArray.getJSONArray(j);

                List<Drug> drugs = new ArrayList<>();
                for (int k = 0; k < drugsArray.length(); k++) {
                    JSONObject drugObject = drugsArray.getJSONObject(k);

                    String ncitCode = drugObject.has("ncitCode") ? drugObject.getString("ncitCode").trim() : null;
                    if (ncitCode != null && ncitCode.isEmpty()) {
                        ncitCode = null;
                    }
                    String drugName = drugObject.has("drugName") ? drugObject.getString("drugName").trim() : null;
                    if (drugName != null && drugName.isEmpty()) {
                        drugName = null;
                    }
                    String drugUuid = drugObject.has("uuid") ? drugObject.getString("uuid").trim() : null;
                    Drug drug = null;
                    if (ncitCode != null) {
                        drug = drugBo.findDrugsByNcitCode(ncitCode);
                    }
                    if (drug == null && drugName != null) {
                        drug = drugBo.findDrugByName(drugName);
                    }
                    if (drug == null) {
                        if (ncitCode != null) {
                            org.oncokb.oncokb_transcript.client.Drug ncitDrug = oncokbTranscriptService.findDrugByNcitCode(ncitCode);
                            if (ncitDrug == null) {
                                System.out.println("ERROR: the NCIT code cannot be found... Code:" + ncitCode);
                            } else {
                                drug = new Drug();
                                drug.setDrugName(ncitDrug.getName());
                                drug.setSynonyms(ncitDrug.getSynonyms().stream().map(synonym -> synonym.getName()).collect(Collectors.toSet()));
                                drug.setNcitCode(ncitDrug.getCode());

                                if (drugName != null) {
                                    DrugUtils.updateDrugName(drug, drugName);
                                }
                            }
                        }
                        if (drug == null) {
                            drug = new Drug();
                            drug.setNcitCode(ncitCode);
                            drug.setDrugName(drugName);
                        }
                        if (drugUuid != null) {
                            drug.setUuid(drugUuid);
                        }
                        drugBo.save(drug);
                    }
                    drugs.add(drug);
                }

                Treatment treatment = new Treatment();
                treatment.setDrugs(drugs);
                treatment.setPriority(priorityCount);
                treatment.setApprovedIndications(approvedIndications);
                treatment.setEvidence(evidence);

                treatments.add(treatment);
                priorityCount++;
            }
            evidence.setTreatments(treatments);

            // highest level of evidence
            if (!drugObj.has("level") || drugObj.getString("level").trim().isEmpty()) {
                System.err.println(spaceStrByNestLevel(nestLevel + 2) + "Error: no level of evidence");
                // TODO:
                //throw new RuntimeException("no level of evidence");
            } else {
                String level = drugObj.getString("level").trim();
                addDateToLastEditSetFromObject(lastEditDates, drugObj, "level");
//                addDateToLastReviewSetFromLong(lastReviewDates, drugObj, "level");

                LevelOfEvidence levelOfEvidence = LevelOfEvidence.getByLevel(level.toUpperCase());
                if (levelOfEvidence == null) {
                    System.err.println(spaceStrByNestLevel(nestLevel + 2) + "Error: wrong level of evidence: " + level);
                    // TODO:
                    //throw new RuntimeException("wrong level of evidence: "+level);
                    continue;
                } else if (LevelUtils.getAllowedCurationLevels().contains(levelOfEvidence)) {
                    System.out.println(spaceStrByNestLevel(nestLevel + 2) +
                        "Level: " + levelOfEvidence.getLevel());
                } else {
                    System.err.println(spaceStrByNestLevel(nestLevel + 2) +
                        "Level not allowed: " + levelOfEvidence.getLevel());
                    continue;
                }
                evidence.setLevelOfEvidence(levelOfEvidence);

                LevelOfEvidence fdaLevel;
                if (drugObj.has(FDA_LEVEL_KEY)) {
                    String fdaLevelStr = drugObj.getString(FDA_LEVEL_KEY);
                    fdaLevel = LevelOfEvidence.getByLevel(fdaLevelStr);
                    System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Manual FDA level: " + fdaLevel);
                } else {
                    fdaLevel = FdaAlterationUtils.convertToFdaLevel(evidence.getLevelOfEvidence());
                    if (fdaLevel != null) {
                        System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Default FDA level: " + fdaLevel);
                    }
                }
                if (fdaLevel != null && LevelUtils.getAllowedFdaLevels().contains(fdaLevel)) {
                    evidence.setFdaLevel(fdaLevel);
                }

                if (drugObj.has(SOLID_PROPAGATION_KEY)) {
                    String definedPropagation = drugObj.getString(SOLID_PROPAGATION_KEY);
                    LevelOfEvidence definedLevel = LevelOfEvidence.getByLevel(definedPropagation.toUpperCase());

                    // Validate level
                    if (definedLevel != null && LevelUtils.getAllowedPropagationLevels().contains(definedLevel)) {
                        evidence.setSolidPropagationLevel(definedLevel);
                    }
                    if (evidence.getSolidPropagationLevel() != null) {
                        System.out.println(spaceStrByNestLevel(nestLevel + 2) +
                            "Manual solid propagation level: " + evidence.getSolidPropagationLevel());
                    }
                } else {
                    evidence.setSolidPropagationLevel(LevelUtils.getDefaultPropagationLevelByTumorForm(evidence, TumorForm.SOLID));
                }


                if (drugObj.has(LIQUID_PROPAGATION_KEY)) {
                    String definedPropagation = drugObj.getString(LIQUID_PROPAGATION_KEY);
                    LevelOfEvidence definedLevel = LevelOfEvidence.getByLevel(definedPropagation.toUpperCase());

                    // Validate level
                    if (definedLevel != null && LevelUtils.getAllowedPropagationLevels().contains(definedLevel)) {
                        evidence.setLiquidPropagationLevel(definedLevel);
                    }
                    if (evidence.getLiquidPropagationLevel() != null) {
                        System.out.println(spaceStrByNestLevel(nestLevel + 2) +
                            "Manual liquid propagation level: " + evidence.getLiquidPropagationLevel());
                    }
                } else {
                    evidence.setLiquidPropagationLevel(LevelUtils.getDefaultPropagationLevelByTumorForm(evidence, TumorForm.LIQUID));
                }
            }

            // description
//            if (drugObj.has("short") && !drugObj.getString("short").trim().isEmpty()) {
//                String additionalInfo = drugObj.getString("short").trim();
//                evidence.setAdditionalInfo(additionalInfo);
//            }
            if (drugObj.has("description") && !drugObj.getString("description").trim().isEmpty()) {
                String desc = drugObj.getString("description").trim();
                addDateToLastEditSetFromObject(lastEditDates, drugObj, "description");
//                addDateToLastReviewSetFromLong(lastReviewDates, drugObj, "description");
                evidence.setDescription(desc);
                System.out.println(spaceStrByNestLevel(nestLevel + 2) +
                    "Has description.");
                setDocuments(desc, evidence);
            }

            Date lastEdit = getMostRecentDate(lastEditDates);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 2) +
                    "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
            evidence.setLastEdit(lastEdit);

//            Date lastReview = getMostRecentDate(lastReviewDates);
//            if (lastReview != null) {
//                System.out.println(spaceStrByNestLevel(nestLevel + 2) +
//                    "Last update on: " + MainUtils.getTimeByDate(lastReview));
//            }
//            evidence.setLastReview(lastReview);

            if (excludedCancerTypes != null) {
                evidence.setExcludedCancerTypes(new HashSet<>(excludedCancerTypes));
            }

            if (drugObj.has("relevantCancerTypes")) {
                evidence.setRelevantCancerTypes(new HashSet<>(getTumorTypes(drugObj.getJSONArray("relevantCancerTypes"))));
            } else if (relevantCancerTypes != null) {
                evidence.setRelevantCancerTypes(new HashSet<>(relevantCancerTypes));
            }

            evidenceBo.save(evidence);
        }
    }

    private Evidence parseImplication(Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, JSONObject implication, String uuid, EvidenceType evidenceType, Integer nestLevel) throws Exception {
        if (evidenceType != null && implication != null &&
            ((implication.has("description") && !implication.getString("description").trim().isEmpty())
                || (implication.has("level") && !implication.getString("level").trim().isEmpty()))) {
            System.out.println(spaceStrByNestLevel(nestLevel) + evidenceType.name() + ":");
            Set<Date> lastEditDates = new HashSet<>();
            Set<Date> lastReviewDates = new HashSet<>();
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            Evidence evidence = new Evidence();

            evidence.setEvidenceType(evidenceType);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setUuid(uuid);
            evidence.setCancerTypes(new HashSet<>(tumorTypes));

            if (excludedCancerTypes != null) {
                evidence.setExcludedCancerTypes(new HashSet<>(excludedCancerTypes));
            }

            if (implication.has("level") && !implication.getString("level").trim().isEmpty()) {
                LevelOfEvidence level = LevelOfEvidence.getByLevel(implication.getString("level").trim());
                System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Level of the implication: " + level);
                evidence.setLevelOfEvidence(level);
                addDateToLastEditSetFromObject(lastEditDates, implication, "level");
            }

            if (implication.has("relevantCancerTypes")) {
                evidence.setRelevantCancerTypes(new HashSet<>(getTumorTypes(implication.getJSONArray("relevantCancerTypes"))));
            } else if (relevantCancerTypes != null && relevantCancerTypes.size() > 0) {
                evidence.setRelevantCancerTypes(new HashSet<>(relevantCancerTypes));
            } else if (LevelOfEvidence.LEVEL_Dx1.equals(evidence.getLevelOfEvidence())) {
                evidence.setRelevantCancerTypes(TumorTypeUtils.getDxOneRelevantCancerTypes(new HashSet<>(tumorTypes)));
            }

            if (implication.has("description") && !implication.getString("description").trim().isEmpty()) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Has description.");
                String desc = implication.getString("description").trim();
                evidence.setDescription(desc);
                addDateToLastEditSetFromObject(lastEditDates, implication, "description");
//                addDateToLastReviewSetFromLong(lastReviewDates, implication, "description");
                setDocuments(desc, evidence);
            }

            Date lastEdit = getMostRecentDate(lastEditDates);
            evidence.setLastEdit(lastEdit);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) +
                    "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }

//            Date lastReview = getMostRecentDate(lastReviewDates);
//            evidence.setLastReview(lastReview);
//            if (lastReview != null) {
//                System.out.println(spaceStrByNestLevel(nestLevel + 1) +
//                    "Last review on: " + MainUtils.getTimeByDate(lastReview));
//            }
            evidenceBo.save(evidence);
            return evidence;
        }
        return null;
    }

    private String spaceStrByNestLevel(Integer nestLevel) {
        if (nestLevel == null || nestLevel < 1)
            nestLevel = 1;
        return StringUtils.repeat("    ", nestLevel - 1);
    }

    private void setDocuments(String str, Evidence evidence) {
        if (str == null) return;
        Set<Article> docs = new HashSet<>();
        ArticleBo articleBo = ApplicationContextSingleton.getArticleBo();
        Pattern pmidPattern = Pattern.compile("PMIDs?:?\\s*([\\d,\\s*]+)", Pattern.CASE_INSENSITIVE);
        Pattern abstractPattern = Pattern.compile("\\(?\\s*Abstract\\s*:([^\\)]*);?\\s*\\)?", Pattern.CASE_INSENSITIVE);
        Pattern abItemPattern = Pattern.compile("(.*?)\\.\\s*(http.*)", Pattern.CASE_INSENSITIVE);
        Matcher m = pmidPattern.matcher(str);
        int start = 0;
        Set<String> pmidToSearch = new HashSet<>();
        while (m.find(start)) {
            String pmids = m.group(1).trim();
            for (String pmid : pmids.split(", *(PMID:)? *")) {
                Article doc = articleBo.findArticleByPmid(pmid);
                if (doc == null) {
                    pmidToSearch.add(pmid);
                }
                if (doc != null) {
                    docs.add(doc);
                }
            }
            start = m.end();
        }

        if (!pmidToSearch.isEmpty()) {
            for (Article article : NcbiEUtils.readPubmedArticles(pmidToSearch)) {
                docs.add(article);
                articleBo.save(article);
            }
        }

        Matcher abstractMatch = abstractPattern.matcher(str);
        start = 0;
        String abstracts = "", abContent = "", abLink = "";
        while (abstractMatch.find(start)) {
            abstracts = abstractMatch.group(1).trim();
            for (String abs : abstracts.split(";")) {
                Matcher abItems = abItemPattern.matcher(abs);
                if (abItems.find()) {
                    abContent = abItems.group(1).trim();
                    abLink = abItems.group(2).trim();
                }
                if (!abContent.isEmpty()) {
                    Article doc = articleBo.findArticleByAbstract(abContent);
                    if (doc == null) {
                        doc = new Article();
                        doc.setAbstractContent(abContent);
                        doc.setLink(abLink);
                        articleBo.save(doc);
                    }
                    docs.add(doc);
                }
                abContent = "";
                abLink = "";

            }
            start = abstractMatch.end();

        }

        evidence.addArticles(docs);
    }

    private Date getLastEdit(JSONObject object, String key) {
        return object.has(key + LAST_EDIT_EXTENSION) ? getUpdateTime(object.get(key + LAST_EDIT_EXTENSION)) : null;
    }

    private String getUUID(JSONObject object, String key) {
        return object.has(key + UUID_EXTENSION) ? object.getString(key + UUID_EXTENSION) : "";
    }

    private void addDateToLastEditSetFromObject(Set<Date> set, JSONObject object, String key) throws JSONException {
        if (object.has(key + LAST_EDIT_EXTENSION)) {
            Date tmpDate = getUpdateTime(object.get(key + LAST_EDIT_EXTENSION));
            if (tmpDate != null) {
                set.add(tmpDate);
            }
        }
    }

    private Date getMostRecentDate(Set<Date> dates) {
        if (dates == null || dates.size() == 0)
            return null;
        return Collections.max(dates);
    }
}
