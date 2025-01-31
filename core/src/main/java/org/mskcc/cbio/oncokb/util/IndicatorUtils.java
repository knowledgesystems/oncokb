package org.mskcc.cbio.oncokb.util;

import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.apiModels.Citations;
import org.mskcc.cbio.oncokb.apiModels.Implication;
import org.mskcc.cbio.oncokb.apiModels.MutationEffectResp;
import org.mskcc.cbio.oncokb.model.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mskcc.cbio.oncokb.util.LevelUtils.getTherapeuticLevelsWithPriorityLIstIterator;
import static org.mskcc.cbio.oncokb.util.SummaryUtils.allelesToStr;
import static org.mskcc.cbio.oncokb.util.SummaryUtils.getVUSSummary;

/**
 * Created by hongxinzhang on 4/5/16.
 */
public class IndicatorUtils {
    public static IndicatorQueryResp processQuery(Query query,
                                                  Set<LevelOfEvidence> levels, Boolean highestLevelOnly,
                                                  Set<EvidenceType> evidenceTypes, Boolean geneQueryOnly) {
        highestLevelOnly = highestLevelOnly == null ? false : highestLevelOnly;

        levels = levels == null ? LevelUtils.getPublicLevels() :
            new HashSet<>(CollectionUtils.intersection(levels, LevelUtils.getPublicLevels()));

        Set<EvidenceType> selectedTreatmentEvidence = new HashSet<>();
        if (evidenceTypes == null || evidenceTypes.isEmpty()) {
            evidenceTypes = new HashSet<>(EvidenceTypeUtils.getAllEvidenceTypes(query.isGermline()));
            selectedTreatmentEvidence = EvidenceTypeUtils.getTreatmentEvidenceTypes();
        } else {
            selectedTreatmentEvidence = Sets.intersection(evidenceTypes, EvidenceTypeUtils.getTreatmentEvidenceTypes());
        }

        boolean hasTreatmentEvidence = !selectedTreatmentEvidence.isEmpty();
        boolean hasDiagnosticImplicationEvidence = evidenceTypes.contains(EvidenceType.DIAGNOSTIC_IMPLICATION);
        boolean hasPrognosticImplicationEvidence = evidenceTypes.contains(EvidenceType.PROGNOSTIC_IMPLICATION);
        boolean hasOncogenicEvidence = evidenceTypes.contains(EvidenceType.ONCOGENIC);
        boolean hasMutationEffectEvidence = evidenceTypes.contains(EvidenceType.MUTATION_EFFECT);

        IndicatorQueryResp indicatorQuery = new IndicatorQueryResp();
        indicatorQuery.setQuery(query);

        Gene gene = null;
        List<Alteration> relevantAlterations = new ArrayList<>();

        Set<Evidence> allQueryRelatedEvidences = new HashSet<>();

        // Queried alteration
        Alteration alteration;

        if (query == null) {
            return indicatorQuery;
        }

        query.enrich();

        // Temporary forward previous production annotation
        if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(query.getAlteration()) && query.getAlteration().equals("EGFRvIII")) {
            query.setAlteration("vIII");
        }


        // For intragenic annotation, convert it to structural variant and mark as Deletion
        if (StringUtils.containsIgnoreCase(query.getAlteration(), "intragenic")) {
            query.setAlterationType(AlterationType.STRUCTURAL_VARIANT.name());
            query.setSvType(StructuralVariantType.DELETION);
            query.setAlteration("");
        }


        Boolean isStructuralVariantEvent = false;
        // Deal with fusion without primary gene, and this is only for legacy fusion event
        // The latest fusion event has been integrated with alteration type. Please see next if-else condition
        // for more info.
        // TODO: support entrezGeneId fusion
        AlterationType alterationType = AlterationType.getByName(query.getAlterationType());
        Map<String, Object> fusionGeneAltsMap = new HashMap<>();
        if (query.getHugoSymbol() != null
            && alterationType != null &&
            alterationType.equals(AlterationType.FUSION)) {
            isStructuralVariantEvent = true;
            fusionGeneAltsMap = findFusionGeneAndRelevantAlts(query);

            // Dup: For single gene deletion event. We should map to Deletion instead of Truncating Mutation when Deletion has been curated
            if (query.getSvType() != null && query.getSvType().equals(StructuralVariantType.DELETION)) {
                Set<String> queryFusionGenes = (Set<String>) fusionGeneAltsMap.get("queryFusionGenes");
                if (queryFusionGenes.size() == 1) {
                    Gene queryFusionGene = GeneUtils.getGeneByHugoSymbol(queryFusionGenes.iterator().next());
                    if (queryFusionGene != null) {
                        Alteration deletion = AlterationUtils.findAlteration(queryFusionGene, query.getReferenceGenome(), "Deletion");
                        if (deletion != null) {
                            query.setAlteration("deletion");
                            query.setConsequence("feature_truncation");
                            fusionGeneAltsMap = findFusionGeneAndRelevantAlts(query);
                        }
                    }
                }
            }

            gene = (Gene) fusionGeneAltsMap.get("pickedGene");
            relevantAlterations = (List<Alteration>) fusionGeneAltsMap.get("relevantAlts");
            Set<Gene> allGenes = (LinkedHashSet<Gene>) fusionGeneAltsMap.get("allGenes");
        } else if (alterationType != null && alterationType.equals(AlterationType.STRUCTURAL_VARIANT)) {
            isStructuralVariantEvent = true;
            VariantConsequence variantConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(query.getConsequence());
            Boolean isFunctionalFusion = variantConsequence != null && variantConsequence.getTerm().equals("fusion");

            if (isFunctionalFusion || !com.mysql.jdbc.StringUtils.isNullOrEmpty(query.getAlteration())) {
                fusionGeneAltsMap = findFusionGeneAndRelevantAlts(query);
                gene = (Gene) fusionGeneAltsMap.get("pickedGene");
                relevantAlterations = (List<Alteration>) fusionGeneAltsMap.get("relevantAlts");
            } else {
                query.setAlteration("truncating mutation");
                query.setConsequence("feature_truncation");

                fusionGeneAltsMap = findFusionGeneAndRelevantAlts(query);

                // For single gene deletion event. We should map to Deletion instead of Truncating Mutation when Deletion has been curated
                if (query.getSvType() != null && query.getSvType().equals(StructuralVariantType.DELETION)) {
                    Set<String> queryFusionGenes = (Set<String>) fusionGeneAltsMap.get("queryFusionGenes");
                    if (queryFusionGenes.size() == 1) {
                        Gene queryFusionGene = GeneUtils.getGeneByHugoSymbol(queryFusionGenes.iterator().next());
                        if (queryFusionGene != null) {
                            Alteration deletion = AlterationUtils.findAlteration(queryFusionGene, query.getReferenceGenome(), "Deletion");
                            if (deletion != null) {
                                query.setAlteration("deletion");
                                fusionGeneAltsMap = findFusionGeneAndRelevantAlts(query);
                            }
                        }
                    }
                }

                gene = (Gene) fusionGeneAltsMap.get("pickedGene");
                fusionGeneAltsMap = new HashMap<>();
                // As long as this is a structural variant event, we need to attach the Truncating Mutation
                Alteration truncatingMutations = AlterationUtils.getTruncatingMutations(gene);
                if (truncatingMutations != null && !relevantAlterations.contains(truncatingMutations)) {
                    relevantAlterations.add(truncatingMutations);
                    List<Alteration> truncMutRelevants = AlterationUtils.getRelevantAlterations(query.getReferenceGenome(), truncatingMutations);
                    for (Alteration alt : truncMutRelevants) {
                        if (!relevantAlterations.contains(alt)) {
                            relevantAlterations.add(alt);
                        }
                    }
                }
            }
        } else {
            gene = GeneUtils.getGene(query.getEntrezGeneId(), query.getHugoSymbol());
            if (gene != null) {
                Alteration alt = AlterationUtils.getAlteration(gene.getHugoSymbol(), query.getAlteration(),
                    null, query.getConsequence(), query.getProteinStart(), query.getProteinEnd(), query.getReferenceGenome());

                relevantAlterations = AlterationUtils.getRelevantAlterations(query.getReferenceGenome(), alt);
            }
        }

        // For fusions
        if (fusionGeneAltsMap.containsKey("hasRelevantAltsGenes")) {
            // If there are more than two genes have matches we need to compare the highest level, then oncogenicity
            TreeSet<IndicatorQueryResp> result = new TreeSet<>(new IndicatorQueryRespComp());
            for (Gene tmpGene : (List<Gene>) fusionGeneAltsMap.get("hasRelevantAltsGenes")) {
                Query tmpQuery = new Query(query.getId(), query.getReferenceGenome(), tmpGene.getEntrezGeneId(),
                    tmpGene.getHugoSymbol(), query.getAlteration(), null, query.getSvType(),
                    query.getTumorType(), query.getConsequence(), query.getProteinStart(),
                    query.getProteinEnd(), query.getHgvs(), query.isGermline(), query.getAlleleState(), query.getPathogenicity());
                result.add(IndicatorUtils.processQuery(tmpQuery, levels, highestLevelOnly, evidenceTypes, geneQueryOnly));
            }
            return result.iterator().next();
        }

        if (gene != null) {
            // we replace hugo symbol with matched gene when queries hugo symbol is not available or when it's the structural variant
            if (StringUtils.isEmpty(query.getHugoSymbol()) || isStructuralVariantEvent) {
                query.setHugoSymbol(gene.getHugoSymbol());
            }
            query.setEntrezGeneId(gene.getEntrezGeneId());

            // Gene exist should only be set to true if entrezGeneId is bigger than 0
            indicatorQuery.setGeneExist(gene.getEntrezGeneId() > 0);

            // Gene summary
            if (evidenceTypes.contains(EvidenceType.GENE_SUMMARY) || evidenceTypes.contains(EvidenceType.GENE_SUMMARY)) {
                indicatorQuery.setGeneSummary(SummaryUtils.geneSummary(gene, query.getHugoSymbol(), query.isGermline()));
                allQueryRelatedEvidences.addAll(EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, Collections.singleton(EvidenceType.GENE_SUMMARY)));
            }

            alteration = AlterationUtils.getAlteration(gene.getHugoSymbol(), query.getAlteration(),
                null, query.getConsequence(), query.getProteinStart(), query.getProteinEnd(), query.getReferenceGenome());

            List<Alteration> nonVUSRelevantAlts = AlterationUtils.excludeVUS(relevantAlterations);
            Map<String, LevelOfEvidence> highestLevels = new HashMap<>();
            TumorType matchedTumorType = ApplicationContextSingleton.getTumorTypeBo().getByName(query.getTumorType());
            List<TumorType> relevantUpwardTumorTypes = new ArrayList<>();
            List<TumorType> relevantDownwardTumorTypes = new ArrayList<>();

            Alteration matchedAlt = AlterationUtils.findExactlyMatchedAlteration(query.getReferenceGenome(), alteration, AlterationUtils.getAllAlterations(query.getReferenceGenome(), gene));

            if (matchedAlt == null && isStructuralVariantEvent) {
                matchedAlt = AlterationUtils.getRevertFusions(query.getReferenceGenome(), alteration);
            }

            indicatorQuery.setVariantExist(matchedAlt != null);

            if(matchedAlt == null) {
                matchedAlt = alteration;
            }

            List<Alteration> alleles = AlterationUtils.getAlleleAlterations(query.getReferenceGenome(), matchedAlt);

            // This is for tumor type level info. We do not want to map the alternative alleles on tumor type level
            List<Alteration> relevantAlterationsWithoutAlternativeAlleles = new ArrayList<>(relevantAlterations);
            AlterationUtils.removeAlternativeAllele(query.getReferenceGenome(), matchedAlt, relevantAlterationsWithoutAlternativeAlleles);

            indicatorQuery.setHotspot(HotspotUtils.isHotspot(matchedAlt));

            if (query.getTumorType() != null) {
                relevantUpwardTumorTypes = TumorTypeUtils.findRelevantTumorTypes(query.getTumorType());
            }

            relevantDownwardTumorTypes = TumorTypeUtils.findRelevantTumorTypes(query.getTumorType(), null, RelevantTumorTypeDirection.DOWNWARD);

            indicatorQuery.setVUS(isVUS(matchedAlt));

            if (indicatorQuery.getVUS()) {
                List<Evidence> vusEvidences = EvidenceUtils.getEvidence(Collections.singletonList(matchedAlt), Collections.singleton(EvidenceType.VUS), null);
                if (vusEvidences != null) {
                    allQueryRelatedEvidences.addAll(vusEvidences);
                }
            }

            if (alleles == null || alleles.size() == 0) {
                indicatorQuery.setAlleleExist(false);
            } else {
                indicatorQuery.setAlleleExist(true);
            }

            Set<Evidence> treatmentEvidences = new HashSet<>();

            if (nonVUSRelevantAlts.size() > 0) {

                // Add germline info
                if(query.isGermline()){
                    indicatorQuery.setGermline(getGermlineVariantInfo(matchedAlt, query.getAlleleState(), relevantAlterations));
                }else{
                    if (hasOncogenicEvidence) {
                        IndicatorQueryOncogenicity indicatorQueryOncogenicity = getOncogenicity(matchedAlt, alleles, nonVUSRelevantAlts);

                        if (indicatorQueryOncogenicity.getOncogenicityEvidence() != null) {
                            allQueryRelatedEvidences.add(indicatorQueryOncogenicity.getOncogenicityEvidence());
                        }

                        // Only set oncogenicity if no previous data assigned.
                        if (indicatorQuery.getOncogenic() == null && indicatorQueryOncogenicity.getOncogenicity() != null) {
                            indicatorQuery.setOncogenic(indicatorQueryOncogenicity.getOncogenicity().getOncogenic());
                        }
                    }

                    if (hasMutationEffectEvidence) {
                        IndicatorQueryMutationEffect indicatorQueryMutationEffect = getMutationEffect(matchedAlt, alleles, nonVUSRelevantAlts);

                        if (indicatorQueryMutationEffect.getMutationEffectEvidence() != null) {
                            allQueryRelatedEvidences.add(indicatorQueryMutationEffect.getMutationEffectEvidence());
                        }

                        // Only set mutation effect if no previous data assigned.
                        if (indicatorQuery.getMutationEffect() == null && indicatorQueryMutationEffect.getMutationEffect() != null) {
                            MutationEffectResp mutationEffectResp = new MutationEffectResp();
                            mutationEffectResp.setKnownEffect(indicatorQueryMutationEffect.getMutationEffect().getMutationEffect());
                            if (indicatorQueryMutationEffect.getMutationEffectEvidence() != null && StringUtils.isNotEmpty(indicatorQueryMutationEffect.getMutationEffectEvidence().getDescription())) {
                                mutationEffectResp.setDescription(CplUtils.annotate(
                                    indicatorQueryMutationEffect.getMutationEffectEvidence().getDescription(),
                                    query.getHugoSymbol(),
                                    query.getAlteration(),
                                    query.getTumorType(),
                                    query.getReferenceGenome(),
                                    gene,
                                    matchedTumorType
                                ));
                                mutationEffectResp.setCitations(MainUtils.getCitationsByEvidence(indicatorQueryMutationEffect.getMutationEffectEvidence()));
                            }
                            indicatorQuery.setMutationEffect(mutationEffectResp);
                        }
                    }
                }

                // Set implications
                if (hasTreatmentEvidence) {
                    if (StringUtils.isEmpty(query.getTumorType())) {
                        treatmentEvidences = EvidenceUtils.getRelevantEvidences(query, matchedAlt,
                            selectedTreatmentEvidence, levels, relevantAlterationsWithoutAlternativeAlleles, alleles, geneQueryOnly);
                    } else {
                        treatmentEvidences = EvidenceUtils.keepHighestLevelForSameTreatments(
                            EvidenceUtils.getRelevantEvidences(query, matchedAlt,
                                selectedTreatmentEvidence, levels, relevantAlterationsWithoutAlternativeAlleles, alleles, geneQueryOnly), query.getReferenceGenome(), matchedAlt);
                    }
                }

                if (gene != null && hasDiagnosticImplicationEvidence) {
                    List<Implication> implications = new ArrayList<>();
                    Set<LevelOfEvidence> levelOfEvidences = new HashSet<>();
                    levelOfEvidences.add(LevelOfEvidence.LEVEL_Dx1);
                    levelOfEvidences.add(LevelOfEvidence.LEVEL_Dx2);
                    levelOfEvidences.add(LevelOfEvidence.LEVEL_Dx3);
                    implications.addAll(getImplications(gene, matchedAlt, alleles, relevantAlterationsWithoutAlternativeAlleles, EvidenceType.DIAGNOSTIC_IMPLICATION, matchedTumorType, StringUtils.isEmpty(query.getTumorType()) ? null : relevantUpwardTumorTypes, query.getHugoSymbol(), levelOfEvidences, geneQueryOnly));
                    indicatorQuery.setDiagnosticImplications(implications);
                    if (indicatorQuery.getDiagnosticImplications().size() > 0) {
                        indicatorQuery.setHighestDiagnosticImplicationLevel(LevelUtils.getHighestDiagnosticImplicationLevel(indicatorQuery.getDiagnosticImplications().stream().map(implication -> implication.getLevelOfEvidence()).collect(Collectors.toSet())));
                    }
                }

                if (gene != null && hasPrognosticImplicationEvidence) {
                    indicatorQuery.setPrognosticImplications(getImplications(gene, matchedAlt, alleles, relevantAlterationsWithoutAlternativeAlleles, EvidenceType.PROGNOSTIC_IMPLICATION, matchedTumorType, StringUtils.isEmpty(query.getTumorType()) ? null : relevantUpwardTumorTypes, query.getHugoSymbol(), null, geneQueryOnly));
                    if (indicatorQuery.getPrognosticImplications().size() > 0) {
                        indicatorQuery.setHighestPrognosticImplicationLevel(LevelUtils.getHighestPrognosticImplicationLevel(indicatorQuery.getPrognosticImplications().stream().map(implication -> implication.getLevelOfEvidence()).collect(Collectors.toSet())));
                    }
                }
            }

            // If the variant does not exist and Pathogenicity is P/LP, then we will
            // return the genomic indicators associated with Pathogenic Variants
            if (query.isGermline() && !indicatorQuery.getVariantExist() && query.getPathogenicity() != null) {
                if (query.getPathogenicity().equals(Pathogenicity.YES) || query.getPathogenicity().equals(Pathogenicity.LIKELY)) {
                    GermlineVariant germlineVariant = new GermlineVariant();
                    List<Evidence> genomicIndicatorEvis = EvidenceUtils.getGenomicIndicatorAssociatedWithPathogenicVariants(gene, query.getAlleleState());
                    germlineVariant.setGenomicIndicators(genomicIndicatorEvis.stream().map(Evidence::getName).collect(Collectors.toList()));
                    indicatorQuery.setGermline(germlineVariant);
                }
            }

            // Set hotspot oncogenicity to Likely Oncogenic
            if (indicatorQuery.getHotspot() && !MainUtils.isValidHotspotOncogenicity(Oncogenicity.getByEffect(indicatorQuery.getOncogenic()))) {
                indicatorQuery.setOncogenic(Oncogenicity.LIKELY.getOncogenic());

                // Check whether the gene has Oncogenic Mutations annotated
                List<Alteration> oncogenicMutations = new ArrayList<>(AlterationUtils.findOncogenicMutations(AlterationUtils.getAllAlterations(query.getReferenceGenome(), gene)));
                if (!oncogenicMutations.isEmpty()) {
                    relevantAlterations.addAll(oncogenicMutations);
                    if (hasTreatmentEvidence) {
                        if (StringUtils.isEmpty(query.getTumorType())) {
                            treatmentEvidences.addAll(EvidenceUtils.getEvidence(oncogenicMutations, selectedTreatmentEvidence, levels));
                        } else {
                            treatmentEvidences.addAll(EvidenceUtils.keepHighestLevelForSameTreatments(
                                EvidenceUtils.convertEvidenceLevel(
                                    EvidenceUtils.getEvidence(oncogenicMutations,
                                        selectedTreatmentEvidence, levels), new HashSet<>(relevantUpwardTumorTypes)), query.getReferenceGenome(), matchedAlt));
                        }
                    }
                }
            }

            if (hasTreatmentEvidence && treatmentEvidences != null && !treatmentEvidences.isEmpty()) {
                if (highestLevelOnly) {
                    Set<Evidence> filteredEvis = new HashSet<>();
                    // Get highest sensitive evidences
                    Set<Evidence> sensitiveEvidences = EvidenceUtils.getSensitiveEvidences(treatmentEvidences);
                    filteredEvis.addAll(EvidenceUtils.getOnlySignificantLevelsEvidences(sensitiveEvidences));

                    // Get highest resistance evidences
                    Set<Evidence> resistanceEvidences = EvidenceUtils.getResistanceEvidences(treatmentEvidences);
                    filteredEvis.addAll(EvidenceUtils.getOnlyHighestLevelEvidences(resistanceEvidences, query.getReferenceGenome(), matchedAlt));

                    treatmentEvidences = filteredEvis;
                }
                if (!treatmentEvidences.isEmpty()) {
                    List<IndicatorQueryTreatment> treatments = getIndicatorQueryTreatments(treatmentEvidences, query.getHugoSymbol(), StringUtils.isEmpty(query.getTumorType()) ? false : true);

                    // Make sure the treatment in KIT is always sorted.
                    if (gene.getHugoSymbol().equals("KIT")) {
                        CustomizeComparator.sortKitTreatment(treatments);
                    }
                    for (IndicatorQueryTreatment treatment : treatments) {
                        treatment.setDescription(CplUtils.annotate(
                            treatment.getDescription(),
                            query.getHugoSymbol(),
                            query.getAlteration(),
                            query.getTumorType(),
                            query.getReferenceGenome(),
                            gene,
                            matchedTumorType
                        ));
                    }
                    indicatorQuery.setTreatments(treatments);
                    highestLevels = findHighestLevel(new HashSet<>(treatments));
                    indicatorQuery.setHighestSensitiveLevel(highestLevels.get("sensitive"));
                    indicatorQuery.setHighestResistanceLevel(highestLevels.get("resistant"));
                    indicatorQuery.setHighestFdaLevel(LevelUtils.getHighestFdaLevel(treatments.stream().filter(t -> t.getFdaLevel() != null).map(t -> t.getFdaLevel()).collect(Collectors.toSet())));
                    allQueryRelatedEvidences.addAll(treatmentEvidences);
                }
            }

            // Tumor type summary
            if (evidenceTypes.contains(EvidenceType.TUMOR_TYPE_SUMMARY) && StringUtils.isNotEmpty(query.getTumorType())) {
                Map<String, Object> tumorTypeSummary = SummaryUtils.tumorTypeSummary(EvidenceType.TUMOR_TYPE_SUMMARY, gene, query, matchedAlt,
                    new ArrayList<>(relevantAlterationsWithoutAlternativeAlleles),
                    matchedTumorType,
                    relevantUpwardTumorTypes);
                if (tumorTypeSummary != null) {
                    indicatorQuery.setTumorTypeSummary((String) tumorTypeSummary.get("summary"));
                    Date lateEdit = tumorTypeSummary.get("lastEdit") == null ? null : (Date) tumorTypeSummary.get("lastEdit");
                    if (lateEdit != null) {
                        Evidence lastEditTTSummary = new Evidence();
                        lastEditTTSummary.setLastEdit(lateEdit);
                        allQueryRelatedEvidences.add(lastEditTTSummary);
                    }
                }
            }

            // Mutation summary
            if (evidenceTypes.contains(EvidenceType.MUTATION_SUMMARY) && StringUtils.isNotEmpty(matchedAlt.getAlteration())) {
                indicatorQuery.setVariantSummary(SummaryUtils.variantSummary(gene, matchedAlt,
                    new ArrayList<>(relevantAlterations), query));
            }

            // Diagnostic summary
            if (evidenceTypes.contains(EvidenceType.DIAGNOSTIC_SUMMARY) && matchedTumorType != null) {
                Map<String, Object> diagnosticSummary = new HashMap<>();
                if (indicatorQuery.getDiagnosticImplications().stream().filter(implication -> implication.getLevelOfEvidence() != null && implication.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_Dx1)).findAny().isPresent()) {
                    diagnosticSummary = SummaryUtils.tumorTypeSummary(EvidenceType.DIAGNOSTIC_SUMMARY, gene, query, matchedAlt,
                        new ArrayList<>(relevantAlterationsWithoutAlternativeAlleles),
                        matchedTumorType,
                        relevantDownwardTumorTypes);
                }
                if (StringUtils.isEmpty((String) diagnosticSummary.get("summary"))) {
                    diagnosticSummary = SummaryUtils.tumorTypeSummary(EvidenceType.DIAGNOSTIC_SUMMARY, gene, query, matchedAlt,
                        new ArrayList<>(relevantAlterationsWithoutAlternativeAlleles),
                        matchedTumorType,
                        relevantUpwardTumorTypes);
                }

                if(StringUtils.isEmpty((String) diagnosticSummary.get("summary"))) {
                    indicatorQuery.setDiagnosticSummary("");
                } else {
                    indicatorQuery.setDiagnosticSummary((String) diagnosticSummary.get("summary"));
                }
                Date lateEdit = diagnosticSummary.get("lastEdit") == null ? null : (Date) diagnosticSummary.get("lastEdit");
                if (lateEdit != null) {
                    Evidence lastEditTTSummary = new Evidence();
                    lastEditTTSummary.setLastEdit(lateEdit);
                    allQueryRelatedEvidences.add(lastEditTTSummary);
                }
            }

            // Prognostic summary
            if (evidenceTypes.contains(EvidenceType.PROGNOSTIC_SUMMARY) && matchedTumorType != null) {
                Map<String, Object> prognosticSummary = SummaryUtils.tumorTypeSummary(EvidenceType.PROGNOSTIC_SUMMARY, gene, query, matchedAlt,
                    new ArrayList<>(relevantAlterationsWithoutAlternativeAlleles),
                    matchedTumorType,
                    relevantUpwardTumorTypes);
                if (prognosticSummary != null) {
                    indicatorQuery.setPrognosticSummary((String) prognosticSummary.get("summary"));
                    Date lateEdit = prognosticSummary.get("lastEdit") == null ? null : (Date) prognosticSummary.get("lastEdit");
                    if (lateEdit != null) {
                        Evidence lastEditTTSummary = new Evidence();
                        lastEditTTSummary.setLastEdit(lateEdit);
                        allQueryRelatedEvidences.add(lastEditTTSummary);
                    }
                }
            }

            // This is special case for KRAS wildtype. May need to come up with a better plan for this.
            if (gene != null && (gene.getHugoSymbol().equals("KRAS") || gene.getHugoSymbol().equals("NRAS"))
                && query.getAlteration() != null
                && StringUtils.containsIgnoreCase(query.getAlteration(), "wildtype")) {
                if (relevantUpwardTumorTypes.contains(ApplicationContextSingleton.getTumorTypeBo().getByMainType("Colorectal Cancer"))) {
                    indicatorQuery.setGeneSummary("RAS (KRAS/NRAS) which is wildtype (not mutated) in this sample, encodes an upstream activator of the pro-oncogenic MAP- and PI3-kinase pathways and is mutated in approximately 40% of late stage colorectal cancers.");
                    indicatorQuery.setVariantSummary("The absence of a mutation in the RAS genes is clinically important because it expands approved treatments available to treat this tumor. RAS status in stage IV colorectal cancer influences patient responses to the anti-EGFR antibody therapies cetuximab and panitumumab.");
                    indicatorQuery.setTumorTypeSummary("These drugs are FDA-approved for the treatment of KRAS wildtype colorectal tumors together with chemotherapy or alone following progression through standard chemotherapy.");
                } else {
                    indicatorQuery.setVariantSummary("");
                    indicatorQuery.setTumorTypeSummary("");
                    indicatorQuery.setTreatments(new ArrayList<IndicatorQueryTreatment>());
                    indicatorQuery.setHighestResistanceLevel(null);
                    indicatorQuery.setHighestSensitiveLevel(null);
                }
            }
        } else {
            indicatorQuery.setGeneExist(false);
        }

        if(StringUtils.isEmpty(indicatorQuery.getOncogenic()) && StringUtils.isNotEmpty(query.getAlteration()) && query.getAlteration().trim().toLowerCase().startsWith(InferredMutation.ONCOGENIC_MUTATIONS.getVariant().toLowerCase())) {
            indicatorQuery.setOncogenic(Oncogenicity.YES.getOncogenic());
        }

        if (indicatorQuery.getMutationEffect() == null) {
            indicatorQuery.setMutationEffect(getDefaultMutationEffectResponse());
        }

        indicatorQuery.setDataVersion(MainUtils.getDataVersion());

        Date lastUpdate = getLatestDateFromEvidences(allQueryRelatedEvidences);
        indicatorQuery.setLastUpdate(lastUpdate == null ? MainUtils.getDataVersionDate() :
            new SimpleDateFormat("MM/dd/yyy").format(lastUpdate));

        // Give default oncogenicity if no data has been assigned.
        if (indicatorQuery.getOncogenic() == null) {
            indicatorQuery.setOncogenic(Oncogenicity.UNKNOWN.getOncogenic());
        }
        return indicatorQuery;
    }

    // This should used by only for delins range missense mutation
    private static Alteration pickMatchedAlteration(List<Alteration> alterations, Query originalQuery, Set<LevelOfEvidence> levels, Boolean highestLevelOnly, Set<EvidenceType> evidenceTypes) {
        if (alterations == null || alterations.size() == 0) {
            return null;
        }
        Map<Oncogenicity, List<Alteration>> groupedOncogenicities = new HashedMap();
        Map<LevelOfEvidence, List<Alteration>> groupedLevel = new HashedMap();
        for (Alteration alteration : alterations) {
            Query tmpQuery = new Query(null, originalQuery.getReferenceGenome(), alteration.getGene().getEntrezGeneId(),
                alteration.getGene().getHugoSymbol(), alteration.getAlteration(), null, null,
                originalQuery.getTumorType(), alteration.getConsequence().getTerm(), alteration.getProteinStart(),
                alteration.getProteinEnd(), null, originalQuery.isGermline(), originalQuery.getAlleleState(), originalQuery.getPathogenicity());

            // Add oncogenicity
            IndicatorQueryOncogenicity indicatorQueryOncogenicity = getOncogenicity(alteration, new ArrayList<>(), new ArrayList<>());
            Oncogenicity oncogenicity = indicatorQueryOncogenicity.getOncogenicity();
            if (!groupedOncogenicities.containsKey(oncogenicity)) {
                groupedOncogenicities.put(oncogenicity, new ArrayList<>());
            }
            groupedOncogenicities.get(oncogenicity).add(alteration);

        }
        Oncogenicity highestOncogenicity = MainUtils.findHighestOncogenicity(groupedOncogenicities.keySet());
        if (highestOncogenicity == null) {
            return alterations.iterator().next();
        }
        // when the oncogenicity is the same, then the therapeutic info will be the same as well.
        return groupedOncogenicities.get(highestOncogenicity).get(0);
    }

    private static void sortGermlineEvidenceByAlterationSize(List<Evidence> evidences){
        evidences.sort(new Comparator<Evidence>() {
            @Override
            public int compare(Evidence o1, Evidence o2) {
                return o1.getAlterations().size() - o2.getAlterations().size();
            }
        });
    }

    private static GermlineVariant getGermlineVariantInfo(Alteration matchedAlt, String alleleState, List<Alteration> relevantAlterations) {
        GermlineVariant germlineVariant = new GermlineVariant();

        // Get pathogenic info
        List<Evidence> pathogenicEvis = EvidenceUtils.getEvidence(Collections.singletonList(matchedAlt), Collections.singleton(EvidenceType.PATHOGENIC), null);
        if (pathogenicEvis.size() > 0) {
            sortGermlineEvidenceByAlterationSize(pathogenicEvis);
            Evidence pathogenicEvi = pathogenicEvis.iterator().next();
            germlineVariant.setPathogenic(pathogenicEvi.getKnownEffect());
            germlineVariant.setDescription(StringUtils.isEmpty(pathogenicEvi.getDescription()) ? "" : pathogenicEvi.getDescription());
        }

        // Get penetrance info
        List<Evidence> penetranceEvis = EvidenceUtils.getEvidence(Collections.singletonList(matchedAlt), Collections.singleton(EvidenceType.VARIANT_PENETRANCE), null);
        if (penetranceEvis.isEmpty()) {
            penetranceEvis.addAll(EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(matchedAlt.getGene(), Collections.singleton(EvidenceType.GENE_PENETRANCE)));
        } else {
            sortGermlineEvidenceByAlterationSize(penetranceEvis);
        }
        if (!penetranceEvis.isEmpty()) {
            Evidence penetranceEvi = penetranceEvis.iterator().next();
            germlineVariant.setPenetrance(penetranceEvi.getKnownEffect());
            germlineVariant.setPenetranceDescription(StringUtils.isEmpty(penetranceEvi.getDescription()) ? "" : penetranceEvi.getDescription());
        }

        // Get cancer risk info
        List<Evidence> cancerRiskEvis = EvidenceUtils.getEvidence(Collections.singletonList(matchedAlt), Collections.singleton(EvidenceType.VARIANT_CANCER_RISK), null);
        cancerRiskEvis.sort(Comparator.comparing(Evidence::getKnownEffect));

        if (cancerRiskEvis.size() > 0) {
            if (StringUtils.isNotEmpty(alleleState)) {
                cancerRiskEvis = cancerRiskEvis.stream().filter(evidence -> alleleState.toLowerCase().equals(evidence.getKnownEffect().toLowerCase())).collect(Collectors.toList());
            }
            germlineVariant.setCancerRisk(cancerRiskEvis.stream().map(evidence -> StringUtils.capitalize(evidence.getKnownEffect()) + " " + matchedAlt.getGene().getHugoSymbol() + " mutation carriers: " + evidence.getDescription()).collect(Collectors.joining("\n\n")));
        }

        // Get inheritance mechanism info
        List<Evidence> inheritanceMechanismEvis = EvidenceUtils.getEvidence(Collections.singletonList(matchedAlt), Collections.singleton(EvidenceType.VARIANT_INHERITANCE_MECHANISM), null);
        if (inheritanceMechanismEvis.isEmpty()) {
            inheritanceMechanismEvis.addAll(EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(matchedAlt.getGene(), Collections.singleton(EvidenceType.GENE_INHERITANCE_MECHANISM)));
        } else {
            sortGermlineEvidenceByAlterationSize(inheritanceMechanismEvis);
        }
        if (!inheritanceMechanismEvis.isEmpty()) {
            Evidence inheritanceMechanismEvi = inheritanceMechanismEvis.iterator().next();
            germlineVariant.setInheritanceMechanism(inheritanceMechanismEvi.getKnownEffect());
            germlineVariant.setInheritanceMechanismDescription(StringUtils.isEmpty(inheritanceMechanismEvi.getDescription()) ? "" : inheritanceMechanismEvi.getDescription());
        }

        // Get genomic indicator
        List<Alteration> alts = new ArrayList<>();
        alts.add(matchedAlt);
        alts.addAll(relevantAlterations);
        List<Evidence> genomicIndicatorEvis = EvidenceUtils.getGenomicIndicatorsByAlteration(alts, alleleState);
        germlineVariant.setGenomicIndicators(genomicIndicatorEvis.stream().map(Evidence::getName).collect(Collectors.toList()));
        return germlineVariant;
    }

    private static Set<Implication> getImplicationsFromEvidence(Evidence evidence, String queryHugoSymbol) {
        if (evidence == null) {
            return null;
        }
        Citations citations = MainUtils.getCitationsByEvidence(evidence);
        return evidence.getCancerTypes().stream().map(tumorType -> {
            Implication implication = new Implication();
            implication.setLevelOfEvidence(evidence.getLevelOfEvidence());
            implication.setAlterations(evidence.getAlterations().stream().map(alteration -> alteration.getName() == null ? alteration.getAlteration() : alteration.getAlteration()).collect(Collectors.toSet()));
            implication.setTumorType(new org.mskcc.cbio.oncokb.apiModels.TumorType(tumorType));
            String hugoSymbol = StringUtils.isEmpty(queryHugoSymbol) ? evidence.getGene().getHugoSymbol() : queryHugoSymbol;
            implication.setPmids(citations.getPmids());
            implication.setAbstracts(citations.getAbstracts());
            implication.setDescription(CplUtils.annotateGene(evidence.getDescription(), hugoSymbol));
            return implication;
        }).collect(Collectors.toSet());
    }

    private static MutationEffectResp getDefaultMutationEffectResponse() {
        MutationEffectResp mutationEffectResp = new MutationEffectResp();
        mutationEffectResp.setKnownEffect(MutationEffect.UNKNOWN.getMutationEffect());
        return mutationEffectResp;
    }

    private static List<Implication> getImplicationFromEvidence(List<Evidence> evidences, String queryHugoSymbol) {
        List<Implication> implications = new ArrayList<>();
        if (evidences == null) {
            return implications;
        }
        for (Evidence evidence : evidences) {
            implications.addAll(getImplicationsFromEvidence(evidence, queryHugoSymbol));
        }
        return implications;
    }

    private static List<Implication> getImplications(Gene gene, Alteration matchedAlt, List<Alteration> alternativeAlleles, List<Alteration> relevantAlterations, EvidenceType evidenceType, TumorType matchedTumorType, List<TumorType> tumorTypes, String queryHugoSymbol, Set<LevelOfEvidence> levelOfEvidences, Boolean geneQueryOnly) {
        List<Implication> implications = new ArrayList<>();

        if (matchedAlt != null && !StringUtils.isEmpty(matchedAlt.getAlteration())) {
            // Find alteration specific evidence
            List<Evidence> selfAltEvis = EvidenceUtils.getEvidence(Collections.singletonList(matchedAlt), Collections.singleton(evidenceType), matchedTumorType, tumorTypes, levelOfEvidences);
            if (selfAltEvis != null && selfAltEvis.size() > 0) {
                implications.addAll(getImplicationFromEvidence(selfAltEvis, queryHugoSymbol));
            }

            List<Alteration> listToBeRemoved = new ArrayList<>(alternativeAlleles);
            listToBeRemoved.add(matchedAlt);

            for (Alteration alt : AlterationUtils.removeAlterationsFromList(relevantAlterations, listToBeRemoved)) {
                List<Evidence> altEvis = EvidenceUtils.getEvidence(Collections.singletonList(alt), Collections.singleton(evidenceType), matchedTumorType, tumorTypes, levelOfEvidences);
                if (altEvis != null && altEvis.size() > 0) {
                    implications.addAll(getImplicationFromEvidence(altEvis, queryHugoSymbol));
                }
            }
        } else if (geneQueryOnly){
            Set<Evidence> geneEvisSet = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, Stream.of(evidenceType).collect(Collectors.toSet()));
            List<Evidence> geneEvis = new ArrayList<>();
            geneEvis.addAll(geneEvisSet);
            implications.addAll(getImplicationFromEvidence(geneEvis, queryHugoSymbol));
        }
        return filterImplication(implications);
    }


    public static List<Implication> filterImplication(List<Implication> implications) {
        return implications.stream().filter(implication -> implication.getLevelOfEvidence() != null)
            .map(UniqueImplication::new)
            .distinct()
            .map(UniqueImplication::unwrap)
            .collect(Collectors.toList());
    }

    public static IndicatorQueryOncogenicity getOncogenicity(Alteration alteration, List<Alteration> alternativeAllele, List<Alteration> relevantAlterations) {
        Oncogenicity oncogenicity = null;
        Evidence oncogenicityEvidence = null;

        // Find alteration specific oncogenicity
        oncogenicity = MainUtils.getCuratedAlterationOncogenicity(alteration);

        if(StringUtils.isNotEmpty(alteration.getAlteration()) && alteration.getAlteration().trim().toLowerCase().startsWith(InferredMutation.ONCOGENIC_MUTATIONS.getVariant().toLowerCase())) {
            oncogenicity = Oncogenicity.YES;
        }

        if (oncogenicity == null || oncogenicity.equals(Oncogenicity.UNKNOWN)) {
            // Find Oncogenicity from alternative alleles
            if (alternativeAllele.size() > 0) {
                oncogenicityEvidence = MainUtils.findHighestOncogenicEvidenceByEvidences(new HashSet<>(EvidenceUtils.getEvidence(new ArrayList<>(alternativeAllele), Collections.singleton(EvidenceType.ONCOGENIC), null)));
                if (oncogenicityEvidence != null) {
                    Oncogenicity tmpOncogenicity = MainUtils.setToAlleleOncogenicity(Oncogenicity.getByEffect(oncogenicityEvidence.getKnownEffect()));
                    if (tmpOncogenicity != null) {
                        oncogenicity = tmpOncogenicity;
                    }
                }
            }

            // If there is no oncogenic info available for this variant, find oncogenicity from relevant variants
            // This also includes inconclusive check cause if the best oncogenicity from alternative allele, we should continue find better one in other relevant alterations
            if (oncogenicity == null || oncogenicity.equals(Oncogenicity.UNKNOWN) || oncogenicity.equals(Oncogenicity.INCONCLUSIVE)) {
                List<Alteration> listToBeRemoved = new ArrayList<>(alternativeAllele);
                listToBeRemoved.add(alteration);

                oncogenicityEvidence = MainUtils.findHighestOncogenicEvidenceByEvidences(
                    new HashSet<>(EvidenceUtils.getEvidence(new ArrayList<>(AlterationUtils.removeAlterationsFromList(relevantAlterations, listToBeRemoved)), Collections.singleton(EvidenceType.ONCOGENIC), null))
                );
                if (oncogenicityEvidence != null) {
                    Oncogenicity tmpOncogenicity = Oncogenicity.getByEffect(oncogenicityEvidence.getKnownEffect());
                    if (tmpOncogenicity != null) {
                        oncogenicity = tmpOncogenicity;
                    }
                }
            }
        }

        if (oncogenicity == null) {
            oncogenicity = Oncogenicity.UNKNOWN;

            if (HotspotUtils.isHotspot(alteration)) {
                oncogenicity = Oncogenicity.LIKELY;
                oncogenicityEvidence = null;
            }
        }
        return new IndicatorQueryOncogenicity(oncogenicity, oncogenicityEvidence);
    }
    public static IndicatorQueryPathogenicity getPathogenicity(Alteration alteration, List<Alteration> relevantAlterations) {
        Pathogenicity pathogenicity = null;
        Evidence pathogenicityEvidence = null;

        // Find alteration specific pathogenicity
        pathogenicity = MainUtils.getCuratedAlterationPathogenicity(alteration);

        if(StringUtils.isNotEmpty(alteration.getAlteration()) && alteration.getAlteration().trim().toLowerCase().startsWith(InferredMutation.PATHOGENIC_VARIANTS.getVariant().toLowerCase())) {
            pathogenicity = Pathogenicity.YES;
        }

        if (pathogenicity == null || pathogenicity.equals(Pathogenicity.UNKNOWN)) {
            if (pathogenicity == null || pathogenicity.equals(Pathogenicity.UNKNOWN)) {
                pathogenicityEvidence = MainUtils.findHighestPathogenicEvidenceByEvidences(
                    new HashSet<>(EvidenceUtils.getEvidence(relevantAlterations, Collections.singleton(EvidenceType.PATHOGENIC), null))
                );
                if (pathogenicityEvidence != null) {
                    Pathogenicity tmpPathogenicity = Pathogenicity.getByEffect(pathogenicityEvidence.getKnownEffect());
                    if (tmpPathogenicity != null) {
                        pathogenicity = tmpPathogenicity;
                    }
                }
            }
        }

        if (pathogenicity == null) {
            pathogenicity = Pathogenicity.UNKNOWN;
        }
        return new IndicatorQueryPathogenicity(pathogenicity, pathogenicityEvidence);
    }

    private static String getMutationEffectDescriptionForUnknownAltButWithAA(Alteration alteration, Set<Evidence> evidences, Set<Alteration> alternativeAllele, String alternativeAlleleDescription) {
        StringBuilder sb = new StringBuilder("");
        boolean isVus = MainUtils.isVUS(alteration);
        if (isVus) {
            sb.append(getVUSSummary(alteration, "[[gene]] [[mutation]] [[[mutation]]]", true));
        } else {
            sb.append("The [[gene]] [[mutation]] [[[mutation]]] has not specifically been reviewed by the OncoKB team.");
        }
        sb.append(" However, ");
        Set<Alteration> evisAlts = new HashSet<>();
        evidences.forEach(evidence -> evisAlts.addAll(evidence.getAlterations()));
        Set<Alteration> intersectedAlts = Sets.intersection(evisAlts, new HashSet<>(alternativeAllele));
        if (intersectedAlts.size() > 1) {
            sb.append("we have mutation effect descriptions for [[gene]] " + allelesToStr(new HashSet<>(alternativeAllele)) + ".");
        } else {
            sb.append("the mutation effect description for [[gene]] " + evisAlts.iterator().next().getName() + ", an alternate allele of [[gene]] [[mutation]], " + " is: ");
            sb.append(alternativeAlleleDescription);
        }
        return sb.toString();
    }
    private static IndicatorQueryMutationEffect getMutationEffect(Alteration alteration, List<Alteration> alternativeAllele, List<Alteration> relevantAlterations) {
        IndicatorQueryMutationEffect indicatorQueryMutationEffect = new IndicatorQueryMutationEffect();
        // Find alteration specific mutation effect
        List<Evidence> selfAltMEEvis = EvidenceUtils.getEvidence(Collections.singletonList(alteration),
            Collections.singleton(EvidenceType.MUTATION_EFFECT), null);
        if (selfAltMEEvis != null) {
            indicatorQueryMutationEffect = MainUtils.findHighestMutationEffectByEvidence(new HashSet<>(selfAltMEEvis));
        }

        Set<Evidence> alternativeAlleleEvis = new HashSet<>();
        if (indicatorQueryMutationEffect.getMutationEffect() == null || indicatorQueryMutationEffect.getMutationEffect().equals(MutationEffect.UNKNOWN)) {
            alternativeAlleleEvis = new HashSet<>(EvidenceUtils.getEvidence(new ArrayList<>(alternativeAllele), Collections.singleton(EvidenceType.MUTATION_EFFECT), null));
            boolean isPositionalVariant = AlterationUtils.isPositionedAlteration(alteration);
            // Find mutation effect from alternative alleles
            if (alternativeAllele.size() > 0 && !isPositionalVariant) {
                Set<Evidence> evidences = MainUtils.getMutationEffectFromAlternativeAlleles(alternativeAlleleEvis);
                StringBuilder sb = new StringBuilder();
                if (evidences.size() > 0) {
                    Evidence evidenceToUse = evidences.iterator().next();
                    indicatorQueryMutationEffect.setMutationEffectEvidence(evidenceToUse);
                    indicatorQueryMutationEffect.setMutationEffect(MainUtils.setToAlternativeAlleleMutationEffect(MutationEffect.getByName(evidenceToUse.getKnownEffect())));

                    sb.append(getMutationEffectDescriptionForUnknownAltButWithAA(alteration, evidences, new HashSet<>(alternativeAllele), indicatorQueryMutationEffect.getMutationEffectEvidence().getDescription()));

                    Evidence evidence = new Evidence(indicatorQueryMutationEffect.getMutationEffectEvidence(), indicatorQueryMutationEffect.getMutationEffectEvidence().getId());
                    evidence.setDescription(sb.toString());
                    indicatorQueryMutationEffect.setMutationEffectEvidence(evidence);
                }
            }

            // If there is no mutation effect info available for this variant, find mutation effect from relevant variants
            // This also includes inconclusive check cause if the best mutation effect from alternative allele, we should continue find better one in other relevant alterations
            if (indicatorQueryMutationEffect.getMutationEffect() == null || indicatorQueryMutationEffect.getMutationEffect().equals(MutationEffect.INCONCLUSIVE) || indicatorQueryMutationEffect.getMutationEffect().equals(MutationEffect.UNKNOWN)) {
                List<Alteration> listToBeRemoved = new ArrayList<>(alternativeAllele);
                listToBeRemoved.add(alteration);

                for(Alteration relevantAlt : AlterationUtils.removeAlterationsFromList(relevantAlterations, listToBeRemoved)){
                    indicatorQueryMutationEffect = MainUtils.findHighestMutationEffectByEvidence(
                        new HashSet<>(EvidenceUtils.getEvidence(Collections.singletonList(relevantAlt), Collections.singleton(EvidenceType.MUTATION_EFFECT), null))
                    );
                    if (indicatorQueryMutationEffect.getMutationEffect() != null && !indicatorQueryMutationEffect.getMutationEffect().equals(MutationEffect.INCONCLUSIVE) && !indicatorQueryMutationEffect.getMutationEffect().equals(MutationEffect.UNKNOWN)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("The mutation effect description for ");
                        if (AlterationUtils.isTruncatingMutations((relevantAlt.getName()))) {
                            sb.append("truncating mutations");
                        } else if (FusionUtils.isFusion(relevantAlt.getName())) {
                            sb.append("functional fusions");
                        } else {
                            sb.append(relevantAlt.getName());
                        }
                        sb.append(" in [[gene]] is: " + indicatorQueryMutationEffect.getMutationEffectEvidence().getDescription());
                        Evidence evidence = new Evidence(indicatorQueryMutationEffect.getMutationEffectEvidence(), indicatorQueryMutationEffect.getMutationEffectEvidence().getId());
                        evidence.setDescription(sb.toString());
                        indicatorQueryMutationEffect.setMutationEffectEvidence(evidence);
                        break;
                    }
                }
            }

        }
        if (indicatorQueryMutationEffect.getMutationEffect() == null) {
            indicatorQueryMutationEffect.setMutationEffect(MutationEffect.UNKNOWN);
            // add information if alternative allele has mutation effect
            if (alternativeAlleleEvis.size() > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(getMutationEffectDescriptionForUnknownAltButWithAA(alteration, alternativeAlleleEvis, new HashSet<>(alternativeAllele), alternativeAlleleEvis.iterator().next().getDescription()));
                Evidence evidence = new Evidence();
                evidence.setKnownEffect(MutationEffect.UNKNOWN.getMutationEffect());
                evidence.setDescription(sb.toString());
                indicatorQueryMutationEffect.setMutationEffectEvidence(evidence);
            }
        }
        return indicatorQueryMutationEffect;
    }

    private static Date getLatestDateFromEvidences(Set<Evidence> evidences) {
        Date date = null;
        if (evidences != null) {
            for (Evidence evidence : evidences) {
                if (evidence.getLastEdit() != null) {
                    if (date == null) {
                        date = evidence.getLastEdit();
                    } else if (date.before(evidence.getLastEdit())) {
                        date = evidence.getLastEdit();
                    }
                }
            }
        }
        return date;
    }

    private static List<IndicatorQueryTreatment> getIndicatorQueryTreatments(Set<Evidence> evidences, String queryHugoSymbol, Boolean filterSameTreatment) {
        List<IndicatorQueryTreatment> treatments = new ArrayList<>();
        if (evidences != null) {
            Map<LevelOfEvidence, Set<Evidence>> evidenceSetMap = EvidenceUtils.separateEvidencesByLevel(evidences);

            ListIterator<LevelOfEvidence> li = getTherapeuticLevelsWithPriorityLIstIterator();
            while (li.hasPrevious()) {
                LevelOfEvidence level = li.previous();
                if (evidenceSetMap.containsKey(level)) {
                    Set<Treatment> sameLevelTreatments = new HashSet<>();
                    Map<Treatment, String> hugoSymbolMap = new HashMap<>();
                    Map<Treatment, Set<String>> pmidsMap = new HashMap<>();
                    Map<Treatment, Set<ArticleAbstract>> abstractsMap = new HashMap<>();
                    Map<Treatment, List<String>> alterationsMap = new HashMap<>();
                    Map<Treatment, Evidence> evidenceMap = new HashMap<>();
                    Map<Treatment, String> descriptionMap = new HashMap<>();
                    Map<Treatment, Set<LevelOfEvidence>> fdaLevelMap = new HashMap<>();

                    for (Evidence evidence : evidenceSetMap.get(level)) {
                        Citations citations = MainUtils.getCitationsByEvidence(evidence);
                        for (Treatment treatment : evidence.getTreatments()) {
                            if (!pmidsMap.containsKey(treatment)) {
                                pmidsMap.put(treatment, new HashSet<String>());
                            }
                            if (!abstractsMap.containsKey(treatment)) {
                                abstractsMap.put(treatment, new HashSet<ArticleAbstract>());
                            }
                            if (!alterationsMap.containsKey(treatment)) {
                                alterationsMap.put(treatment, new ArrayList<>());
                            }
                            if (!fdaLevelMap.containsKey(treatment)) {
                                fdaLevelMap.put(treatment, new HashSet<>());
                            }
                            hugoSymbolMap.put(treatment, evidence.getGene().getHugoSymbol());
                            pmidsMap.put(treatment, citations.getPmids());
                            abstractsMap.put(treatment, citations.getAbstracts());
                            alterationsMap.put(treatment, evidence.getAlterations().stream().map(alteration -> alteration.getName()).collect(Collectors.toList()));
                            evidenceMap.put(treatment, evidence);
                            descriptionMap.put(treatment, evidence.getDescription());
                            fdaLevelMap.get(treatment).add(evidence.getFdaLevel());
                        }
                        sameLevelTreatments.addAll(evidence.getTreatments());
                    }
                    List<Treatment> list = new ArrayList<>(sameLevelTreatments);
                    TreatmentUtils.sortTreatmentsByPriority(list);
                    for (Treatment treatment : list) {
                        String hugoSymbol = StringUtils.isEmpty(queryHugoSymbol) ? hugoSymbolMap.get(treatment) : queryHugoSymbol;
                        if (!filterSameTreatment || !treatmentExist(treatments, level, treatment.getDrugs())) {
                            List<TumorType> pickedCancerTypes = new ArrayList<>();
                            if (filterSameTreatment) {
                                pickedCancerTypes.add(evidenceMap.get(treatment).getCancerTypes().iterator().next());
                            } else {
                                pickedCancerTypes.addAll(evidenceMap.get(treatment).getCancerTypes());
                            }
                            Evidence evidence = evidenceMap.get(treatment);
                            for (TumorType tumorType :  pickedCancerTypes) {
                                List<TumorType> relevantTumorTypes = TumorTypeUtils.findRelevantTumorTypes(TumorTypeUtils.getTumorTypeName(tumorType), StringUtils.isEmpty(tumorType.getSubtype()), RelevantTumorTypeDirection.DOWNWARD);
                                IndicatorQueryTreatment indicatorQueryTreatment = new IndicatorQueryTreatment();
                                indicatorQueryTreatment.setDrugs(treatment.getDrugs());
                                indicatorQueryTreatment.setApprovedIndications(treatment.getApprovedIndications().stream().map(indication -> CplUtils.annotateGene(indication, hugoSymbol)).collect(Collectors.toSet()));
                                indicatorQueryTreatment.setLevel(level);
                                indicatorQueryTreatment.setFdaLevel(LevelUtils.getHighestFdaLevel(fdaLevelMap.get(treatment)));
                                indicatorQueryTreatment.setPmids(pmidsMap.get(treatment));
                                indicatorQueryTreatment.setAbstracts(abstractsMap.get(treatment));
                                indicatorQueryTreatment.setAlterations(alterationsMap.get(treatment));
                                indicatorQueryTreatment.setLevelAssociatedCancerType(new org.mskcc.cbio.oncokb.apiModels.TumorType(tumorType));
                                if (evidence.getExcludedCancerTypes().size() > 0) {
                                    Set<org.mskcc.cbio.oncokb.apiModels.TumorType> excludedCancerTypes = evidence.getExcludedCancerTypes().stream().filter(ect -> relevantTumorTypes.contains(ect)).map(ect -> new org.mskcc.cbio.oncokb.apiModels.TumorType(ect)).collect(Collectors.toSet());
                                    indicatorQueryTreatment.setLevelExcludedCancerTypes(excludedCancerTypes);
                                }
                                indicatorQueryTreatment.setDescription(CplUtils.annotateGene(descriptionMap.get(treatment), queryHugoSymbol));
                                treatments.add(indicatorQueryTreatment);
                            }
                        }
                    }
                }
            }
        }
        return treatments;
    }

    private static boolean treatmentExist(List<IndicatorQueryTreatment> treatments, LevelOfEvidence newTreatmentLevel,  List<Drug> newTreatment) {
        boolean exists = false;
        // Info level treatment can be included even the drug(s) is the same
        for (IndicatorQueryTreatment treatment : treatments) {
            if (getSortedTreatmentName(treatment.getDrugs()).equals(getSortedTreatmentName(newTreatment)) && !LevelUtils.INFO_LEVELS.contains(newTreatmentLevel)) {
                exists = true;
                break;
            }
        }
        return exists;
    }

    private static String getSortedTreatmentName(List<Drug> drugs) {
        List<Drug> drugsCopy = new ArrayList<>(drugs);
        drugsCopy.sort(new Comparator<Drug>() {
            @Override
            public int compare(Drug o1, Drug o2) {
                return o1.getDrugName().compareTo(o2.getDrugName());
            }
        });
        return drugsCopy.stream().map(drug -> drug.getDrugName()).collect(Collectors.joining("+"));
    }
    private static Boolean isVUS(Alteration alteration) {
        if (alteration == null) {
            return false;
        }
        List<Alteration> alterations = AlterationUtils.excludeVUS(Collections.singletonList(alteration));
        return alterations.size() == 0;
    }

    private static Map<String, LevelOfEvidence> findHighestLevel(Set<IndicatorQueryTreatment> treatments) {
        int levelSIndex = -1;
        int levelRIndex = -1;

        Map<String, LevelOfEvidence> levels = new HashMap<>();

        if (treatments != null) {
            for (IndicatorQueryTreatment treatment : treatments) {
                LevelOfEvidence levelOfEvidence = treatment.getLevel();
                if (levelOfEvidence != null) {
                    int _index = -1;
                    if (LevelUtils.isSensitiveLevel(levelOfEvidence)) {
                        _index = LevelUtils.getSensitiveLevelIndex(levelOfEvidence);
                        if (_index > levelSIndex) {
                            levelSIndex = _index;
                        }
                    } else if (LevelUtils.isResistanceLevel(levelOfEvidence)) {
                        _index = LevelUtils.getResistanceLevelIndex(levelOfEvidence);
                        if (_index > levelRIndex) {
                            levelRIndex = _index;
                        }
                    }
                }
            }
        }
        levels.put("sensitive", levelSIndex > -1 ? LevelUtils.getSensitiveLevelByIndex(levelSIndex) : null);
        levels.put("resistant", levelRIndex > -1 ? LevelUtils.getResistanceLevelByIndex(levelRIndex) : null);
        return levels;
    }

    private static List<Alteration> findRelevantAlts(Gene gene, ReferenceGenome referenceGenome, String alteration) {
        Set<Alteration> relevantAlts = new LinkedHashSet<>();
        Alteration alt = AlterationUtils.getAlteration(gene.getHugoSymbol(), alteration,
            null, null, null, null, referenceGenome);

        relevantAlts.addAll(AlterationUtils.getRelevantAlterations(referenceGenome, alt));

        Alteration revertAlt = AlterationUtils.getRevertFusions(referenceGenome, alt);
        if (revertAlt != null) {
            relevantAlts.addAll(AlterationUtils.getRelevantAlterations(referenceGenome, revertAlt));
        }
        return new ArrayList<>(relevantAlts);
    }

    public static Map<String, Object> findFusionGeneAndRelevantAlts(Query query) {
        List<String> geneStrsList = new ArrayList<>();
        if (query.getEntrezGeneId() != null) {
            Gene gene = GeneUtils.getGeneByEntrezId(query.getEntrezGeneId());
            if (gene != null) {
                geneStrsList.add(gene.getHugoSymbol());
            }
        } else {
            geneStrsList.addAll(FusionUtils.getGenesStrs(query.getHugoSymbol()));
        }
        Set<String> geneStrsSet = new LinkedHashSet<>();
        Gene gene = null;
        List<Alteration> fusionPair = new ArrayList<>();
        List<Alteration> relevantAlterations = new ArrayList<>();
        Map<String, Object> map = new HashedMap();

        if (geneStrsList != null) {
            geneStrsSet = new LinkedHashSet<>(geneStrsList);
            map.put("queryFusionGenes", geneStrsSet);
        }

        // Deal with two different genes fusion event.
        if (geneStrsSet.size() >= 2) {
            Set<Gene> tmpGenes = new LinkedHashSet<>();
            for (String geneStr : geneStrsSet) {
                Gene tmpGene = GeneUtils.getGeneByHugoSymbol(geneStr);
                if (tmpGene != null) {
                    tmpGenes.add(tmpGene);
                }
            }
            if (tmpGenes.size() > 0) {

                List<Gene> hasRelevantAltsGenes = new ArrayList<>();
                for (Gene tmpGene : tmpGenes) {
                    List<Alteration> tmpRelevantAlts = new ArrayList<>();
                    if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(query.getAlteration())) {
                        tmpRelevantAlts = findRelevantAlts(tmpGene, query.getReferenceGenome(), query.getAlteration());
                    } else {
                        tmpRelevantAlts = findRelevantAlts(tmpGene, query.getReferenceGenome(), query.getHugoSymbol() + " Fusion");
                    }
                    if (tmpRelevantAlts != null && tmpRelevantAlts.size() > 0) {
                        hasRelevantAltsGenes.add(tmpGene);
                    }
                }

                if (hasRelevantAltsGenes.size() > 1) {
                    map.put("hasRelevantAltsGenes", hasRelevantAltsGenes);
                } else if (hasRelevantAltsGenes.size() == 1) {
                    gene = hasRelevantAltsGenes.iterator().next();
                    if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(query.getAlteration())) {
                        relevantAlterations = findRelevantAlts(gene, query.getReferenceGenome(), query.getAlteration());
                    } else {
                        relevantAlterations = findRelevantAlts(gene, query.getReferenceGenome(), query.getHugoSymbol() + " Fusion");
                    }
                }

                // None of relevant alterations found in both genes.
                if (gene == null) {
                    gene = tmpGenes.iterator().next();
                }
                map.put("allGenes", tmpGenes);
            }
        } else if (geneStrsSet.size() == 1) {
            String geneStr = geneStrsSet.iterator().next();
            if (geneStr != null) {
                Gene tmpGene = GeneUtils.getGeneByHugoSymbol(geneStr);
                if (tmpGene != null) {
                    gene = tmpGene;
                    Alteration alt = AlterationUtils.getAlteration(gene.getHugoSymbol(), query.getAlteration(),
                        AlterationType.getByName(query.getAlterationType()), query.getConsequence(), null, null, query.getReferenceGenome());
                    if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(query.getAlteration())) {
                        relevantAlterations = findRelevantAlts(gene, query.getReferenceGenome(), query.getAlteration());
                    } else {
                        relevantAlterations = AlterationUtils.getRelevantAlterations(query.getReferenceGenome(), alt);

                        // Map Truncating Mutations to single gene fusion event
                        Alteration truncatingMutations = AlterationUtils.getTruncatingMutations(gene);
                        if (truncatingMutations != null && !relevantAlterations.contains(truncatingMutations)) {
                            relevantAlterations.add(truncatingMutations);
                        }
                    }
                }
            }
            LinkedHashSet<Gene> allGenes = new LinkedHashSet<>();
            for (String subGeneStr : geneStrsSet) {
                Gene tmpGene = GeneUtils.getGeneByHugoSymbol(subGeneStr);
                if (tmpGene != null) {
                    allGenes.add(tmpGene);
                }
            }
            map.put("allGenes", allGenes);
        }

        map.put("pickedGene", gene);
        map.put("relevantAlts", relevantAlterations);
        return map;
    }
}

class IndicatorQueryRespComp implements Comparator<IndicatorQueryResp> {

    public IndicatorQueryRespComp() {
    }

    @Override
    public int compare(IndicatorQueryResp e1, IndicatorQueryResp e2) {
        Integer result = LevelUtils.compareLevel(e1.getHighestSensitiveLevel(), e2.getHighestSensitiveLevel());
        if (result != 0) {
            return result;
        }

        result = LevelUtils.compareLevel(e1.getHighestResistanceLevel(), e2.getHighestResistanceLevel());
        if (result != 0) {
            return result;
        }

        result = MainUtils.compareOncogenicity(Oncogenicity.getByEffect(e1.getOncogenic()), Oncogenicity.getByEffect(e2.getOncogenic()), true);

        if (result != 0) {
            return result;
        }

        if (e1.getGeneExist() == null || !e1.getGeneExist()) {
            return 1;
        }

        if (e2.getGeneExist() == null || !e2.getGeneExist()) {
            return -1;
        }
        return -1;
    }
}

class IndicatorQueryMutationEffect {
    MutationEffect mutationEffect;
    Evidence mutationEffectEvidence;

    public IndicatorQueryMutationEffect() {
    }

    public MutationEffect getMutationEffect() {
        return mutationEffect;
    }

    public Evidence getMutationEffectEvidence() {
        return mutationEffectEvidence;
    }

    public void setMutationEffect(MutationEffect mutationEffect) {
        this.mutationEffect = mutationEffect;
    }

    public void setMutationEffectEvidence(Evidence mutationEffectEvidence) {
        this.mutationEffectEvidence = mutationEffectEvidence;
    }
}

class UniqueImplication {
    private Implication e;

    public UniqueImplication(Implication e) {
        this.e = e;
    }

    public Implication unwrap() {
        return this.e;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UniqueImplication that = (UniqueImplication) o;
        return Objects.equals(e.getLevelOfEvidence(), that.e.getLevelOfEvidence()) && Objects.equals(e.getTumorType(), that.e.getTumorType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(e.getLevelOfEvidence()) + Objects.hashCode(e.getTumorType());
    }
}
