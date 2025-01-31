package org.mskcc.cbio.oncokb.util;

import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.bo.AlterationBo;
import org.mskcc.cbio.oncokb.bo.ArticleBo;
import org.mskcc.cbio.oncokb.bo.EvidenceBo;
import org.mskcc.cbio.oncokb.model.*;

import javax.xml.parsers.ParserConfigurationException;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;
import static org.mskcc.cbio.oncokb.model.RelevantTumorTypeDirection.DOWNWARD;
import static org.mskcc.cbio.oncokb.util.LevelUtils.INFO_LEVELS;

/**
 * Created by Hongxin on 8/10/15.
 */
public class EvidenceUtils {
    private static EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();

    /**
     * Remove evidences if its alteration in the alteration list
     *
     * @param evidences
     * @param alterations
     * @return
     */
    public static List<Evidence> removeByAlterations(List<Evidence> evidences, Collection<Alteration> alterations) {
        if (alterations != null) {
            Iterator<Evidence> i = evidences.iterator();
            while (i.hasNext()) {
                Boolean contain = false;
                Evidence evidence = i.next();
                for (Alteration alteration : alterations) {
                    if (alteration != null) {
                        for (Alteration eviAlt : evidence.getAlterations()) {
                            if (eviAlt != null && alteration.equals(eviAlt)) {
                                contain = true;
                                break;
                            }
                        }
                        if (contain) {
                            i.remove();
                            break;
                        }
                    }
                }
            }
        }
        return evidences;
    }

    public static Set<Evidence> getRelevantEvidences(
        Query query, Alteration matchedAlt,
        Set<EvidenceType> evidenceTypes, Set<LevelOfEvidence> levelOfEvidences, List<Alteration> relevantAlterations, List<Alteration> alternativeAlleles, Boolean geneQueryOnly) {
        if (query == null) {
            return new HashSet<>();
        }
        Gene gene = GeneUtils.getGene(query.getEntrezGeneId(), query.getHugoSymbol());
        if (gene != null) {
            String variantId = query.getQueryId() +
                "&" + evidenceTypes.toString() +
                (levelOfEvidences == null ? "" : ("&" + levelOfEvidences.toString()));
            if (matchedAlt == null) {
                matchedAlt = AlterationUtils.getAlteration(gene.getHugoSymbol(), query.getAlteration(),
                    AlterationType.getByName(query.getAlterationType()), query.getConsequence(), query.getProteinStart(), query.getProteinEnd(), query.getReferenceGenome());
            }

            Set<Evidence> relevantEvidences;
            EvidenceQueryRes evidenceQueryRes = new EvidenceQueryRes();

            List<TumorType> relevantTumorTypes = new ArrayList<>();
            if (query.getTumorType() != null) {
                relevantTumorTypes = TumorTypeUtils.findRelevantTumorTypes(query.getTumorType());
                evidenceQueryRes.setExactMatchedTumorType(ApplicationContextSingleton.getTumorTypeBo().getByName(query.getTumorType()));
            }
            evidenceQueryRes.setGene(gene);
            evidenceQueryRes.setQuery(query);
            evidenceQueryRes.setAlterations(relevantAlterations);
            evidenceQueryRes.setOncoTreeTypes(relevantTumorTypes);
            evidenceQueryRes.setExactMatchedAlteration(matchedAlt);
            evidenceQueryRes.setLevelOfEvidences(levelOfEvidences == null ? null : new ArrayList<>(levelOfEvidences));

            relevantEvidences = getEvidence(query.getReferenceGenome(), evidenceQueryRes, evidenceTypes, levelOfEvidences);

            return filterEvidence(relevantEvidences, evidenceQueryRes, geneQueryOnly);
        } else {
            return new HashSet<>();
        }
    }

    public static Set<Evidence> getEvidenceByEvidenceTypesAndLevels(Set<EvidenceType> types, Set<LevelOfEvidence> levels) {
        List<Evidence> evidences = new ArrayList<>();
        Set<Evidence> cachedEvidences = CacheUtils.getAllEvidences();
        for (Evidence evidence : cachedEvidences) {
            if (types != null && types.size() > 0 && !types.contains(evidence.getEvidenceType())) {
                continue;
            }
            if (levels != null && levels.size() > 0 && !levels.contains(evidence.getLevelOfEvidence())) {
                continue;
            }
            evidences.add(evidence);
        }
        return new HashSet<>(evidences);
    }

    private static List<Evidence> getEvidence(List<Alteration> alterations) {
        if (alterations == null || alterations.size() == 0) {
            return new ArrayList<>();
        }
        return getAlterationEvidences(alterations);
    }

    public static List<Evidence> getEvidence(List<Alteration> alterations, Set<EvidenceType> evidenceTypes, Set<LevelOfEvidence> levelOfEvidences) {
        if (alterations == null) {
            alterations = new ArrayList<>();
        }
        if (evidenceTypes == null) {
            evidenceTypes = new HashSet<>();
        }
        if (levelOfEvidences == null) {
            levelOfEvidences = new HashSet<>();
        }
        if (alterations.size() == 0) {
            return new ArrayList<>();
        }
        if (evidenceTypes.size() == 0 && levelOfEvidences.size() == 0) {
            return getEvidence(alterations);
        }
        List<Evidence> alterationEvidences = getAlterationEvidences(alterations);
        List<Evidence> result = new ArrayList<>();

        for (Evidence evidence : alterationEvidences) {
            if (evidenceTypes.size() > 0 && !evidenceTypes.contains(evidence.getEvidenceType())) {
                continue;
            }
            if (levelOfEvidences.size() > 0 && !levelOfEvidences.contains(evidence.getLevelOfEvidence())) {
                continue;
            }
            result.add(evidence);
        }
        return result;
    }

    public static List<Evidence> getEvidence(List<Alteration> alterations, Set<EvidenceType> evidenceTypes, TumorType matchedTumorType, List<TumorType> tumorTypes, Set<LevelOfEvidence> levelOfEvidences) {
        if (alterations == null || alterations.size() == 0) {
            return new ArrayList<>();
        }
        if (evidenceTypes == null || evidenceTypes.size() == 0) {
            return getEvidence(alterations);
        }
        if (tumorTypes == null || tumorTypes.size() == 0) {
            return getEvidence(alterations, evidenceTypes, levelOfEvidences);
        }
        if (levelOfEvidences == null || levelOfEvidences.size() == 0) {
            return evidenceBo.findEvidencesByAlteration(alterations, evidenceTypes, matchedTumorType, tumorTypes);
        } else {
            return evidenceBo.findEvidencesByAlteration(alterations, evidenceTypes, matchedTumorType, tumorTypes, levelOfEvidences);
        }
    }

    private static Set<Evidence> getEvidence(ReferenceGenome referenceGenome, EvidenceQueryRes query, Set<EvidenceType> evidenceTypes, Set<LevelOfEvidence> levelOfEvidences) {
        Set<Evidence> evidences = new HashSet<>();

        Set<Gene> genes = new HashSet<>(); //Get gene evidences
        TumorType matchedTumorType = ApplicationContextSingleton.getTumorTypeBo().getByName(query.getQuery().getTumorType());
        Set<Alteration> alterations = new HashSet<>();
        List<TumorType> upwardTumorTypes = new ArrayList<>();
        List<TumorType> downwardTumorTypes = new ArrayList<>();

        if (query.getOncoTreeTypes() != null) {
            upwardTumorTypes.addAll(query.getOncoTreeTypes());
        }
        downwardTumorTypes.addAll(TumorTypeUtils.findRelevantTumorTypes(query.getQuery().getTumorType(), null, DOWNWARD));

        if (query.getGene() != null) {
            genes.add(query.getGene());
            if ((query.getExactMatchedAlteration() != null && query.getExactMatchedAlteration().getAlteration().length() == 0) && query.getAlterations().isEmpty() && query.getAlleles().isEmpty()) {
                alterations.addAll(AlterationUtils.getAllAlterations(referenceGenome, query.getGene()));
            } else {
                if (query.getAlterations() != null) {
                    alterations.addAll(query.getAlterations());
                }
            }
        } else {
            // this is really a performance blow if we compute based on all genes and all alterations, there is a collection disjoint in the base layer
            // in this case, let's directly return all evidences with query, evidenceTypes and level of evidences filtered
            Set<Evidence> evidenceToReturn = CacheUtils.getAllEvidences();
            if (evidenceTypes != null && evidenceTypes.size() > 0) {
                evidenceToReturn = evidenceToReturn.stream().filter(evidence -> evidenceTypes.contains(evidence.getEvidenceType())).collect(toSet());
            }
            if (levelOfEvidences != null && levelOfEvidences.size() > 0) {
                evidenceToReturn = evidenceToReturn.stream().filter(evidence -> levelOfEvidences.contains(evidence.getLevelOfEvidence())).collect(toSet());
            }
            if (StringUtils.isNotEmpty(query.getQuery().getTumorType())) {
                evidenceToReturn = evidenceToReturn.stream().filter(evidence -> {
                    if (evidence.getEvidenceType() != null) {
                        Set<TumorType> relevantCancerTypes = TumorTypeUtils.findEvidenceRelevantCancerTypes(evidence);
                        if (evidence.getEvidenceType().equals(EvidenceType.DIAGNOSTIC_IMPLICATION) && evidence.getLevelOfEvidence() != null && evidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_Dx1)) {
                            return !Collections.disjoint(downwardTumorTypes, relevantCancerTypes);
                        } else if (EvidenceTypeUtils.getTumorTypeEvidenceTypes().contains(evidence.getEvidenceType())) {
                            return !Collections.disjoint(upwardTumorTypes, relevantCancerTypes);
                        } else {
                            return true;
                        }
                    } else {
                        return true;
                    }
                }).collect(toSet());
            }
            return evidenceToReturn;
        }

        // Get all gene related evidences
        Map<Gene, Set<Evidence>> mappedEvidences =
            EvidenceUtils.getEvidenceByGenesAndEvidenceTypes(genes, Sets.intersection(EvidenceTypeUtils.getGeneEvidenceTypes(query.getQuery().isGermline()), evidenceTypes));
        for (Map.Entry<Gene, Set<Evidence>> cursor : mappedEvidences.entrySet()) {
            evidences.addAll(cursor.getValue());
        }

        List<Alteration> uniqueAlterationsWithoutAlternativeAlleles = new ArrayList<>(alterations);
        if (query.getExactMatchedAlteration() != null) {
            AlterationUtils.removeAlternativeAllele(referenceGenome, query.getExactMatchedAlteration(), uniqueAlterationsWithoutAlternativeAlleles);
        }
        // Get all mutation related evidences

        Set<EvidenceType> common = Sets.intersection(EvidenceTypeUtils.getMutationEvidenceTypes(query.getQuery().isGermline()), evidenceTypes);
        if (common.size() > 0) {
            evidences.addAll(getEvidence(new ArrayList<>(alterations), common, null));
        }

        if (uniqueAlterationsWithoutAlternativeAlleles.size() > 0) {
            // For sensitive evidences, get all ignore tumor types. They will be propagated to other tumor types
            // in assignEvidence function
            common = Sets.intersection(EvidenceTypeUtils.getSensitiveTreatmentEvidenceTypes(), evidenceTypes);
            if (common.size() > 0) {
                evidences.addAll(getEvidence(uniqueAlterationsWithoutAlternativeAlleles, common, levelOfEvidences));
            }

            // Get diagnostic implication evidences
            if (evidenceTypes.contains(EvidenceType.DIAGNOSTIC_IMPLICATION)) {
                evidences.addAll(getEvidence(uniqueAlterationsWithoutAlternativeAlleles, Collections.singleton(EvidenceType.DIAGNOSTIC_IMPLICATION), matchedTumorType, StringUtils.isEmpty(query.getQuery().getTumorType()) ? null : downwardTumorTypes, levelOfEvidences));
            }

            // Get other tumor type related evidences
            Set<EvidenceType> restTTevidenceTypes = EvidenceTypeUtils.getTumorTypeEvidenceTypes();
            restTTevidenceTypes.removeAll(EvidenceTypeUtils.getSensitiveTreatmentEvidenceTypes());
            restTTevidenceTypes.remove(EvidenceType.DIAGNOSTIC_IMPLICATION);
            common = Sets.intersection(restTTevidenceTypes, evidenceTypes);
            if (common.size() > 0) {
                evidences.addAll(getEvidence(uniqueAlterationsWithoutAlternativeAlleles, common, matchedTumorType, StringUtils.isEmpty(query.getQuery().getTumorType()) ? null : upwardTumorTypes, levelOfEvidences));
            }
        }

        return evidences;
    }

    public static List<Evidence> getAlterationEvidences(List<Alteration> alterations) {
        List<Evidence> evidences = new ArrayList<>();

        List<Evidence> geneEvidences = getAllEvidencesByAlterationsGenes(alterations);
        Set<Alteration> altSet = new HashSet<>(alterations);
        for (int i = 0; i < geneEvidences.size(); i++) {
            Evidence evidence = geneEvidences.get(i);
            if (!Collections.disjoint(evidence.getAlterations(), altSet)) {
                evidences.add(evidence);
            }
        }
        return evidences;
    }

    public static Map<Gene, Set<Evidence>> getEvidenceByGenes(Set<Gene> genes) {
        Map<Gene, Set<Evidence>> evidences = new HashMap<>();
        for (Gene gene : genes) {
            if (gene != null) {
                evidences.put(gene, new HashSet<>(CacheUtils.getEvidences(gene)));
            }
        }
        return evidences;
    }

    public static Map<Gene, Set<Evidence>> getEvidenceByGenesAndEvidenceTypes(Set<Gene> genes, Set<EvidenceType> evidenceTypes) {
        Map<Gene, Set<Evidence>> result = new HashMap<>();
        if (evidenceTypes == null || evidenceTypes.isEmpty())
            return result;
        for (Gene gene : genes) {
            if (gene != null) {
                List<Evidence> evidences = CacheUtils.getEvidences(gene);
                Set<Evidence> filtered = new HashSet<>();
                for (Evidence evidence : evidences) {
                    if (evidenceTypes.contains(evidence.getEvidenceType())) {
                        filtered.add(evidence);
                    }
                }
                result.put(gene, filtered);
            }
        }
        return result;
    }

    public static Set<Evidence> getEvidenceByGeneAndEvidenceTypes(Gene gene, Set<EvidenceType> evidenceTypes) {
        Set<Evidence> result = new HashSet<>();
        if (gene != null) {
            List<Evidence> evidences = CacheUtils.getEvidences(gene);
            for (int i = 0; i < evidences.size(); i++) {
                Evidence evidence = evidences.get(i);
                if (evidenceTypes.contains(evidence.getEvidenceType())) {
                    result.add(evidence);
                }
            }
        }
        return result;
    }

    public static List<Evidence> getGenomicIndicatorsByAlteration(List<Alteration> alterations, String alleleState) {
        List<Evidence> genomicIndicatorEvis = EvidenceUtils.getEvidence(alterations, Collections.singleton(EvidenceType.GENOMIC_INDICATOR), null);
        if (StringUtils.isNotEmpty(alleleState)) {
            genomicIndicatorEvis = genomicIndicatorEvis.stream().filter(evidence -> StringUtils.isEmpty(evidence.getKnownEffect()) || evidence.getKnownEffect().toLowerCase().contains(alleleState)).collect(Collectors.toList());
        }
        return genomicIndicatorEvis;
    }

    public static List<Evidence> getGenomicIndicatorAssociatedWithPathogenicVariants(Gene gene, String alleleState) {
        List<Evidence> genomicIndicatorEvis = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, Collections.singleton(EvidenceType.GENOMIC_INDICATOR)).stream().collect(Collectors.toList());
        if (StringUtils.isNotEmpty(alleleState)) {
            genomicIndicatorEvis = genomicIndicatorEvis.stream().filter(evidence -> StringUtils.isEmpty(evidence.getKnownEffect()) || evidence.getKnownEffect().toLowerCase().contains(alleleState)).collect(Collectors.toList());
        }
        return genomicIndicatorEvis;
    }

    public static Set<Evidence> convertEvidenceLevel(List<Evidence> evidences, Set<TumorType> tumorTypes) {
        Set<Evidence> tmpEvidences = new HashSet<>();
        TumorForm tumorForm = TumorTypeUtils.checkTumorForm(tumorTypes);
        for (Evidence evidence : evidences) {
            Evidence tmpEvidence = new Evidence(evidence, evidence.getId());
            Boolean flag = true;
            if (Collections.disjoint(tmpEvidence.getCancerTypes(), tumorTypes)) {
                if (tmpEvidence.getLevelOfEvidence() != null) {
                    LevelOfEvidence propagationLevel = getPropagationLevel(tmpEvidence, tumorForm);
                    if (propagationLevel != null) {
                        tmpEvidence.setLevelOfEvidence(propagationLevel);
                    } else {
                        flag = false;
                    }

                    // Don't include any resistance evidence if tumor type is not matched.
                    if (LevelUtils.getResistanceLevels().contains(tmpEvidence.getLevelOfEvidence())) {
                        flag = false;
                    }
                }
            }

            if (flag) {
                tmpEvidences.add(tmpEvidence);
            }
        }
        return tmpEvidences;
    }

    private static Set<Evidence> filterEvidence(Set<Evidence> evidences, EvidenceQueryRes evidenceQuery, Boolean geneQueryOnly) {
        Set<Evidence> filtered = new HashSet<>();

        // Logic step 1, liquid therapies will not be propagated to solid
//        boolean isSolidTumorQuery = false;
//        for (TumorType tumorType : evidenceQuery.getOncoTreeTypes()) {
//            if (TumorTypeUtils.isSolidTumor(tumorType)) {
//                isSolidTumorQuery = true;
//                break;
//            }
//        }

        if (evidenceQuery.getGene() != null) {
            for (Evidence evidence : evidences) {

                if (evidence.getGene().equals(evidenceQuery.getGene())) {
                    //Add all gene specific evidences
                    if (evidence.getAlterations().isEmpty()) {
                        filtered.add(evidence);
                    } else if (evidenceQuery.getExactMatchedAlteration() != null && StringUtils.isEmpty(evidenceQuery.getExactMatchedAlteration().getAlteration()) && geneQueryOnly) {
                        filtered.add(evidence);
                    } else {
                        boolean hasjointed = !Collections.disjoint(evidence.getAlterations(), evidenceQuery.getAlterations());
                        if (!hasjointed) {
                            hasjointed = !Collections.disjoint(evidence.getAlterations(), evidenceQuery.getAlleles());
                        }
                        if (hasjointed) {
                            if (evidence.getCancerTypes().isEmpty()) {
                                if (evidence.getEvidenceType().equals(EvidenceType.ONCOGENIC)) {
                                    if (evidence.getDescription() == null) {
                                        List<Alteration> alterations = new ArrayList<>();
                                        alterations.addAll(evidence.getAlterations());
                                    }
                                }
                                filtered.add(evidence);
                            } else {
                                TumorForm tumorForm = TumorTypeUtils.checkTumorForm(new HashSet<>(evidenceQuery.getOncoTreeTypes()));

                                // for evidence has relevant cancer types, we should only look at the exact matched cancer type of the evidence query
                                hasjointed = !Collections.disjoint(TumorTypeUtils.findEvidenceRelevantCancerTypes(evidence), evidenceQuery.getExactMatchedTumorType() == null ? evidenceQuery.getOncoTreeTypes() : Collections.singleton(evidenceQuery.getExactMatchedTumorType()));

                                if (hasjointed || com.mysql.jdbc.StringUtils.isNullOrEmpty(evidenceQuery.getQuery().getTumorType())) {
                                    filtered.add(evidence);
                                } else if (tumorForm != null) {
                                    // if the cancer type is specifically excluded, it should not even be propagated
                                    if (evidence.getExcludedCancerTypes().size() > 0) {
                                        hasjointed = !Collections.disjoint(evidence.getExcludedCancerTypes(), evidenceQuery.getOncoTreeTypes());
                                        if (hasjointed) {
                                            continue;
                                        }
                                    }
                                    if (evidence.getLevelOfEvidence() != null) {
                                        Evidence propagatedLevel = getPropagateEvidence(evidenceQuery.getLevelOfEvidences(), evidence, tumorForm);
                                        if (propagatedLevel != null) {
                                            filtered.add(propagatedLevel);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return filtered;
    }

    private static LevelOfEvidence resolveUnknownTumorFormLevel(LevelOfEvidence solidPropagationLevel, LevelOfEvidence liquidPropagationLevel) {
        if (solidPropagationLevel == null || liquidPropagationLevel == null) {
            return null;
        } else if (solidPropagationLevel.equals(liquidPropagationLevel)) {
            return liquidPropagationLevel;
        } else {
            return null;
        }
    }

    public static LevelOfEvidence getPropagationLevel(Evidence evidence, TumorForm queriedTumorForm) {
        if (evidence == null) {
            return null;
        }
        if (queriedTumorForm == null) {
            return resolveUnknownTumorFormLevel(evidence.getSolidPropagationLevel(), evidence.getLiquidPropagationLevel());
        }
        return queriedTumorForm.equals(TumorForm.SOLID) ? evidence.getSolidPropagationLevel() : evidence.getLiquidPropagationLevel();
    }

    private static Evidence getPropagateEvidence(List<LevelOfEvidence> allowedLevels, Evidence evidence, TumorForm queriedTumorForm) {
        Evidence propagatedEvidence = null;
        if (queriedTumorForm == null) {
            return propagatedEvidence;
        }
        LevelOfEvidence propagationLevel = getPropagationLevel(evidence, queriedTumorForm);
        if (propagationLevel != null) {
            if (allowedLevels == null
                || allowedLevels.size() == 0
                || allowedLevels.contains(propagationLevel)) {
                propagatedEvidence = new Evidence(evidence, evidence.getId());
                propagatedEvidence.setLevelOfEvidence(propagationLevel);
                propagatedEvidence.setFdaLevel(FdaAlterationUtils.convertToFdaLevel(propagationLevel));
            }
        }
        return propagatedEvidence;
    }

    public static Map<Gene, List<Evidence>> separateEvidencesByGene(Set<Gene> genes, Set<Evidence> evidences) {
        Map<Gene, List<Evidence>> result = new HashMap<>();

        for (Gene gene : genes) {
            result.put(gene, new ArrayList<>());
        }

        for (Evidence evidence : evidences) {
            if (result.containsKey(evidence.getGene())) {
                result.get(evidence.getGene()).add(evidence);
            }
        }
        return result;
    }

    public static MutationEffect getMutationEffectFromEvidence(Set<Evidence> evidences) {
        Set<MutationEffect> result = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (StringUtils.isNotEmpty(evidence.getKnownEffect())) {
                result.add(MutationEffect.getByName(evidence.getKnownEffect()));
            }
        }

        if (result.size() > 1) {
            return MainUtils.findHighestMutationEffect(result);
        } else if (result.size() == 1) {
            return result.iterator().next();
        } else {
            return null;
        }
    }

    public static Oncogenicity getOncogenicityFromEvidence(Set<Evidence> evidences) {
        Set<Oncogenicity> result = new HashSet<>();
        if (evidences == null) return null;
        for (Evidence evidence : evidences) {
            if (evidence.getKnownEffect() != null) {
                result.add(Oncogenicity.getByEvidence(evidence));
            }
        }

        if (result.size() > 1) {
            return MainUtils.findHighestOncogenicity(result);
        } else if (result.size() == 1) {
            return result.iterator().next();
        } else {
            return null;
        }
    }

    public static Pathogenicity getPathogenicityFromEvidence(Set<Evidence> evidences) {
        Set<Pathogenicity> result = new HashSet<>();
        if (evidences == null) return null;
        for (Evidence evidence : evidences) {
            if (evidence.getKnownEffect() != null) {
                result.add(Pathogenicity.getByEvidence(evidence));
            }
        }

        if (result.size() > 1) {
            return MainUtils.findHighestPathogenicity(result);
        } else if (result.size() == 1) {
            return result.iterator().next();
        } else {
            return null;
        }
    }

    public static Set<String> getPmids(Set<Evidence> evidences) {
        Set<String> result = new HashSet<>();

        for (Evidence evidence : evidences) {
            for (Article article : evidence.getArticles()) {
                if (article.getPmid() != null) {
                    result.add(article.getPmid());
                }
            }
        }
        return result;
    }

    public static Set<ArticleAbstract> getAbstracts(Set<Evidence> evidences) {
        Set<ArticleAbstract> result = new HashSet<>();

        for (Evidence evidence : evidences) {
            for (Article article : evidence.getArticles()) {
                if (article.getAbstractContent() != null) {
                    ArticleAbstract articleAbstract = new ArticleAbstract();
                    articleAbstract.setAbstractContent(article.getAbstractContent());
                    articleAbstract.setLink(article.getLink());
                    result.add(articleAbstract);
                }
            }
        }
        return result;
    }

    public static LinkedHashSet<String> getDrugs(Set<Evidence> evidences) {
        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (Evidence evidence : evidences) {
            for (Treatment treatment : evidence.getTreatments().stream().sorted(Comparator.comparingInt(Treatment::getPriority)).collect(toList())) {
                LinkedHashSet<String> drugsInTreatment = new LinkedHashSet<>();
                for (TreatmentDrug treatmentDrug : treatment.getTreatmentDrugs().stream().sorted(Comparator.comparingInt(TreatmentDrug::getPriority)).collect(toList())) {
                    if (treatmentDrug.getDrug() != null && treatmentDrug.getDrug().getDrugName() != null) {
                        drugsInTreatment.add(treatmentDrug.getDrug().getDrugName());
                    }
                }
                result.add(StringUtils.join(drugsInTreatment, " + "));
            }
        }
        return result;
    }

    public static Map<Gene, Set<Evidence>> getAllGeneBasedEvidences() {
        Set<Gene> genes = CacheUtils.getAllGenes();
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getEvidenceByGenes(genes);
        return evidences;
    }

    public static Set<Evidence> getEvidenceBasedOnHighestOncogenicity(Set<Evidence> evidences) {
        Set<Evidence> filtered = new HashSet<>();
        Map<Oncogenicity, Set<Evidence>> map = new HashMap<>();

        if (evidences == null || evidences.size() == 0)
            return filtered;

        for (Evidence evidence : evidences) {
            if (evidence.getEvidenceType() != null && evidence.getEvidenceType().equals(EvidenceType.ONCOGENIC)) {
                Oncogenicity oncogenicity = Oncogenicity.getByEvidence(evidence);

                if (oncogenicity != null) {
                    if (!map.containsKey(oncogenicity))
                        map.put(oncogenicity, new HashSet<Evidence>());

                    map.get(oncogenicity).add(evidence);
                }
            }
        }

        Oncogenicity highestOncogenicity = MainUtils.findHighestOncogenicByEvidences(evidences);
        if (map.get(highestOncogenicity) != null)
            filtered = map.get(highestOncogenicity);
        return filtered;
    }

    public static Set<Evidence> getOnlyHighestLevelEvidences(Set<Evidence> evidences, ReferenceGenome referenceGenome, Alteration exactMatch) {
        Map<LevelOfEvidence, Set<Evidence>> levels = separateEvidencesByLevel(evidences);
        Set<LevelOfEvidence> keys = levels.keySet();

        LevelOfEvidence highestLevel = LevelUtils.getHighestLevel(keys);
        LevelOfEvidence highestSensitiveLevel = LevelUtils.getHighestSensitiveLevel(keys);

        Set<Evidence> tagAlongEvidences = (highestLevel == null || INFO_LEVELS.contains(highestLevel)) ? new HashSet<>() :
            evidences.stream().filter(
                evidence -> INFO_LEVELS.contains(evidence.getLevelOfEvidence())
            ).collect(Collectors.toSet());

        // When resistance level is not null, we need to consider whether the sensitive/resistance level is alteration specific
        // if so the resistance level is broader
        if (highestLevel != highestSensitiveLevel && highestSensitiveLevel != null) {
            LevelOfEvidence highestResistanceLevel = LevelUtils.getHighestResistanceLevel(keys);
            Set<Alteration> sensitiveAlterations = AlterationUtils.getEvidencesAlterations(levels.get(highestSensitiveLevel));
            Set<Alteration> resistanceAlterations = AlterationUtils.getEvidencesAlterations(levels.get(highestResistanceLevel));
            Set<Alteration> resistanceRelevantAlts = new HashSet<>();
            for (Alteration alteration : resistanceAlterations) {
                List<Alteration> relevantAlterations = AlterationUtils.getRelevantAlterations(referenceGenome, alteration);

                // we need to remove the ranges that overlap but not fully cover the alteration
                Iterator<Alteration> i = relevantAlterations.iterator();
                while (i.hasNext()) {
                    Alteration relAlt = i.next();
                    if (AlterationUtils.consequenceRelated(relAlt.getConsequence(), alteration.getConsequence())) {
                        if (relAlt.getProteinStart() > alteration.getProteinStart() || relAlt.getProteinEnd() < alteration.getProteinEnd()) {
                            i.remove();
                        }
                    }
                }
                resistanceRelevantAlts.addAll(relevantAlterations);
            }
            if (exactMatch != null && Collections.disjoint(resistanceRelevantAlts, sensitiveAlterations) && sensitiveAlterations.contains(exactMatch)) {
                highestLevel = highestSensitiveLevel;
            }
        }

        // if the levels include more than one evidence, we only return one
        if (highestLevel != null) {
            if (tagAlongEvidences.size() > 0) {
                Set<Evidence> mergeResult = new HashSet<>();
                mergeResult.add(levels.get(highestLevel).iterator().next());
                mergeResult.addAll(tagAlongEvidences);
                return mergeResult;
            } else {
                return levels.get(highestLevel);
            }
        } else {
            return new HashSet<>();
        }
    }

    public static Set<Evidence> getOnlySignificantLevelsEvidences(Set<Evidence> evidences) {
        Map<LevelOfEvidence, Set<Evidence>> levels = separateEvidencesByLevel(evidences);
        Set<LevelOfEvidence> keys = levels.keySet();
        LevelOfEvidence highestLevel = LevelUtils.getHighestLevel(keys);

        Set<Evidence> result = new HashSet<>();

        if (highestLevel != null) {
            result.addAll(levels.get(highestLevel));
        }
        return result;
    }

    public static Map<LevelOfEvidence, Set<Evidence>> separateEvidencesByLevel(Set<Evidence> evidences) {
        Map<LevelOfEvidence, Set<Evidence>> levels = new HashMap<>();

        for (Evidence evidence : evidences) {
            if (evidence.getLevelOfEvidence() != null) {
                if (!levels.containsKey(evidence.getLevelOfEvidence())) {
                    levels.put(evidence.getLevelOfEvidence(), new HashSet<Evidence>());
                }
                levels.get(evidence.getLevelOfEvidence()).add(evidence);
            }
        }
        return levels;
    }

    public static Set<Evidence> keepHighestLevelForSameTreatments(Set<Evidence> evidences, ReferenceGenome referenceGenome, Alteration exactMatch) {
        Map<String, Set<Evidence>> maps = new HashedMap();
        Set<Evidence> filtered = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (evidence.getTreatments() != null && evidence.getTreatments().size() > 0) {
                List<String> treatments = TreatmentUtils.getTreatments(new HashSet<>(evidence.getTreatments()));

                for (String treatment : treatments) {
                    if (!maps.containsKey(treatment)) {
                        maps.put(treatment, new HashSet<>());
                    }
                    maps.get(treatment).add(evidence);
                }
            } else {
                // Keep all un-treatment evidences
                filtered.add(evidence);
            }
        }

        TumorType tumorTypeNA = new TumorType();
        tumorTypeNA.setSubtype("NA");
        List<TumorType> mostFrequentTumorTypes = new ArrayList<>();
        Map<TumorType, List<Evidence>> tumorTypeFrequency = new HashMap<>();
        evidences.stream().filter(evidence -> !evidence.getCancerTypes().isEmpty()).forEach(evidence -> evidence.getCancerTypes().forEach(tumorType -> {
            if (!tumorTypeFrequency.containsKey(tumorType)) {
                tumorTypeFrequency.put(tumorType, new ArrayList<>());
            }
            tumorTypeFrequency.get(tumorType).add(evidence);
        }));
        tumorTypeFrequency.entrySet().stream().sorted((o1, o2) -> {
            int result = o2.getValue().size() - o1.getValue().size();
            if (result == 0) {
                return TumorTypeUtils.getTumorTypeName(o1.getKey()).compareTo(TumorTypeUtils.getTumorTypeName(o2.getKey()));
            } else {
                return result;
            }
        }).forEach(tumorTypeListEntry -> mostFrequentTumorTypes.add(tumorTypeListEntry.getKey()));

        for (Map.Entry<String, Set<Evidence>> entry : maps.entrySet()) {
            Set<Evidence> highestEvis = EvidenceUtils.getOnlyHighestLevelEvidences(entry.getValue(), referenceGenome, exactMatch);

            // If highestEvis has more than 1 items, find highest original level if the level is 3B
            // We also return R2 when the same treatment has sensitive level
            if (highestEvis.size() > 1) {
                Set<LevelOfEvidence> checkLevels = new HashSet<>();
                checkLevels.add(LevelOfEvidence.LEVEL_3B);

                for (Evidence highestEvi : highestEvis) {
                    if (checkLevels.contains(highestEvi.getLevelOfEvidence())) {
                        Set<Integer> evidenceIds = new HashSet<>();
                        Set<Gene> genes = new HashSet<>();

                        for (Evidence evidence : highestEvis) {
                            evidenceIds.add(evidence.getId());
                            genes.add(evidence.getGene());
                        }

                        Set<Evidence> originalEvis = EvidenceUtils.getEvidencesByGenesAndIds(genes, evidenceIds);

                        Set<Evidence> highestOriginalEvis = EvidenceUtils.getOnlyHighestLevelEvidences(originalEvis, referenceGenome, exactMatch);
                        Set<Integer> filteredIds = new HashSet<>();
                        for (Evidence evidence : highestOriginalEvis) {
                            filteredIds.add(evidence.getId());
                        }
                        List<Evidence> sameLevelEvidences = new ArrayList<>();
                        for (Evidence evidence : highestEvis) {
                            if (filteredIds.contains(evidence.getId())) {
                                sameLevelEvidences.add(evidence);
                            }
                        }
                        if (sameLevelEvidences.size() == 1) {
                            filtered.add(sameLevelEvidences.iterator().next());
                        } else if (sameLevelEvidences.size() > 1) {
                            // Select evidence with most frequently occurred tumor type when the level and the treatment are the same
                            sameLevelEvidences.sort((o1, o2) -> {
                                int o1Index = o1.getCancerTypes().stream().map(mostFrequentTumorTypes::indexOf).sorted().findFirst().get();
                                int o2Index = o2.getCancerTypes().stream().map(mostFrequentTumorTypes::indexOf).sorted().findFirst().get();
                                int result = o1Index - o2Index;
                                if (result == 0) {
                                    return -1;
                                } else {
                                    return result;
                                }
                            });
                            filtered.add(sameLevelEvidences.iterator().next());
                        }
                    } else {
                        filtered.add(highestEvi);
                    }
                }
            } else {
                filtered.addAll(highestEvis);
            }
        }
        return filtered;
    }

    public static Set<Evidence> getEvidencesByUUID(String uuid) {
        if (uuid == null) {
            return new HashSet<>();
        }
        return CacheUtils.getEvidencesByUUID(uuid);
    }

    public static Set<Evidence> getEvidencesByUUIDs(Set<String> uuids) {
        if (uuids == null) {
            return new HashSet<>();
        }
        return CacheUtils.getEvidencesByUUIDs(uuids);
    }

    public static Evidence getEvidenceByEvidenceId(Integer id) {
        if (id == null) {
            return null;
        }
        Set<Evidence> evidences = new HashSet<>();
        evidences = CacheUtils.getEvidencesByIds(Collections.singleton(id));
        if (evidences == null || evidences.size() > 1) {
            return null;
        }
        return evidences.iterator().next();
    }

    public static Set<Evidence> getEvidencesByGenesAndIds(Set<Gene> genes, Set<Integer> ids) {
        if (ids == null) {
            return new HashSet<>();
        }
        return CacheUtils.getEvidencesByGenesAndIds(genes, ids);
    }

    public static Set<Evidence> getEvidenceByEvidenceIds(Set<Integer> ids) {
        if (ids == null) {
            return new HashSet<>();
        }
        return CacheUtils.getEvidencesByIds(ids);
    }

    public static Set<Evidence> filterEvidenceByKnownEffect(Set<Evidence> evidences, String knownEffect) {
        if (knownEffect == null) {
            return null;
        }
        Set<Evidence> result = new HashSet<>();
        for (Evidence evidence : evidences) {
            if (evidence.getKnownEffect() != null
                && evidence.getKnownEffect().equalsIgnoreCase(knownEffect)) {
                result.add(evidence);
            }
        }
        return result;
    }

    public static Set<Evidence> getSensitiveEvidences(Set<Evidence> evidences) {
        return filterEvidenceByKnownEffect(evidences, "sensitive");
    }

    public static Set<Evidence> getResistanceEvidences(Set<Evidence> evidences) {
        return filterEvidenceByKnownEffect(evidences, "resistant");
    }

    // Temporary move evidence process methods here in order to share the code between new APIs and legacies
    public static List<EvidenceQueryRes> processRequest(List<Query> requestQueries, Set<EvidenceType> evidenceTypes,
                                                        Set<LevelOfEvidence> levelOfEvidences, Boolean highestLevelOnly, Boolean geneQueryOnly) {
        List<EvidenceQueryRes> evidenceQueries = new ArrayList<>();

        levelOfEvidences = levelOfEvidences == null ? levelOfEvidences :
            new HashSet<>(CollectionUtils.intersection(levelOfEvidences, LevelUtils.getPublicLevels()));

        highestLevelOnly = highestLevelOnly == null ? false : highestLevelOnly;

        // when the LoE and ET are empty, no info should be returned
        if ((levelOfEvidences != null && levelOfEvidences.size() == 0) || evidenceTypes.size() == 0) {
            if (requestQueries == null || requestQueries.size() == 0) {
                EvidenceQueryRes query = new EvidenceQueryRes();
                return Collections.singletonList(query);
            } else {
                List<EvidenceQueryRes> evidenceQueryRes = new ArrayList<>();
                for (Query query : requestQueries) {
                    evidenceQueries.add(new EvidenceQueryRes());
                }
                return evidenceQueryRes;
            }
        }

        if (requestQueries == null || requestQueries.size() == 0) {
            Set<Evidence> evidences = new HashSet<>();
            if ((evidenceTypes != null && evidenceTypes.size() > 0) ||
                levelOfEvidences.size() > 0) {
                evidences = EvidenceUtils.getEvidenceByEvidenceTypesAndLevels(evidenceTypes, levelOfEvidences);
            }
            EvidenceQueryRes query = new EvidenceQueryRes();
            query.setEvidences(new ArrayList<>(evidences));
            return Collections.singletonList(query);
        } else {
            for (Query requestQuery : requestQueries) {
                EvidenceQueryRes query = new EvidenceQueryRes();

                requestQuery.enrich();

                if (evidenceTypes == null) {
                    evidenceTypes = new HashSet<>(EvidenceTypeUtils.getAllEvidenceTypes(requestQuery.isGermline()));
                }

                query.setQuery(requestQuery);

                query.setGene(GeneUtils.getGene(requestQuery.getEntrezGeneId(), requestQuery.getHugoSymbol()));

                if (requestQuery.getTumorType() != null && !requestQuery.getTumorType().isEmpty()) {
                    query.setExactMatchedTumorType(ApplicationContextSingleton.getTumorTypeBo().getByName(requestQuery.getTumorType()));
                    query.setOncoTreeTypes(
                        TumorTypeUtils.findRelevantTumorTypes(requestQuery.getTumorType()));
                }
                if (query.getGene() != null) {

                    if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(requestQuery.getAlteration())) {
                        Alteration alt = AlterationUtils.findAlteration(query.getGene(), requestQuery.getReferenceGenome(), requestQuery.getAlteration());

                        if (alt == null) {
                            alt = AlterationUtils.getAlteration(query.getGene().getHugoSymbol(),
                                requestQuery.getAlteration(), null, requestQuery.getConsequence(),
                                requestQuery.getProteinStart(), requestQuery.getProteinEnd(), requestQuery.getReferenceGenome());
                            AlterationUtils.annotateAlteration(alt, alt.getAlteration());
                        }
                        query.setExactMatchedAlteration(alt);
                        List<Alteration> relevantAlts = AlterationUtils.getRelevantAlterations(requestQuery.getReferenceGenome(), alt);

                        // Look for Oncogenic Mutations if no relevantAlt found for alt and alt is hotspot
                        if (relevantAlts.isEmpty()
                            && HotspotUtils.isHotspot(alt)) {
                            List<Alteration> oncogenicMutations = AlterationUtils.findOncogenicMutations(AlterationUtils.getAllAlterations(requestQuery.getReferenceGenome(), alt.getGene()));
                            if (!oncogenicMutations.isEmpty()) {
                                relevantAlts.addAll(oncogenicMutations);
                            }
                        }

                        Alteration alteration = AlterationUtils.getAlteration(query.getGene().getHugoSymbol(), requestQuery.getAlteration(), AlterationType.MUTATION, requestQuery.getConsequence(), requestQuery.getProteinStart(), requestQuery.getProteinEnd(), requestQuery.getReferenceGenome());
                        List<Alteration> allelesAlts = AlterationUtils.getAlleleAlterations(requestQuery.getReferenceGenome(), alteration);
                        relevantAlts.removeAll(allelesAlts);
                        query.setAlterations(relevantAlts);

                        query.setAlleles(new ArrayList<>(allelesAlts));
                    } else {
                        // if no alteration assigned, but has tumor type
                        query.setAlterations(new ArrayList<>(AlterationUtils.getAllAlterations(requestQuery.getReferenceGenome(), query.getGene())));
                    }
                }
                query.setLevelOfEvidences(levelOfEvidences == null ? null : new ArrayList<>(levelOfEvidences));
                Set<Evidence> relevantEvidences = new HashSet<>();
                if (query.getExactMatchedAlteration() != null) {
                    relevantEvidences = getRelevantEvidences(query.getQuery(), query.getExactMatchedAlteration(), evidenceTypes, levelOfEvidences, AlterationUtils.getRelevantAlterations(requestQuery.getReferenceGenome(), query.getExactMatchedAlteration()), query.getAlleles(), geneQueryOnly);
                } else {
                    relevantEvidences = getEvidence(requestQuery.getReferenceGenome(), query, evidenceTypes, levelOfEvidences);
                }
                query = assignEvidence(relevantEvidences,
                    Collections.singletonList(query), highestLevelOnly, query.getQuery().isGermline()).iterator().next();

                Set<Evidence> updatedEvidences = new HashSet<>();
                final List<LevelOfEvidence> allowedLevels = query.getLevelOfEvidences();
                final List<TumorType> upwardTumorTypes = query.getOncoTreeTypes();
                TumorForm tumorForm = TumorTypeUtils.checkTumorForm(new HashSet<>(upwardTumorTypes));
                for (Evidence evidence : query.getEvidences()) {
                    if (evidence.getLevelOfEvidence() != null && EvidenceTypeUtils.getTreatmentEvidenceTypes().contains(evidence.getEvidenceType()) && tumorForm != null) {
                        boolean disjoint = Collections.disjoint(TumorTypeUtils.findEvidenceRelevantCancerTypes(evidence), query.getExactMatchedTumorType() == null ? upwardTumorTypes : Collections.singleton(query.getExactMatchedTumorType()));
                        if (disjoint) {
                            Evidence propagatedLevel = getPropagateEvidence(allowedLevels, evidence, tumorForm);
                            if (propagatedLevel != null) {
                                updatedEvidences.add(propagatedLevel);
                            }
                        } else {
                            updatedEvidences.add(new Evidence(evidence, evidence.getId()));
                        }
                    } else {
                        updatedEvidences.add(new Evidence(evidence, evidence.getId()));
                    }
                }

                if (!StringUtils.isEmpty(requestQuery.getHugoSymbol()) || query.getGene() != null) {
                    String hugoSymbol = StringUtils.isEmpty(requestQuery.getHugoSymbol()) ? query.getGene().getHugoSymbol() : requestQuery.getHugoSymbol();
                    for (Evidence evidence : updatedEvidences) {
                        evidence.setDescription(
                            CplUtils.annotate(
                                evidence.getDescription(),
                                hugoSymbol,
                                evidence.getAlterations().stream().map(alteration -> alteration.getName()).collect(joining(", ")),
                                TumorTypeUtils.getTumorTypesNameWithExclusion(evidence.getCancerTypes(), evidence.getExcludedCancerTypes()),
                                null,
                                evidence.getGene(),
                                null
                            )
                        );
                    }
                }
                query.setEvidences(new ArrayList<>(StringUtils.isEmpty(query.getQuery().getTumorType()) ? updatedEvidences : keepHighestLevelForSameTreatments(updatedEvidences, requestQuery.getReferenceGenome(), query.getExactMatchedAlteration())));
                evidenceQueries.add(query);
            }
        }

        return evidenceQueries;
    }

    private static List<EvidenceQueryRes> assignEvidence(Set<Evidence> evidences, List<EvidenceQueryRes> evidenceQueries,
                                                         Boolean highestLevelOnly, Boolean germline) {
        for (EvidenceQueryRes query : evidenceQueries) {
            Set<Evidence> filteredEvidences = new HashSet<>();
            if (germline == null) {
                filteredEvidences = new HashSet<>(evidences);
            } else {
                filteredEvidences = evidences.stream().filter(evidence -> evidence.getForGermline().equals(germline)).collect(Collectors.toSet());
            }

            if (highestLevelOnly) {
                List<Evidence> filteredHighestEvidences = new ArrayList<>();

                // Get highest sensitive evidences
                Set<Evidence> sensitiveEvidences = EvidenceUtils.getSensitiveEvidences(filteredEvidences);
                filteredHighestEvidences.addAll(EvidenceUtils.getOnlyHighestLevelEvidences(sensitiveEvidences, query.getQuery().getReferenceGenome(), query.getExactMatchedAlteration()));

                // Get highest resistance evidences
                Set<Evidence> resistanceEvidences = EvidenceUtils.getResistanceEvidences(filteredEvidences);
                filteredHighestEvidences.addAll(EvidenceUtils.getOnlyHighestLevelEvidences(resistanceEvidences, query.getQuery().getReferenceGenome(), query.getExactMatchedAlteration()));


                // Also include all non-treatment evidences
                for (Evidence evidence : filteredEvidences) {
                    if (!sensitiveEvidences.contains(evidence) && !resistanceEvidences.contains(evidence)) {
                        filteredHighestEvidences.add(evidence);
                    }
                }

                query.setEvidences(filteredHighestEvidences);
            } else {
                query.setEvidences(new ArrayList<>(filteredEvidences));
            }
            CustomizeComparator.sortEvidenceBasedOnPriority(query.getEvidences(), LevelUtils.getIndexedTherapeuticLevels());
            if (query.getGene() != null && query.getGene().getHugoSymbol().equals("KIT")) {
                CustomizeComparator.sortKitTreatmentByEvidence(query.getEvidences());
            }
        }
        return evidenceQueries;
    }

    public static void annotateEvidence(Evidence evidence, ReferenceGenome referenceGenome) throws ParserConfigurationException {
        // If evidence does not have gene info, we can not help with anything here.
        if (evidence.getGene() == null) {
            return;
        }

        // If the gene does not match with any one in our database, we can not help with anything here.
        Gene gene = GeneUtils.getGene(evidence.getGene().getEntrezGeneId(), evidence.getGene().getHugoSymbol());
        if (gene == null) {
            return;
        }

        evidence.setGene(gene);
        List<Alteration> parsedAlterations = new ArrayList<>();
        if (evidence.getAlterations() != null && !evidence.getAlterations().isEmpty()) {
            AlterationType type = AlterationType.MUTATION;
            Set<Alteration> alterations = new HashSet<Alteration>();
            AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
            evidence.getAlterations().stream().forEach(alteration -> parsedAlterations.addAll(AlterationUtils.parseMutationString(alteration.getAlteration(), ",")));
            for (Alteration alt : parsedAlterations) {
                String proteinChange = alt.getAlteration();
                String displayName = alt.getName();
                Alteration alteration = alterationBo.findAlterationFromDao(gene, type, referenceGenome, proteinChange, displayName);
                if (alteration == null) {
                    alteration = new Alteration();
                    alteration.setGene(gene);
                    alteration.setAlterationType(type);
                    alteration.setAlteration(proteinChange);
                    alteration.setName(displayName);
                    alteration.setReferenceGenomes(alt.getReferenceGenomes());
                    AlterationUtils.annotateAlteration(alteration, proteinChange);
                    alterationBo.save(alteration);
                } else if (!alteration.getReferenceGenomes().equals(alt.getReferenceGenomes())) {
                    alteration.setReferenceGenomes(alt.getReferenceGenomes());
                    alterationBo.update(alteration);
                }
                alterations.add(alteration);
            }
            evidence.setAlterations(alterations);
        }

        Set<Article> articles = evidence.getArticles();

        if (articles != null && !articles.isEmpty()) {
            ArticleBo articleBo = ApplicationContextSingleton.getArticleBo();
            Set<Article> annotatedArticles = new HashSet<>();
            Set<String> articlesToBeAdded = new HashSet<>();
            for (Article article : articles) {
                String tempPMID = article.getPmid();
                if (tempPMID == null) {
                    Article tempAT = articleBo.findArticleByAbstract(article.getAbstractContent());
                    if (tempAT == null) {
                        articleBo.save(article);
                        annotatedArticles.add(article);
                    } else {
                        annotatedArticles.add(tempAT);
                    }
                } else {
                    Article tempAT = articleBo.findArticleByPmid(tempPMID);
                    if (tempAT == null) {
                        articlesToBeAdded.add(tempPMID);
                    } else {
                        annotatedArticles.add(tempAT);
                    }
                }
            }

            if (!articlesToBeAdded.isEmpty()) {
                for (Article article : NcbiEUtils.readPubmedArticles(articlesToBeAdded)) {
                    articleBo.save(article);
                    annotatedArticles.add(article);
                }
            }

            evidence.setArticles(annotatedArticles);
        }
    }

    /**
     * @param evidences
     * @param isDesc    default is false
     * @return
     */
    public static List<Evidence> sortTumorTypeEvidenceBasedNumOfAlts(List<Evidence> evidences, Boolean isDesc) {
        // Default multiplier for the sorting
        int flag = 1;
        if (evidences == null) {
            return new ArrayList<>();
        }
        if (isDesc) {
            flag = -1;
        }

        final int multiplier = flag;
        final Map<Evidence, Integer> originalIndices = new HashedMap();
        for (int i = 0; i < evidences.size(); i++) {
            originalIndices.put(evidences.get(i), i);
        }

        // Sort all tumor type summaries, the more specific tumor type summary will be picked.
        Collections.sort(evidences, new Comparator<Evidence>() {
            public int compare(Evidence x, Evidence y) {
                if (x.getAlterations() == null) {
                    return 1;
                }
                if (y.getAlterations() == null) {
                    return -1;
                }
                Integer result = x.getAlterations().size() - y.getAlterations().size();
                if (result.equals(0)) {
                    return originalIndices.get(x) - originalIndices.get(y);
                }
                return multiplier * result;
            }
        });
        return evidences;
    }

    public static List<Evidence> getAllEvidencesByAlterationsGenes(Collection<Alteration> alterations) {
        Set<Gene> genes = new HashSet<>();
        List<Evidence> evidences = new ArrayList<>();
        for (Alteration alteration : alterations) {
            if (alteration != null) genes.add(alteration.getGene());
        }
        if (genes.size() == 1) {
            evidences.addAll(CacheUtils.getEvidences(genes.iterator().next()));
        } else {
            for (Gene gene : genes) {
                evidences.addAll(CacheUtils.getEvidences(gene));
            }
        }
        return evidences;
    }
}
