package org.mskcc.cbio.oncokb.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.mysql.jdbc.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mskcc.cbio.oncokb.bo.ArticleBo;
import org.mskcc.cbio.oncokb.bo.DrugBo;
import org.mskcc.cbio.oncokb.model.*;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.mskcc.cbio.oncokb.model.BiologicalVariant;

import static org.mskcc.cbio.oncokb.util.FusionUtils.FUSION_ALTERNATIVE_SEPARATOR;

public class ValidationUtils {

    public static JSONArray getMissingTreatmentInfoData() {
        final String NO_LEVEL = "No level is specified";
        final String NO_REFERENCE = "No reference is specified";
        final String NO_TREATMENT = "No treatment is specified";
        JSONArray data = new JSONArray();
        for (Gene gene : CacheUtils.getAllGenes()) {
            Set<Evidence> evidences = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, EvidenceTypeUtils.getTreatmentEvidenceTypes());
            for (Evidence evidence : evidences) {
                String hugoSymbol = gene.getHugoSymbol();
                String alterationsName = getEvidenceAlterationsName(evidence);
                String tumorTypesName = TumorTypeUtils.getEvidenceTumorTypesName(evidence);
                if (evidence.getTreatments().isEmpty()) {
                    data.put(getErrorMessage(getTarget(hugoSymbol, alterationsName, tumorTypesName), NO_TREATMENT));
                } else {
                    String treatmentName = TreatmentUtils.getTreatmentName(evidence.getTreatments());
                    if (evidence.getLevelOfEvidence() == null) {
                        data.put(getErrorMessage(getTarget(hugoSymbol, alterationsName, tumorTypesName, treatmentName), NO_LEVEL));
                    }
                    if (evidence.getArticles().isEmpty()) {
                        data.put(getErrorMessage(getTarget(hugoSymbol, alterationsName, tumorTypesName, treatmentName), NO_REFERENCE));
                    }
                }
            }
        }
        return data;
    }

    public static JSONArray getEmptyBiologicalVariants() {
        final String NO_ONCOGENECITY = "No oncogenicity is specified";
        final String NO_MUTATION_EFFECT = "No mutation effect is specified";
        final String NO_MUTATION_EFFECT_REFERENCE = "Mutation effect does not have any reference (pmids, abstracts)";
        JSONArray data = new JSONArray();
        for (Gene gene : CacheUtils.getAllGenes()) {
            Set<BiologicalVariant> variants = MainUtils.getBiologicalVariants(gene, false);
            for (BiologicalVariant variant : variants) {
                if (StringUtils.isNullOrEmpty(variant.getOncogenic())) {
                    data.put(getErrorMessage(getTargetByAlteration(variant.getVariant()), NO_ONCOGENECITY));
                }
                if (StringUtils.isNullOrEmpty(variant.getMutationEffect())) {
                    data.put(getErrorMessage(getTargetByAlteration(variant.getVariant()), NO_MUTATION_EFFECT));
                }
                if (variant.getMutationEffectPmids().isEmpty() && variant.getMutationEffectAbstracts().isEmpty()) {
                    data.put(getErrorMessage(getTargetByAlteration(variant.getVariant()), NO_MUTATION_EFFECT_REFERENCE));
                }
            }
        }
        return data;
    }

    public static JSONArray checkGeneSummaryBackground() {
        final String NO_SUMMARY = "No gene summary is specified";
        final String MULTIPLE_SUMMARY = "Multiple gene summary exist";
        final String NO_BACKGROUND = "No gene background is specified";
        final String MULTIPLE_BACKGROUND = "Multiple gene background exist";
        JSONArray data = new JSONArray();

        for (Gene gene : CacheUtils.getAllGenes()) {
            if (gene.getEntrezGeneId() < 1) {
                continue;
            }
            // Summary
            Set<Evidence> evidences = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, Collections.singleton(EvidenceType.GENE_SUMMARY));
            if (evidences.size() > 1) {
                data.put(getErrorMessage(getTarget(gene.getHugoSymbol()), MULTIPLE_SUMMARY));
            } else if (evidences.size() == 0) {
                data.put(getErrorMessage(getTarget(gene.getHugoSymbol()), NO_SUMMARY));
            }

            // Background
            evidences = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, Collections.singleton(EvidenceType.GENE_BACKGROUND));
            if (evidences.size() > 1) {
                data.put(getErrorMessage(getTarget(gene.getHugoSymbol()), MULTIPLE_BACKGROUND));
            } else if (evidences.size() == 0) {
                data.put(getErrorMessage(getTarget(gene.getHugoSymbol()), NO_BACKGROUND));
            }
        }
        return data;
    }

    public static JSONArray checkEvidenceDescriptionReferenceFormat() {
        final String HTML_RESERVED_CHARS_EXIST = "HTML reserved characters exist";
        final String HTML_TAGS_EXIST = "HTML tag exists";
        final String CANNOT_FIND_PMIDS = "Following PMID(s) cannot be identified: ";

        Pattern reservedCharsRegex = Pattern.compile("&[\\w]{4};");
        Pattern htmlFragmentRegex = Pattern.compile("<\\s*a[^>]*>");

        JSONArray data = new JSONArray();

        ArticleBo articleBo = ApplicationContextSingleton.getArticleBo();
        List<Article> allArticles = articleBo.findAll();

        for (Evidence evidence : CacheUtils.getAllEvidences()) {
            if (evidence.getDescription() != null) {
                Matcher matcher = reservedCharsRegex.matcher(evidence.getDescription());
                if (matcher.find()) {
                    data.put(getErrorMessage(getTarget(evidence.getGene().getHugoSymbol(), evidence.getEvidenceType(), getEvidenceAlterationsName(evidence), TumorTypeUtils.getEvidenceTumorTypesName(evidence), TreatmentUtils.getTreatmentName(evidence.getTreatments())), HTML_RESERVED_CHARS_EXIST));
                }

                matcher = htmlFragmentRegex.matcher(evidence.getDescription());
                if (matcher.find()) {
                    data.put(getErrorMessage(getTarget(evidence.getGene().getHugoSymbol(), evidence.getEvidenceType(), getEvidenceAlterationsName(evidence), TumorTypeUtils.getEvidenceTumorTypesName(evidence), TreatmentUtils.getTreatmentName(evidence.getTreatments())), HTML_TAGS_EXIST));
                }

                Set<String> incorrectPmids = findIncorrectPmids(evidence, allArticles);
                if (incorrectPmids.size() > 0) {
                    data.put(getErrorMessage(getTarget(evidence.getGene().getHugoSymbol(), evidence.getEvidenceType(), getEvidenceAlterationsName(evidence), TumorTypeUtils.getEvidenceTumorTypesName(evidence), TreatmentUtils.getTreatmentName(evidence.getTreatments())), CANNOT_FIND_PMIDS + String.join(", ", incorrectPmids)));
                }
            }
        }
        return data;
    }

    public static JSONArray checkEvidenceDescriptionHasOutdatedInfo() {
        // the goal is to find whether the description includes outdated info
        //
        // Check one: drugs that in the production but no longer included in the latest data
        JSONArray data = new JSONArray();

        DrugBo drugBo = ApplicationContextSingleton.getDrugBo();
        Set<Drug> drugsWithEviAssoc = new HashSet<>();
        CacheUtils.getAllEvidences().forEach(evidence -> evidence.getTreatments().forEach(treatment -> drugsWithEviAssoc.addAll(treatment.getDrugs())));

        List<Drug> allDrugs = drugBo.findAll();
        Set<Drug> drugsWithoutEviAssoc = allDrugs.stream().filter(drug -> !drugsWithEviAssoc.contains(drug)).collect(Collectors.toSet());

        Set<String> drugNameWithSynonyms = new HashSet<>();
        for (Drug drug : drugsWithoutEviAssoc) {
            drugNameWithSynonyms.addAll(drug.getSynonyms());
            drugNameWithSynonyms.add(drug.getDrugName());
        }
        Set<EvidenceType> evidenceTypesToCheck = new HashSet<>();
        evidenceTypesToCheck.add(EvidenceType.MUTATION_EFFECT);
        evidenceTypesToCheck.add(EvidenceType.TUMOR_TYPE_SUMMARY);
        evidenceTypesToCheck.addAll(EvidenceTypeUtils.getTreatmentEvidenceTypes());

        Set<String> finalDrugNameWithSynonyms = drugNameWithSynonyms.stream().map(String::toLowerCase).collect(Collectors.toSet());
        CacheUtils.getAllEvidences().stream().filter(evidence -> evidenceTypesToCheck.contains(evidence.getEvidenceType())).forEach(evidence -> {
            if (!StringUtils.isNullOrEmpty(evidence.getDescription())) {
                finalDrugNameWithSynonyms.forEach(name -> {
                    if (evidence.getDescription().toLowerCase().contains(name)) {
                        data.put(getErrorMessage(getTarget(evidence.getGene().getHugoSymbol(), evidence.getEvidenceType(), getEvidenceAlterationsName(evidence), TumorTypeUtils.getEvidenceTumorTypesName(evidence), TreatmentUtils.getTreatmentName(evidence.getTreatments())), "The drug " + name + " does not have any association in the system, but still in the description"));
                    }
                });
            }
        });
        return data;
    }

    public static JSONArray validateHugoSymbols(Set<Gene> curatedGenesToCheck, Set<CancerGene> cancerGenesToCheck, List<Gene> searchedGenes) {
        JSONArray data = new JSONArray();
        // Check the curated genes
        curatedGenesToCheck.stream().forEach(gene -> {
            Optional<Gene> myGeneOptional = searchedGenes.stream().filter(geneDatum -> geneDatum.getEntrezGeneId().equals(gene.getEntrezGeneId())).findFirst();
            if (myGeneOptional.isPresent()) {
                if (!myGeneOptional.get().getHugoSymbol().equals(gene.getHugoSymbol())) {
                    data.put(getErrorMessage(getTarget(gene.getHugoSymbol()), "Gene symbol outdated, new: " + myGeneOptional.get().getHugoSymbol()));
                }
            } else {
                data.put(getErrorMessage(getTarget(gene.getHugoSymbol()), "We cannot find this gene in MyGene.info"));
            }
        });

        // Check the cancer genes
        cancerGenesToCheck.forEach(cancerGene -> {
            Optional<Gene> myGeneOptional = searchedGenes.stream().filter(geneDatum -> geneDatum.getEntrezGeneId().equals(cancerGene.getEntrezGeneId())).findFirst();
            if (myGeneOptional.isPresent()) {
                if (!myGeneOptional.get().getHugoSymbol().equals(cancerGene.getHugoSymbol())) {
                    data.put(getErrorMessage(getTarget(cancerGene.getHugoSymbol()), "Cancer gene symbol outdated, new: " + myGeneOptional.get().getHugoSymbol()));
                }
            } else {
                data.put(getErrorMessage(getTarget(cancerGene.getHugoSymbol()), "We cannot find this gene in MyGene.info"));
            }
        });
        return data;
    }

    public static JSONArray checkAlterationNameFormat() {
        final String ALTERATION_NAME_IS_EMPTY = "The alteration does not have a name";
        final String UNSUPPORTED_ALTERATION_NAME = "The alteration name is not supported";
        final String INDEL_IS_NOT_SUPPORTED = "Indel is not supported";
        final String EXON_RANGE_NEEDED = "Exon does not have a range defined";
        final String FUSION_NAME_IS_INCORRECT = "Fusion name is incorrect";
        final String VARIANT_CONSEQUENCE_IS_NOT_AVAILABLE = "The alteration does not have variant consequence";
        final String VARIANT_CONSEQUENCE_ANY_IS_INAPPROPRIATE = "The consequence any is assigned to incorrect alteration";

        JSONArray data = new JSONArray();

        Pattern unsupportedAlterationNameRegex = Pattern.compile("[^\\w\\s\\*-\\{\\};]");
        for (Alteration alteration : AlterationUtils.getAllAlterations()) {
            if (StringUtils.isNullOrEmpty(alteration.getAlteration())) {
                data.put(getErrorMessage(getTarget(alteration.getGene().getHugoSymbol()), ALTERATION_NAME_IS_EMPTY));
            } else {
                Matcher matcher = unsupportedAlterationNameRegex.matcher(alteration.getAlteration());
                if (matcher.find() && !specialAlterationNames().contains(alteration.getName())) {
                    data.put(getErrorMessage(getTarget(alteration.getGene().getHugoSymbol(), getAlterationName(alteration)), UNSUPPORTED_ALTERATION_NAME));
                } else {
                    if (alteration.getAlteration().toLowerCase().contains("indel")) {
                        data.put(getErrorMessage(getTarget(alteration.getGene().getHugoSymbol(), getAlterationName(alteration)), INDEL_IS_NOT_SUPPORTED));
                    }
                    if (alteration.getName().toLowerCase().contains("exon") && (alteration.getProteinStart() == null || alteration.getProteinEnd() == null || alteration.getProteinStart().equals(alteration.getProteinEnd()) || alteration.getProteinStart().equals(-1))) {
                        data.put(getErrorMessage(getTarget(alteration.getGene().getHugoSymbol(), getAlterationName(alteration)), EXON_RANGE_NEEDED));
                    }
                    if (alteration.getAlteration().contains(FUSION_ALTERNATIVE_SEPARATOR) && !alteration.getAlteration().toLowerCase().contains("fusion") && !specialAlterationNames().contains(alteration.getName())) {
                        data.put(getErrorMessage(getTarget(alteration.getGene().getHugoSymbol(), getAlterationName(alteration)), FUSION_NAME_IS_INCORRECT));
                    }
                    if (alteration.getConsequence() == null) {
                        data.put(getErrorMessage(getTarget(alteration.getGene().getHugoSymbol(), getAlterationName(alteration)), VARIANT_CONSEQUENCE_IS_NOT_AVAILABLE));
                    } else {
                        if (alteration.getConsequence().equals(VariantConsequenceUtils.findVariantConsequenceByTerm("any")) && !alteration.getAlteration().contains("mut")) {
                            data.put(getErrorMessage(getTarget(alteration.getGene().getHugoSymbol(), getAlterationName(alteration)), VARIANT_CONSEQUENCE_ANY_IS_INAPPROPRIATE));
                        }
                    }
                }
            }
        }
        return data;
    }

    public static JSONArray compareActionableGene() throws IOException {
        String json = null;
        JSONArray data = new JSONArray();
        json = FileUtils.readPublicOncoKBRemote("https://www.oncokb.org/api/v1/evidences/lookup?levelOfEvidence=" + org.apache.commons.lang3.StringUtils.join(LevelUtils.getTherapeuticLevels(), ",") + "&evidenceTypes=" + org.apache.commons.lang3.StringUtils.join(EvidenceTypeUtils.getTreatmentEvidenceTypes(), ","));

        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Evidence> publicTherapeuticEvidences = mapper.readValue(json,new TypeReference<ArrayList<Evidence>>() {});
        Set<String> publicEvidenceStrings = publicTherapeuticEvidences.stream().map(evidence -> evidenceUniqueString(evidence)).collect(Collectors.toSet());

        Set<Evidence> latestTherapeuticEvidences = EvidenceUtils.getEvidenceByEvidenceTypesAndLevels(EvidenceTypeUtils.getTreatmentEvidenceTypes(), LevelUtils.getTherapeuticLevels());
        Set<String> latestEvidenceStrings = latestTherapeuticEvidences.stream().map(evidence -> evidenceUniqueString(evidence)).collect(Collectors.toSet());

        Set<String> commons = new HashSet<>(Sets.intersection(latestEvidenceStrings, publicEvidenceStrings));
        publicEvidenceStrings.removeAll(commons);
        latestEvidenceStrings.removeAll(commons);


        publicEvidenceStrings.stream().forEach(string -> data.put(getErrorMessage(string, "Public")));
        latestEvidenceStrings.stream().forEach(string -> data.put(getErrorMessage(string, "Latest")));

        return data;
    }

    private static String evidenceUniqueString(Evidence evidence) {
        List<String> strings = new ArrayList<>();
        strings.add(evidence.getLevelOfEvidence().name());
        strings.add(evidence.getGene().getHugoSymbol());
        strings.add(getEvidenceSortedAlterationsName(evidence));
        strings.add(TumorTypeUtils.getEvidenceTumorTypesName(evidence));
        strings.add(TreatmentUtils.getTreatmentName(evidence.getTreatments()));
        List<String> pmids = EvidenceUtils.getPmids(Collections.singleton(evidence)).stream().sorted().collect(Collectors.toList());
        strings.add(pmids.size() > 0 ? String.join(", ", pmids) : " 0 pmids");
        strings.add(EvidenceUtils.getAbstracts(Collections.singleton(evidence)).size() + " abstract(s)");
        return String.join(" / ", strings);
    }

    private static Set<String> specialAlterationNames() {
        Set<String> names = new HashSet<>();
        names.addAll(NamingUtils.getAllAbbreviations());
        names.addAll(NamingUtils.getAllAbbreviationFullNames());
        names.add("M1?");
        return names;
    }

    private static Set<String> findIncorrectPmids(Evidence evidence, List<Article> allArticles) {
        Pattern pmidPattern = Pattern.compile("PMIDs?:\\s*([\\d,\\s*]+)", Pattern.CASE_INSENSITIVE);
        Matcher m = pmidPattern.matcher(evidence.getDescription());
        int start = 0;
        Set<String> pmidToSearch = new LinkedHashSet<>();
        while (m.find(start)) {
            String pmids = m.group(1).trim();
            for (String pmid : pmids.split(", *(PMID:)? *")) {
                if (!pmid.isEmpty()) {
                    Optional<Article> articleOptional = allArticles.stream().filter(article -> article.getPmid() != null && article.getPmid().equals(pmid)).findFirst();
                    if (!articleOptional.isPresent()) {
                        pmidToSearch.add(pmid);
                    }
                }
            }
            start = m.end();
        }

        return pmidToSearch;
    }

    private static String getEvidenceAlterationsName(Evidence evidence) {
        return evidence.getAlterations().stream().map(alteration -> getAlterationName(alteration)).collect(Collectors.joining(", "));
    }

    private static String getEvidenceSortedAlterationsName(Evidence evidence) {
        return evidence.getAlterations().stream().map(alteration -> getAlterationName(alteration)).sorted().collect(Collectors.joining(", "));
    }

    private static String getTargetByAlteration(Alteration alteration) {
        return getTarget(alteration.getGene().getHugoSymbol(), getAlterationName(alteration));
    }

    private static String getAlterationName(Alteration alteration) {
        if (alteration.getName().equals(alteration.getAlteration()) || alteration.getAlteration().contains("excluding")) {
            return alteration.getName();
        } else {
            return alteration.getName() + " (" + alteration.getAlteration() + ")";
        }
    }

    public static JSONObject getErrorMessage(String target, String reason) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("target", target);
        jsonObject.put("reason", reason);
        return jsonObject;
    }

    public static String getTarget(String hugoSymbol) {
        return getTarget(hugoSymbol, null);
    }

    public static String getTarget(String hugoSymbol, String alteration) {
        return getTarget(hugoSymbol, alteration, null);
    }

    public static String getTarget(String hugoSymbol, String alteration, String tumorType) {
        return getTarget(hugoSymbol, alteration, tumorType, null);
    }

    public static String getTarget(String hugoSymbol, String alteration, String tumorType, String treatment) {
        return getTarget(hugoSymbol, null, alteration, tumorType, treatment);
    }

    public static String getTarget(String hugoSymbol, EvidenceType evidenceType, String alteration, String tumorType, String treatment) {
        List<String> items = new ArrayList<>();
        if (!StringUtils.isNullOrEmpty(hugoSymbol)) {
            items.add(hugoSymbol);
        }
        if (evidenceType != null) {
            items.add(evidenceType.name());
        }
        if (!StringUtils.isNullOrEmpty(alteration)) {
            items.add(alteration);
        }
        if (!StringUtils.isNullOrEmpty(tumorType)) {
            items.add(tumorType);
        }
        if (!StringUtils.isNullOrEmpty(treatment)) {
            items.add(treatment);
        }
        return String.join(" / ", items);
    }
}
