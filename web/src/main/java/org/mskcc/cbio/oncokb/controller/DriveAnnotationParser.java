package org.mskcc.cbio.oncokb.controller;

import com.google.gdata.util.common.base.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mskcc.cbio.oncokb.bo.*;
import org.mskcc.cbio.oncokb.model.*;
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

import static org.mskcc.cbio.oncokb.util.ArticleUtils.getAbstractFromText;
import static org.mskcc.cbio.oncokb.util.ArticleUtils.getPmidsFromText;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * @author jgao
 */
@Controller
public class DriveAnnotationParser {
    OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();

    @RequestMapping(value = "/legacy-api/driveAnnotation", method = POST)
    public @ResponseBody
    synchronized void getEvidence(@RequestParam(value = "germline") Boolean germline, @RequestParam(value = "gene") String gene, @RequestParam(value = "releaseGene", defaultValue = "FALSE") Boolean releaseGene, @RequestParam(value = "vus", required = false) String vus) throws Exception {

        if (gene == null) {
            System.out.println("#No gene info available.");
        } else {
            JSONObject jsonObj = new JSONObject(gene);
            JSONArray jsonArray = null;
            if (vus != null) {
                jsonArray = new JSONArray(vus);
            }
            parseGene(germline, jsonObj, releaseGene, jsonArray);
        }
    }

    private static final String LAST_EDIT_EXTENSION = "_review";
    private static final String UUID_EXTENSION = "_uuid";
    private static final String SOLID_PROPAGATION_KEY = "propagation";
    private static final String LIQUID_PROPAGATION_KEY = "propagationLiquid";
    private static final String FDA_LEVEL_KEY = "fdaLevel";
    private static final String EXCLUDED_RCTS_KEY = "excludedRCTs";

    private static String ALLELE_STATES_BIALLELIC = "biallelic";
    private static String ALLELE_STATES_MONOALLELIC = "monoallelic";
    private static String ALLELE_STATES_MOSAIC = "mosaic";
    private static String ALLELE_STATES_CARRIER = "carrier";
    private static String[] ALLELE_STATE_CHECKS = new String[]{ALLELE_STATES_BIALLELIC, ALLELE_STATES_MONOALLELIC, ALLELE_STATES_MOSAIC, ALLELE_STATES_CARRIER};

    public void parseVUS(Boolean germline, Gene gene, JSONArray vus, Integer nestLevel) throws JSONException {
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
                    }
                    if (evidence.getLastEdit() == null) {
                        System.out.println(spaceStrByNestLevel(nestLevel + 1) + "WARNING: " + mutationStr + " do not have last update.");
                    }
                    evidence.setForGermline(germline);
                    evidenceBo.save(evidence);
                }
                if (i % 10 == 9) System.out.println("\t\tImported " + (i + 1));
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

    /**
     * @param germline    Whether the content is for germline setting. We only have germline and somatic. The default is false.
     * @param geneInfo
     * @param releaseGene
     * @param vus
     * @return
     * @throws Exception
     */
    private Gene parseGene(Boolean germline, JSONObject geneInfo, Boolean releaseGene, JSONArray vus) throws Exception {
        if (germline == null) {
            germline = false;
        }
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
                    List<Evidence> evidences = evidenceBo.findEvidencesByGene(Collections.singleton(gene));

                    Set<Alteration> alterationsToDelete = new HashSet<>();
                    for (Evidence evidence : evidences) {
                        if (evidence.getForGermline().equals(germline)) {
                            evidenceBo.delete(evidence);
                            alterationsToDelete.addAll(evidence.getAlterations());
                        }
                    }
                    AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
                    for (Alteration alteration : alterationsToDelete) {
                        try {
                            alterationBo.delete(alteration);
                        } catch (Exception e) {
                            // when deletion exception happens, we ignore
                            System.out.println(e.getMessage());
                        }
                    }

                    Map<String, Pair<String, Set<Alteration>>> curationMutationsMap = curationMutations(gene, geneInfo);
                    Set<Alteration> allAlterations = new HashSet<>();
                    for (Map.Entry<String, Pair<String, Set<Alteration>>> entry : curationMutationsMap.entrySet()) {
                        allAlterations.addAll(entry.getValue().getSecond());
                    }

                    CacheUtils.updateGene(Collections.singleton(gene.getEntrezGeneId()), false);

                    // summary
                    parseSummary(germline, gene, geneInfo.has("summary") ? geneInfo.getString("summary").trim() : null, getUUID(geneInfo, "summary"), getLastEdit(geneInfo, "summary"), nestLevel + 1);

                    // background
                    parseGeneBackground(germline, gene, geneInfo.has("background") ? geneInfo.getString("background").trim() : null, getUUID(geneInfo, "background"), getLastEdit(geneInfo, "background"), nestLevel + 1);

                    // save genomic indicators
                    String GENOMIC_INDICATORS_KEY = "genomic_indicators";
                    if (geneInfo.has(GENOMIC_INDICATORS_KEY)) {
                        parseGenomicIndicator(geneInfo.getJSONArray(GENOMIC_INDICATORS_KEY), gene, allAlterations);
                    }

                    String GENE_INHERITANCE_MECHANISM_KEY = "inheritanceMechanism";
                    if (geneInfo.has(GENE_INHERITANCE_MECHANISM_KEY)) {
                        parseInheritanceMechanism(gene, geneInfo.getString(GENE_INHERITANCE_MECHANISM_KEY), geneInfo.getString(GENE_INHERITANCE_MECHANISM_KEY+"_uuid"), null);
                    }

                    String GENE_PENETRANCE_KEY = "penetrance";
                    if (geneInfo.has(GENE_PENETRANCE_KEY)) {
                        parsePenetrance(gene, geneInfo.getString(GENE_PENETRANCE_KEY), geneInfo.getString(GENE_PENETRANCE_KEY+"_uuid"), null);
                    }

                    // mutations
                    parseMutations(germline, gene, geneInfo.has("mutations") ? geneInfo.getJSONArray("mutations") : null, nestLevel + 1, curationMutationsMap);

                    // Variants of unknown significance
                    parseVUS(germline, gene, vus, nestLevel + 1);

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

    private Map<String, Pair<String, Set<Alteration>>> curationMutations(Gene gene, JSONObject geneJsonObject) {
        Map<String, Pair<String, Set<Alteration>>> map = new HashMap<>();
        JSONArray mutations = geneJsonObject.has("mutations") ? geneJsonObject.getJSONArray("mutations") : null;
        AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();

        if(mutations != null) {
            for (int i = 0; i < mutations.length(); i++) {
                JSONObject mutation = mutations.getJSONObject(i);
                String mutationStr = mutation.has("name") ? mutation.getString("name").trim() : null;
                String mutationUuid = mutation.has("name_uuid") ? mutation.getString("name_uuid").trim() : null;
                JSONArray alterationsObj = mutation.has("alterations") ? mutation.getJSONArray("alterations") : null;

                if (mutationUuid != null && (mutationStr != null || alterationsObj != null)) {
                    List<Alteration> alterations = new ArrayList<>();
                    if (alterationsObj != null) {
                        for (int j = 0; j < alterationsObj.length(); j++) {
                            JSONObject alteration = alterationsObj.getJSONObject(j);
                            if (alteration.has("alteration") && StringUtils.isNotEmpty(alteration.getString("alteration"))) {
                                List<Alteration> alts = AlterationUtils.parseMutationString(alteration.getString("alteration"), ",");
                                if (alteration.has("proteinChange") && StringUtils.isNotEmpty(alteration.getString("proteinChange"))) {
                                    String proteinChange = alteration.getString("proteinChange");
                                    for (Alteration alt : alts) {
                                        alt.setProteinChange(proteinChange);
                                    }
                                } else {
                                    for (Alteration alt : alts) {
                                        alt.setProteinChange("");
                                    }
                                }
                                alterations.addAll(alts);
                            }
                        }
                    } else {
                        alterations = AlterationUtils.parseMutationString(mutationStr, ",");
                    }

                    Set<Alteration> savedAlts = new HashSet<>();
                    for (Alteration alt : alterations) {
                        Alteration alteration = alterationBo.findAlterationFromDao(gene, AlterationType.MUTATION, ReferenceGenome.GRCh37, alt.getAlteration(), alt.getName());
                        if (alteration == null) {
                            alteration = new Alteration();
                            alteration.setGene(gene);
                            alteration.setAlterationType(AlterationType.MUTATION);
                            alteration.setAlteration(alt.getAlteration());
                            alteration.setProteinChange(alt.getProteinChange());
                            alteration.setName(alt.getName());
                            alteration.setReferenceGenomes(alt.getReferenceGenomes());
                            AlterationUtils.annotateAlteration(alteration, alt.getAlteration());
                            alterationBo.save(alteration);
                        } else if (!alteration.getReferenceGenomes().equals(alt.getReferenceGenomes())) {
                            alteration.setReferenceGenomes(alt.getReferenceGenomes());
                            alterationBo.save(alteration);
                        }
                        savedAlts.add(alteration);
                    }

                    map.put(mutationUuid, new Pair<>(mutationStr, savedAlts));
                }
            }
        }
        return map;
    }

    private Date getUpdateTime(Object obj) throws JSONException {
        if (obj == null) return null;
        JSONObject reviewObj = new JSONObject(obj.toString());
        if (reviewObj.has("updateTime") && StringUtils.isNumeric(reviewObj.get("updateTime").toString())) {
            return new Date(reviewObj.getLong("updateTime"));
        }
        return null;
    }

    private void parseSummary(Boolean germline, Gene gene, String geneSummary, String uuid, Date lastEdit, Integer nestLevel) {
        System.out.println(spaceStrByNestLevel(nestLevel) + "Summary");
        // gene summary
        if (geneSummary != null && !geneSummary.isEmpty()) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.GENE_SUMMARY);
            evidence.setGene(gene);
            evidence.setDescription(geneSummary);
            evidence.setUuid(uuid);
            evidence.setLastEdit(lastEdit);
            evidence.setForGermline(germline);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
            setDocuments(geneSummary, evidence);
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidenceBo.save(evidence);
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Has description");
        }
    }

    private void parseGeneBackground(Boolean germline, Gene gene, String bg, String uuid, Date lastEdit, Integer nestLevel) {
        System.out.println(spaceStrByNestLevel(nestLevel) + "Background");

        if (bg != null && !bg.isEmpty()) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(EvidenceType.GENE_BACKGROUND);
            evidence.setGene(gene);
            evidence.setDescription(bg);
            evidence.setUuid(uuid);
            evidence.setLastEdit(lastEdit);
            evidence.setForGermline(germline);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
            setDocuments(bg, evidence);
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidenceBo.save(evidence);
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Has description");
        }
    }

    private void parseMutations(Boolean germline, Gene gene, JSONArray mutations, Integer nestLevel, Map<String, Pair<String, Set<Alteration>>> curationMutationsMap) throws Exception {
        if (mutations != null) {
            System.out.println(spaceStrByNestLevel(nestLevel) + mutations.length() + " mutations.");
            for (int i = 0; i < mutations.length(); i++) {
                parseMutation(germline, gene, mutations.getJSONObject(i), nestLevel + 1, curationMutationsMap);
            }
        } else {
            System.out.println(spaceStrByNestLevel(nestLevel) + "No mutation.");
        }
    }

    private void saveEffectDescriptionEvidence(Boolean germline, Gene gene, Set<Alteration> alterations, EvidenceType evidenceType, String knownEffect, String description) {
        if (StringUtils.isNotEmpty(knownEffect) || StringUtils.isNotEmpty(description)) {
            Evidence evidence = new Evidence();
            evidence.setEvidenceType(evidenceType);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            if (StringUtils.isNotEmpty(description)) {
                evidence.setDescription(description);
                setDocuments(description, evidence);
            }
            evidence.setKnownEffect(knownEffect);
            evidence.setForGermline(germline);
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            evidenceBo.save(evidence);
        }
    }

    private String getJsonStringVal(JSONObject object, String key) {
        return object.has(key) ? (object.getString(key).trim().isEmpty() ? null : object.getString(key).trim()) : null;
    }

    private void parseInheritanceMechanism(Gene gene, String inheritanceMechanism, String uuid, Set<Alteration> alterations) {
        if (StringUtils.isNotEmpty(inheritanceMechanism)) {
            Evidence evidence = new Evidence();
            evidence.setUuid(uuid);
            evidence.setKnownEffect(inheritanceMechanism);
            evidence.setGene(gene);
            evidence.setForGermline(true);
            if (alterations == null || alterations.isEmpty()) {
                evidence.setEvidenceType(EvidenceType.GENE_INHERITANCE_MECHANISM);
            } else {
                evidence.setAlterations(alterations);
                evidence.setEvidenceType(EvidenceType.VARIANT_INHERITANCE_MECHANISM);
            }
            ApplicationContextSingleton.getEvidenceBo().save(evidence);
        }
    }

    private void parsePenetrance(Gene gene, String penetrance, String uuid, Set<Alteration> alterations) {
        if (StringUtils.isNotEmpty(penetrance)) {
            Evidence evidence = new Evidence();
            evidence.setUuid(uuid);
            evidence.setKnownEffect(penetrance);
            evidence.setGene(gene);
            evidence.setForGermline(true);
            if (alterations == null || alterations.isEmpty()) {
                evidence.setEvidenceType(EvidenceType.GENE_PENETRANCE);
            } else {
                evidence.setAlterations(alterations);
                evidence.setEvidenceType(EvidenceType.VARIANT_PENETRANCE);
            }
            ApplicationContextSingleton.getEvidenceBo().save(evidence);
        }
    }

    private void parseGenomicIndicator(JSONArray genomicIndicators, Gene gene, Set<Alteration> fullAlterations) {
        String ASSO_VARS_KEY = "associationVariants";
        String DESC_KEY = "description";
        String ALLELE_STATES_KEY = "allele_state";

        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

        for (int i = 0; i < genomicIndicators.length(); i++) {
            JSONObject genomicIndicator = genomicIndicators.getJSONObject(i);

            Evidence evidence = new Evidence();
            evidence.setForGermline(true);
            evidence.setEvidenceType(EvidenceType.GENOMIC_INDICATOR);
            Set<Alteration> associatedAlterations = new HashSet<>();
            if (genomicIndicator.has(ASSO_VARS_KEY)) {
                associatedAlterations = getAlterationsFromAssociatedVariants(gene, genomicIndicator.getJSONArray(ASSO_VARS_KEY), fullAlterations);
            }
            evidence.setAlterations(associatedAlterations);
            evidence.setGene(gene);
            evidence.setName(genomicIndicator.getString("name"));
            evidence.setUuid(genomicIndicator.getString("name_uuid"));
            evidence.setDescription(genomicIndicator.has(DESC_KEY) ? genomicIndicator.getString(DESC_KEY) : "");

            if (genomicIndicator.has(ALLELE_STATES_KEY)) {
                JSONObject alleleStatesObject = genomicIndicator.getJSONObject(ALLELE_STATES_KEY);
                evidence.setKnownEffect(Arrays.stream(ALLELE_STATE_CHECKS).filter(alleleState -> alleleStatesObject.has(alleleState) && StringUtils.isNotEmpty(alleleStatesObject.getString(alleleState))).collect(Collectors.joining(",")));
            }
            evidenceBo.save(evidence);
        }
    }

    private Set<Alteration> getAlterationsFromAssociatedVariants(Gene gene, JSONArray associatedVariants, Set<Alteration> alterations) {
        Set<Alteration> mappedAlterations = new HashSet<>();

        for (int i = 0; i < associatedVariants.length(); i++) {
            JSONObject associatedVariant = associatedVariants.getJSONObject(i);
            String name = associatedVariant.getString("name");
            String uuid = associatedVariant.getString("uuid");
            name = AlterationUtils.trimComment(name);
            Optional<Alteration> matchedOptional = Optional.empty();
            for (Alteration alteration : alterations) {
                if (StringUtils.isNotEmpty(uuid) && uuid.equals(alteration.getUuid())) {
                    matchedOptional = Optional.of(alteration);
                    break;
                }
                if (StringUtils.isNotEmpty(name) && name.equalsIgnoreCase(alteration.getAlteration())) {
                    matchedOptional = Optional.of(alteration);
                    break;
                }
            }
            if (matchedOptional.isPresent()) {
                mappedAlterations.add(matchedOptional.get());
            } else {
                Alteration alteration = new Alteration();
                alteration.setGene(gene);
                alteration.setAlterationType(AlterationType.MUTATION);
                alteration.setAlteration(name);
                alteration.setName(name);
                AlterationUtils.annotateAlteration(alteration, name);
                ApplicationContextSingleton.getAlterationBo().save(alteration);
                mappedAlterations.add(alteration);
            }
        }
        return mappedAlterations;
    }

    private void parseMutation(Boolean germline, Gene gene, JSONObject mutationObj, Integer nestLevel, Map<String, Pair<String, Set<Alteration>>> curationMutationsMap) throws Exception {
        String mutationStrUuid = mutationObj.getString("name_uuid").trim();
        String MUTATION_CANCER_RISK_KEY = "mutation_specific_cancer_risk";
        String MUTATION_INHERITANCE_MECHANISM_KEY = "mutation_specific_inheritance_mechanism";
        String MUTATION_PENETRANCE_KEY = "mutation_specific_penetrance";

        if (StringUtils.isNotEmpty(mutationStrUuid)) {
            Pair<String, Set<Alteration>> mutationPair = curationMutationsMap.get(mutationStrUuid);
            String mutationStr = mutationPair.getFirst();
            Set<Alteration> alterations = mutationPair.getSecond();
            System.out.println(spaceStrByNestLevel(nestLevel) + "Mutation: " + mutationStr);

            JSONObject mutationEffect = mutationObj.has("mutation_effect") ? mutationObj.getJSONObject("mutation_effect") : null;

            if (mutationEffect != null) {
                Oncogenicity oncogenic = getOncogenicity(mutationEffect);
                String oncogenic_uuid = getUUID(mutationEffect, "oncogenic");
                Date oncogenic_lastEdit = getLastEdit(mutationEffect, "oncogenic");

                Pathogenicity pathogenic = getPathogenicity(mutationEffect);
                String pathogenic_uuid = getUUID(mutationEffect, "pathogenic");
                Date pathogenic_lastEdit = getLastEdit(mutationEffect, "pathogenic");

                alterations.forEach(alteration -> {
                    setOncogenic(gene, alteration, oncogenic, oncogenic_uuid, oncogenic_lastEdit);
                    setPathogenic(gene, alteration, pathogenic, pathogenic_uuid, pathogenic_lastEdit);
                });

                Set<Date> lastEditDatesEffect = new HashSet<>();

                String effect = mutationEffect.has("effect") ? mutationEffect.getString("effect") : null;
                addDateToLastEditSetFromObject(lastEditDatesEffect, mutationEffect, "effect");
                String effect_uuid = getUUID(mutationEffect, "effect");

                // mutation effect
                String effectDesc = mutationEffect.has("description") ? (mutationEffect.getString("description").trim().isEmpty() ? null : mutationEffect.getString("description").trim()) : null;
                addDateToLastEditSetFromObject(lastEditDatesEffect, mutationEffect, "description");

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

                    evidence.setKnownEffect(effect);
                    evidence.setUuid(effect_uuid);

                    Date effect_lastEdit = getMostRecentDate(lastEditDatesEffect);
                    evidence.setLastEdit(effect_lastEdit);

                    evidence.setForGermline(germline);
                    EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
                    evidenceBo.save(evidence);
                }
            }

            // add mutation summary
            String mutationSummary = mutationObj.has("summary") ? mutationObj.getString("summary") : "";
            if (StringUtils.isNotEmpty(mutationSummary)) {
                Evidence evidence = new Evidence();
                evidence.setEvidenceType(EvidenceType.MUTATION_SUMMARY);
                evidence.setAlterations(alterations);
                evidence.setGene(gene);
                evidence.setDescription(mutationSummary);
                setDocuments(mutationSummary, evidence);
                evidence.setUuid(getUUID(mutationObj, "summary"));
                evidence.setLastEdit(getLastEdit(mutationObj, "summary"));
                EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
                evidenceBo.save(evidence);
            }

            // Save germline variant penetrance
            if (mutationObj.has(MUTATION_PENETRANCE_KEY)) {
                JSONObject mutationPenetrance = mutationObj.getJSONObject(MUTATION_PENETRANCE_KEY);
                saveEffectDescriptionEvidence(Boolean.TRUE, gene, alterations, EvidenceType.VARIANT_PENETRANCE, getJsonStringVal(mutationPenetrance, "penetrance"), getJsonStringVal(mutationPenetrance, "description"));
            }

            // Save germline mechanism of inheritance
            if (mutationObj.has(MUTATION_INHERITANCE_MECHANISM_KEY)) {
                JSONObject mutationInheritanceMechanism = mutationObj.getJSONObject(MUTATION_INHERITANCE_MECHANISM_KEY);
                saveEffectDescriptionEvidence(Boolean.TRUE, gene, alterations, EvidenceType.VARIANT_INHERITANCE_MECHANISM, getJsonStringVal(mutationInheritanceMechanism, "inheritanceMechanism"), getJsonStringVal(mutationInheritanceMechanism, "description"));
            }

            // Save germline cancer risk
            if (mutationObj.has(MUTATION_CANCER_RISK_KEY)) {
                JSONObject cancerRisk = mutationObj.getJSONObject(MUTATION_CANCER_RISK_KEY);
                for (String cancerRiskKey : ALLELE_STATE_CHECKS) {
                    if (StringUtils.isNotEmpty(getJsonStringVal(cancerRisk, cancerRiskKey))) {
                        saveEffectDescriptionEvidence(Boolean.TRUE, gene, alterations, EvidenceType.VARIANT_CANCER_RISK, cancerRiskKey, getJsonStringVal(cancerRisk, cancerRiskKey));
                    }
                }
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

                    List<TumorType> relevantCancerTypes = getRelevantCancerTypesIfExistsFromJsonObject(cancers.getJSONObject(i), tumorTypes, excludedCancerTypes, null);

                    parseCancer(germline, gene, alterations, cancers.getJSONObject(i), tumorTypes, excludedCancerTypes, relevantCancerTypes, nestLevel + 1);
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
            } else if (mainType != null) {
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

    protected Pathogenicity getPathogenicityByString(String pathogenicStr) {
        Pathogenicity pathogenic = null;
        if (pathogenicStr != null) {
            pathogenicStr = pathogenicStr.toLowerCase();
            switch (pathogenicStr) {
                case "yes":
                    pathogenic = Pathogenicity.YES;
                    break;
                case "pathogenic":
                    pathogenic = Pathogenicity.YES;
                    break;
                case "likely":
                    pathogenic = Pathogenicity.LIKELY;
                    break;
                case "likely pathogenic":
                    pathogenic = Pathogenicity.LIKELY;
                    break;
                case "unknown":
                    pathogenic = Pathogenicity.UNKNOWN;
                    break;
                case "likely benign":
                    pathogenic = Pathogenicity.LIKELY_BENIGN;
                    break;
                case "benign":
                    pathogenic = Pathogenicity.BENIGN;
                    break;
                default:
                    break;
            }
        }
        return pathogenic;
    }

    private Oncogenicity getOncogenicity(JSONObject mutationEffect) throws JSONException {
        Oncogenicity oncogenic = null;
        String ONCOGENICITY_KEY = "oncogenic";
        if (mutationEffect.has(ONCOGENICITY_KEY) && !mutationEffect.getString(ONCOGENICITY_KEY).isEmpty()) {
            oncogenic = getOncogenicityByString(mutationEffect.getString(ONCOGENICITY_KEY));
        }
        return oncogenic;
    }

    private Pathogenicity getPathogenicity(JSONObject mutationEffect) throws JSONException {
        Pathogenicity pathogenic = null;
        String PATHOGENICITY_KEY = "pathogenic";
        if (mutationEffect.has(PATHOGENICITY_KEY) && !mutationEffect.getString(PATHOGENICITY_KEY).isEmpty()) {
            pathogenic = getPathogenicityByString(mutationEffect.getString(PATHOGENICITY_KEY));
        }
        return pathogenic;
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
                evidence.setForGermline(false);
                evidenceBo.save(evidence);
            } else if (Oncogenicity.compare(oncogenic, Oncogenicity.getByEvidence(evidences.get(0))) > 0) {
                evidences.get(0).setKnownEffect(oncogenic.getOncogenic());
                evidences.get(0).setLastEdit(lastEdit);
                evidenceBo.update(evidences.get(0));
            }
        }
    }

    private void setPathogenic(Gene gene, Alteration alteration, Pathogenicity pathogenic, String uuid, Date lastEdit) {
        if (alteration != null && gene != null && pathogenic != null) {
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            List<Evidence> evidences = evidenceBo.findEvidencesByAlteration(Collections.singleton(alteration), Collections.singleton(EvidenceType.PATHOGENIC));
            if (evidences.isEmpty()) {
                Evidence evidence = new Evidence();
                evidence.setGene(gene);
                evidence.setAlterations(Collections.singleton(alteration));
                evidence.setEvidenceType(EvidenceType.PATHOGENIC);
                evidence.setKnownEffect(pathogenic.getPathogenic());
                evidence.setUuid(uuid);
                evidence.setLastEdit(lastEdit);
                evidence.setForGermline(true);
                evidenceBo.save(evidence);
            } else if (Pathogenicity.compare(pathogenic, Pathogenicity.getByEvidence(evidences.get(0))) > 0) {
                evidences.get(0).setKnownEffect(pathogenic.getPathogenic());
                evidences.get(0).setLastEdit(lastEdit);
                evidenceBo.update(evidences.get(0));
            }
        }
    }

    private void saveDxPxSummaries(Boolean germline, JSONObject cancerObj, String summaryKey, Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, EvidenceType evidenceType, Integer nestLevel, LevelOfEvidence level) {
        List<TumorType> rcts = new ArrayList<>(relevantCancerTypes);
        if ((rcts == null || rcts.size() == 0) && LevelOfEvidence.LEVEL_Dx1.equals(level)) {
            rcts.addAll(TumorTypeUtils.getDxOneRelevantCancerTypes(new HashSet<>(tumorTypes)));
        }
        saveTumorLevelSummaries(germline, cancerObj, summaryKey, gene, alterations, tumorTypes, excludedCancerTypes, rcts, evidenceType, nestLevel);
    }

    private void saveTumorLevelSummaries(Boolean germline, JSONObject cancerObj, String summaryKey, Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, EvidenceType evidenceType, Integer nestLevel) {
        if (cancerObj.has(summaryKey) && !cancerObj.getString(summaryKey).isEmpty()) {
            EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
            System.out.println(spaceStrByNestLevel(nestLevel + 1) + " " + summaryKey);
            Date lastEdit = getLastEdit(cancerObj, summaryKey);
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
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
            if (!tumorTypes.isEmpty()) {
                evidence.setCancerTypes(new HashSet<>(tumorTypes));
            }
            setDocuments(cancerObj.getString(summaryKey), evidence);
            System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Has description.");
            evidence.setForGermline(germline);
            evidenceBo.save(evidence);
        }
    }

    private void parseCancer(Boolean germline, Gene gene, Set<Alteration> alterations, JSONObject cancerObj, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, Integer nestLevel) throws Exception {
        if (tumorTypes.isEmpty()) {
            return;
        }

        System.out.println(spaceStrByNestLevel(nestLevel) + "Tumor types: " + tumorTypes.stream().map(TumorTypeUtils::getTumorTypeName).collect(Collectors.joining(", ")));

        // cancer type summary
        saveTumorLevelSummaries(germline, cancerObj, "summary", gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes, EvidenceType.TUMOR_TYPE_SUMMARY, nestLevel);

        // prognostic implications
        Evidence prognosticEvidence = parseImplication(germline, gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes, cancerObj.has("prognostic") ? cancerObj.getJSONObject("prognostic") : null, getUUID(cancerObj, "prognostic"), EvidenceType.PROGNOSTIC_IMPLICATION, nestLevel + 1);

        // diagnostic implications
        Evidence diagnosticEvidence = parseImplication(germline, gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes, cancerObj.has("diagnostic") ? cancerObj.getJSONObject("diagnostic") : null, getUUID(cancerObj, "diagnostic"), EvidenceType.DIAGNOSTIC_IMPLICATION, nestLevel + 1);

        // diagnostic summary
        List<TumorType> diagnosticRCT = getRelevantCancerTypesIfExistsFromJsonObject(cancerObj.getJSONObject("diagnostic"), tumorTypes, excludedCancerTypes, diagnosticEvidence == null ? null : diagnosticEvidence.getLevelOfEvidence());
        saveDxPxSummaries(
            germline,
            cancerObj,
            "diagnosticSummary",
            gene,
            alterations,
            tumorTypes,
            excludedCancerTypes,
            cancerObj.has("diagnostic") ? diagnosticRCT : relevantCancerTypes,
            EvidenceType.DIAGNOSTIC_SUMMARY,
            nestLevel,
            diagnosticEvidence == null ? null : diagnosticEvidence.getLevelOfEvidence()
        );

        // prognostic summary
        List<TumorType> prognosticRCT = getRelevantCancerTypesIfExistsFromJsonObject(cancerObj.getJSONObject("prognostic"), tumorTypes, excludedCancerTypes, prognosticEvidence == null ? null : prognosticEvidence.getLevelOfEvidence());
        saveDxPxSummaries(
            germline,
            cancerObj,
            "prognosticSummary",
            gene,
            alterations,
            tumorTypes,
            excludedCancerTypes,
            cancerObj.has("prognostic") ? prognosticRCT : relevantCancerTypes,
            EvidenceType.PROGNOSTIC_SUMMARY,
            nestLevel,
            prognosticEvidence == null ? null : prognosticEvidence.getLevelOfEvidence()
        );

        JSONArray implications = cancerObj.getJSONArray("TIs");

        for (int i = 0; i < implications.length(); i++) {
            JSONObject implication = implications.getJSONObject(i);
            if ((implication.has("description") && !implication.getString("description").trim().isEmpty()) || (implication.has("treatments") && implication.getJSONArray("treatments").length() > 0)) {
                parseTherapeuticImplications(germline, gene, alterations, tumorTypes, excludedCancerTypes, relevantCancerTypes, implication, nestLevel + 1);
            }
        }
    }

    private void parseTherapeuticImplications(Boolean germline, Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, JSONObject implicationObj,
                                              Integer nestLevel) throws Exception {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

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
            addDateToLastEditSetFromObject(lastEditDates, drugObj, "name");

            ImmutablePair<EvidenceType, String> evidenceTypeAndKnownEffect = getEvidenceTypeAndKnownEffectFromDrugObj(drugObj);
            EvidenceType evidenceType = evidenceTypeAndKnownEffect.getLeft();
            String knownEffect = evidenceTypeAndKnownEffect.getRight();
            if (evidenceType == null) {
                System.err.println(spaceStrByNestLevel(nestLevel + 1) + "Could not get evidence type" + drugObj.toString());
                continue;
            }
            if (knownEffect == null) {
                System.err.println(spaceStrByNestLevel(nestLevel + 1) + "Could not get known effect" + drugObj.toString());
            }

            Evidence evidence = new Evidence();
            evidence.setEvidenceType(evidenceType);
            evidence.setAlterations(alterations);
            evidence.setGene(gene);
            evidence.setCancerTypes(new HashSet<>(tumorTypes));
            evidence.setKnownEffect(knownEffect);
            evidence.setUuid(getUUID(drugObj, "name"));

            // approved indications
            Set<String> approvedIndications = new HashSet<>();
            if (drugObj.has("indication") && !drugObj.getString("indication").trim().isEmpty()) {
                approvedIndications = new HashSet<>(Arrays.asList(drugObj.getString("indication").split(";")));
                addDateToLastEditSetFromObject(lastEditDates, drugObj, "indication");
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
            } else {
                String level = drugObj.getString("level").trim();
                addDateToLastEditSetFromObject(lastEditDates, drugObj, "level");

                LevelOfEvidence levelOfEvidence = LevelOfEvidence.getByLevel(level.toUpperCase());
                if (levelOfEvidence == null) {
                    System.err.println(spaceStrByNestLevel(nestLevel + 2) + "Error: wrong level of evidence: " + level);
                    continue;
                } else if (LevelUtils.getAllowedCurationLevels().contains(levelOfEvidence)) {
                    System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Level: " + levelOfEvidence.getLevel());
                } else {
                    System.err.println(spaceStrByNestLevel(nestLevel + 2) + "Level not allowed: " + levelOfEvidence.getLevel());
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
                        System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Manual solid propagation level: " + evidence.getSolidPropagationLevel());
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
                        System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Manual liquid propagation level: " + evidence.getLiquidPropagationLevel());
                    }
                } else {
                    evidence.setLiquidPropagationLevel(LevelUtils.getDefaultPropagationLevelByTumorForm(evidence, TumorForm.LIQUID));
                }
            }

            if (drugObj.has("description") && !drugObj.getString("description").trim().isEmpty()) {
                String desc = drugObj.getString("description").trim();
                addDateToLastEditSetFromObject(lastEditDates, drugObj, "description");
                evidence.setDescription(desc);
                System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Has description.");
                setDocuments(desc, evidence);
            }

            Date lastEdit = getMostRecentDate(lastEditDates);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 2) + "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }
            evidence.setLastEdit(lastEdit);

            if (excludedCancerTypes != null) {
                evidence.setExcludedCancerTypes(new HashSet<>(excludedCancerTypes));
            }

            List<TumorType> drugRCT = getRelevantCancerTypesIfExistsFromJsonObject(drugObj, tumorTypes, excludedCancerTypes, evidence.getLevelOfEvidence());
            if (drugRCT.size() > 0) {
                evidence.setRelevantCancerTypes(new HashSet<>(drugRCT));
            } else if (relevantCancerTypes != null) {
                evidence.setRelevantCancerTypes(new HashSet<>(relevantCancerTypes));
            }

            evidence.setForGermline(germline);
            evidenceBo.save(evidence);
        }
    }

    private Evidence parseImplication(Boolean germline, Gene gene, Set<Alteration> alterations, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, List<TumorType> relevantCancerTypes, JSONObject implication, String uuid, EvidenceType evidenceType, Integer nestLevel) throws Exception {
        if (evidenceType != null && implication != null && ((implication.has("description") && !implication.getString("description").trim().isEmpty()) || (implication.has("level") && !implication.getString("level").trim().isEmpty()))) {
            System.out.println(spaceStrByNestLevel(nestLevel) + evidenceType.name() + ":");
            Set<Date> lastEditDates = new HashSet<>();
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

            List<TumorType> implicationRCT = getRelevantCancerTypesIfExistsFromJsonObject(implication, tumorTypes, excludedCancerTypes, evidence.getLevelOfEvidence());
            if (implicationRCT.size() > 0) {
                evidence.setRelevantCancerTypes(new HashSet<>(implicationRCT));
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
                setDocuments(desc, evidence);
            }

            Date lastEdit = getMostRecentDate(lastEditDates);
            evidence.setLastEdit(lastEdit);
            if (lastEdit != null) {
                System.out.println(spaceStrByNestLevel(nestLevel + 1) + "Last update on: " + MainUtils.getTimeByDate(lastEdit));
            }

            evidence.setForGermline(germline);
            evidenceBo.save(evidence);
            return evidence;
        }
        return null;
    }

    private String spaceStrByNestLevel(Integer nestLevel) {
        if (nestLevel == null || nestLevel < 1) nestLevel = 1;
        return StringUtils.repeat("    ", nestLevel - 1);
    }

    private void setDocuments(String str, Evidence evidence) {
        if (str == null) return;
        Set<Article> docs = new HashSet<>();
        ArticleBo articleBo = ApplicationContextSingleton.getArticleBo();

        Set<String> pmidToSearch = new HashSet<>();
        getPmidsFromText(evidence.getDescription()).forEach(pmid -> {
            Article doc = articleBo.findArticleByPmid(pmid);
            if (doc != null) {
                docs.add(doc);
            } else {
                pmidToSearch.add(pmid);
            }
        });

        if (!pmidToSearch.isEmpty()) {
            for (Article article : NcbiEUtils.readPubmedArticles(pmidToSearch)) {
                articleBo.save(article);
                docs.add(article);
            }
        }

        getAbstractFromText(evidence.getDescription()).stream().forEach(article -> {
            Article dbArticle = articleBo.findArticleByAbstract(article.getAbstractContent());
            if (dbArticle == null) {
                articleBo.save(article);
                docs.add(article);
            } else {
                docs.add(dbArticle);
            }
        });

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
        if (dates == null || dates.size() == 0) return null;
        return Collections.max(dates);
    }

    private ImmutablePair<EvidenceType, String> getEvidenceTypeAndKnownEffectFromDrugObj(JSONObject drugObj) {
        ImmutablePair<EvidenceType, String> emptyPair = new ImmutablePair<EvidenceType, String>(null, null);
        if (!drugObj.has("level") || drugObj.getString("level").trim().isEmpty()) {
            return emptyPair;
        }
        String level = drugObj.getString("level").trim();
        LevelOfEvidence levelOfEvidence = LevelOfEvidence.getByLevel(level.toUpperCase());

        EvidenceType evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY;
        String type = "";
        if (LevelOfEvidence.LEVEL_1.equals(levelOfEvidence) || LevelOfEvidence.LEVEL_2.equals(levelOfEvidence)) {
            evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY;
            type = "Sensitive";
        } else if (LevelOfEvidence.LEVEL_R1.equals(levelOfEvidence)) {
            evidenceType = EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE;
            type = "Resistant";
        } else if (LevelOfEvidence.LEVEL_3A.equals(levelOfEvidence) || LevelOfEvidence.LEVEL_4.equals(levelOfEvidence)) {
            evidenceType = EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY;
            type = "Sensitive";
        } else if (LevelOfEvidence.LEVEL_R2.equals(levelOfEvidence)) {
            evidenceType = EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE;
            type = "Resistant";
        } else {
            return emptyPair;
        }

        return new ImmutablePair<EvidenceType, String>(evidenceType, type);
    }

    private List<TumorType> getRelevantCancerTypes(List<TumorType> tumorTypes, List<TumorType> excludedTumorTypes, LevelOfEvidence level, List<TumorType> excludedRelevantCancerTypes) {
        RelevantTumorTypeDirection direction = level != null && LevelOfEvidence.LEVEL_Dx1.equals(level) ? RelevantTumorTypeDirection.UPWARD : RelevantTumorTypeDirection.DOWNWARD;

        Set<TumorType> queriedTumorTypes = tumorTypes.stream().map(tt -> {
                return TumorTypeUtils.findRelevantTumorTypes(TumorTypeUtils.getTumorTypeName(tt), StringUtils.isEmpty(tt.getSubtype()), direction, false);
            })
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        Set<TumorType> queriedExcludedTumorTypes = excludedTumorTypes.stream().map(ett -> {
                return TumorTypeUtils.findRelevantTumorTypes(TumorTypeUtils.getTumorTypeName(ett), StringUtils.isEmpty(ett.getSubtype()), direction, false);
            })
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        queriedTumorTypes.removeAll(queriedExcludedTumorTypes);
        queriedTumorTypes.removeAll(excludedRelevantCancerTypes);


        return new ArrayList<>(queriedTumorTypes);
    }

    private List<TumorType> getRelevantCancerTypesIfExistsFromJsonObject(JSONObject jsonObject, List<TumorType> tumorTypes, List<TumorType> excludedCancerTypes, LevelOfEvidence level) throws JSONException, Exception {
        List<TumorType> relevantCancerTypes = new ArrayList<>();
        if (jsonObject.has(EXCLUDED_RCTS_KEY)) {
            List<TumorType> excludedRCT = getTumorTypes(jsonObject.getJSONArray(EXCLUDED_RCTS_KEY));
            relevantCancerTypes = getRelevantCancerTypes(tumorTypes, excludedCancerTypes, level, excludedRCT);
        }
        return relevantCancerTypes;
    }
}
