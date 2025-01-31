package org.mskcc.cbio.oncokb.util;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.TumorType;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.Constants.IN_FRAME_DELETION;
import static org.mskcc.cbio.oncokb.Constants.IN_FRAME_INSERTION;
import static org.mskcc.cbio.oncokb.util.MainUtils.altNameShouldConvertToLowerCase;
import static org.mskcc.cbio.oncokb.util.MainUtils.lowerCaseAlterationName;
import static org.mskcc.cbio.oncokb.util.MainUtils.manuallyAssignedTruncatingMutation;

/**
 * Created by Hongxin on 8/10/15.
 */
public class SummaryUtils {
    public static final String TERT_PROMOTER_MUTATION_SUMMARY = "Select hotspot mutations in the TERT promoter have been shown to be oncogenic.";
    public static final String TERT_PROMOTER_NO_THERAPY_TUMOR_TYPE_SUMMARY = "There are no FDA-approved or NCCN-compendium listed treatments specifically for patients with TERT promoter mutations in [[tumor type]].";
    public static final String ONCOGENIC_MUTATIONS_DEFAULT_SUMMARY = "\"Oncogenic Mutations\" includes all variants annotated as oncogenic and likely oncogenic.";
    public static final List<String> specialAlterations = Arrays.asList("mutation", "alteration", "insertion", "deletion", "duplication", "fusion", "deletion", "amplification");

    public static Map<String, Object> tumorTypeSummary(EvidenceType evidenceType, Gene gene, Query query, Alteration exactMatchedAlt, List<Alteration> alterations, TumorType matchedTumorType, List<TumorType> relevantTumorTypes) {
        Map<String, Object> tumorTypeSummary = newTumorTypeSummary();
        String queryTumorType = query.getTumorType();
        String key = query.getQueryId();
        queryTumorType = convertTumorTypeNameInSummary(queryTumorType);

        if (gene == null || alterations == null || relevantTumorTypes == null || queryTumorType == null) {
            Map<String, Object> map = newTumorTypeSummary();
            return map;
        }

        query.setTumorType(queryTumorType);
        tumorTypeSummary = getTumorTypeSummarySubFunc(evidenceType, gene, query, exactMatchedAlt, alterations, matchedTumorType, relevantTumorTypes);

        return tumorTypeSummary;
    }

    private static Map<String, Object> getTumorTypeSummarySubFunc(EvidenceType evidenceType, Gene gene, Query query, Alteration exactMatchedAlt, List<Alteration> relevantAlterations, TumorType matchedTumorType, List<TumorType> relevantTumorTypes) {
        Map<String, Object> tumorTypeSummary = newTumorTypeSummary();
        Alteration alteration = null;

        if (exactMatchedAlt != null) {
            alteration = exactMatchedAlt;
        } else {
            alteration = AlterationUtils.getAlteration(query.getHugoSymbol(), query.getAlteration(), AlterationType.getByName(query.getAlterationType()), query.getConsequence(), query.getProteinStart(), query.getProteinEnd(), query.getReferenceGenome());
        }

        if (alteration.getConsequence().getTerm().equals("synonymous_variant")) {
            // No summary for synonymous variant
            return tumorTypeSummary;
        }

        tumorTypeSummary = null;

        List<Alteration> matchedAlterations = new ArrayList<>();
        matchedAlterations.add(alteration);

        // Include alteration that is considered the same weight with exact match
        // We send all matched alterations to get evidences which then will be sorted based on relevant tumor types order.
        // This guarantees the most relevant tumor type evidence will be used first
        String matchedAltStr = Optional.ofNullable(alteration.getAlteration()).orElse("");
        matchedAlterations.addAll(relevantAlterations.stream().filter(alt -> matchedAltStr.equals(AlterationUtils.removeExclusionCriteria(alt.getAlteration()))).collect(Collectors.toList()));
        if (tumorTypeSummary == null) {
            tumorTypeSummary = getRelevantTumorTypeSummaryByAlt(evidenceType, matchedAlterations, matchedTumorType, relevantTumorTypes);
        }

        List<Alteration> alternativeAlleles = new ArrayList<>();
        alternativeAlleles.addAll(AlterationUtils.getPositionedAlterations(query.getReferenceGenome(), alteration));
        alternativeAlleles = ListUtils.intersection(alternativeAlleles, relevantAlterations);
        alternativeAlleles.removeAll(matchedAlterations);

        // Get all tumor type summary evidences for the exact alteration + alternative alleles
        // Tumor type has high priority. Get relevant tumor type summary across all alternative alleles, then look for other tumor types summary
        if (tumorTypeSummary == null) {
            for (Alteration allele : alternativeAlleles) {
                tumorTypeSummary = getRelevantTumorTypeSummaryByAlt(evidenceType, Collections.singletonList(allele), matchedTumorType, relevantTumorTypes);
                if (tumorTypeSummary != null) {
                    break;
                }
            }

            if (tumorTypeSummary == null) {
                // when looking for other tumor type summary, we also like to backtrack the ones from matchedAlterations first
                List<Alteration> combinedAlts = new ArrayList<>(matchedAlterations);
                combinedAlts.addAll(alternativeAlleles);
                for (Alteration allele : combinedAlts) {
                    tumorTypeSummary = getOtherTumorTypeSummaryByAlt(evidenceType, allele, new HashSet<>(relevantTumorTypes));
                    if (tumorTypeSummary != null) {
                        break;
                    }
                }
            }
        }

        // Get all tumor type summary evidence for relevant alterations.
        // Alteration has high priority. Get relevant tumor type summary, then other tumor type summary, then next relevant alteration
        if (tumorTypeSummary == null) {
            // Sort all tumor type summaries, the more specific tumor type summary will be picked.
            // Deal with KIT, give Exon annotation highers priority
            relevantAlterations.removeAll(matchedAlterations);
            relevantAlterations.removeAll(alternativeAlleles);

            if (gene.getHugoSymbol().equals("KIT")) {
                Collections.sort(relevantAlterations, new Comparator<Alteration>() {
                    public int compare(Alteration x, Alteration y) {
                        Integer result = 0;
                        // TODO: need more comprehensive method to determine the order.
                        String nameX = (x.getName() != null ? x.getName() : x.getAlteration()).toLowerCase();
                        String nameY = (y.getName() != null ? y.getName() : y.getAlteration()).toLowerCase();
                        if (nameX.contains("exon")) {
                            if (nameY.contains("exon")) {
                                result = 0;
                            } else {
                                result = -1;
                            }
                        } else {
                            if (nameY.contains("exon")) {
                                result = 1;
                            } else {
                                result = 0;
                            }
                        }
                        return result;
                    }
                });
            }

            // group the relevant alterations by removing exclusion
            // when alteration has exclusion criteria and however is included in relevantAlterations,
            // that means the alteration in query is not excluded, therefore the relevant alteration should be
            // treated as the same to the one without exclusion criteria
            // for instance, Oncogenic Mutations and Oncogenic Mutations {excluding V600K}
            // when alteration in query is V600E, both relevant alterations are the same
            // it matters here is that we need to find the specific cancer type first then other tumor type summary

            LinkedHashSet<String> uniqRelevantAlts = new LinkedHashSet<>();
            Map<String, LinkedHashSet<Alteration>> groupedRelevantAlterations = new HashMap<>();
            for (Alteration alt : relevantAlterations) {
                String uniqRelevantAlt = AlterationUtils.removeExclusionCriteria(alt.getAlteration());
                uniqRelevantAlts.add(uniqRelevantAlt);
                groupedRelevantAlterations.putIfAbsent(uniqRelevantAlt, new LinkedHashSet<>());
                groupedRelevantAlterations.get(uniqRelevantAlt).add(alt);
            }

            // Base on the priority of relevant alterations
            for (String uniqRelevantAlt : uniqRelevantAlts) {
                for (Alteration alt : groupedRelevantAlterations.get(uniqRelevantAlt)) {
                    tumorTypeSummary = getRelevantTumorTypeSummaryByAlt(evidenceType, Collections.singletonList(alt), matchedTumorType, relevantTumorTypes);
                    if (tumorTypeSummary != null) {
                        break;
                    }
                }
                if (tumorTypeSummary != null) {
                    break;
                }

                for (Alteration alt : groupedRelevantAlterations.get(uniqRelevantAlt)) {
                    // Get Other Tumor Types summary
                    for (TumorType tumorType : relevantTumorTypes) {
                        tumorTypeSummary = getOtherTumorTypeSummaryByAlt(evidenceType, alt, Collections.singleton(tumorType));
                        if (tumorTypeSummary != null) {
                            break;
                        }
                    }
                    if (tumorTypeSummary != null) {
                        break;
                    }
                }
                if (tumorTypeSummary != null) {
                    break;
                }
            }
        }

        if (tumorTypeSummary == null) {
            tumorTypeSummary = newTumorTypeSummary();
            String tmpSummary = "";
            if (evidenceType.equals(EvidenceType.TUMOR_TYPE_SUMMARY)) {
                if (query.getAlteration().toLowerCase().contains("truncating mutation")) {
                    tmpSummary = "There are no FDA-approved or NCCN-compendium listed treatments specifically for patients with [[tumor type]] harboring " + getGeneArticle(gene) + " [[gene]] truncating mutation.";
                } else if (gene.getHugoSymbol().equals("TERT") && query.getAlteration().trim().equalsIgnoreCase("promoter")) {
                    tmpSummary = TERT_PROMOTER_NO_THERAPY_TUMOR_TYPE_SUMMARY;
                } else {
                    tmpSummary = "There are no FDA-approved or NCCN-compendium listed treatments specifically for patients with [[variant]].";
                }
            }
            tumorTypeSummary.put("summary", tmpSummary);
        }

        tumorTypeSummary.put("summary", CplUtils.annotate(
            (String) tumorTypeSummary.get("summary"),
            query.getHugoSymbol(),
            query.getAlteration(),
            query.getTumorType(),
            query.getReferenceGenome(),
            gene,
            matchedTumorType
        ));

        return tumorTypeSummary;
    }

    private static Map<String, Object> getRelevantTumorTypeSummaryByAlt(EvidenceType evidenceType, List<Alteration> alterations, TumorType matchedTumorType, List<TumorType> relevantTumorTypes) {
        return getTumorTypeSummaryFromEvidences(EvidenceUtils.getEvidence(alterations, Collections.singleton(evidenceType), matchedTumorType, relevantTumorTypes, null));
    }

    private static Map<String, Object> getOtherTumorTypeSummaryByAlt(EvidenceType evidenceType, Alteration alteration, Set<TumorType> relevantTumorTypes) {
        // Check other tumor types summary based on tumor form
        List<SpecialTumorType> specialTumorTypes = new ArrayList<>();
        TumorForm tumorForm = TumorTypeUtils.checkTumorForm(relevantTumorTypes);
        if (tumorForm != null) {
            specialTumorTypes.add(tumorForm.equals(TumorForm.SOLID) ?
                SpecialTumorType.OTHER_SOLID_TUMOR_TYPES : SpecialTumorType.OTHER_LIQUID_TUMOR_TYPES);
        }

        specialTumorTypes.add(SpecialTumorType.OTHER_TUMOR_TYPES);

        for (SpecialTumorType specialTumorType : specialTumorTypes) {

            List<Evidence> evidences = EvidenceUtils.getEvidence(
                Collections.singletonList(alteration),
                Collections.singleton(evidenceType),
                ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(specialTumorType),
                Collections.singletonList(ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(specialTumorType)), null);
            if (evidences.size() > 0) {
                return getTumorTypeSummaryFromEvidences(evidences);
            }
        }
        return null;
    }

    public static String unknownOncogenicSummary(Gene gene, ReferenceGenome referenceGenome, Query query, Alteration alteration) {
        StringBuilder sb = new StringBuilder();
        if (addPrefixArticleToMutation(AlterationUtils.isPositionedAlteration(alteration), alteration.getAlteration())) {
            sb.append("The ");
        }
        sb.append(gene == null ? "variant" : getGeneMutationNameInVariantSummary(gene, referenceGenome, query.getHugoSymbol(), query.getAlteration()));
        sb.append(" has not specifically been reviewed by the OncoKB team, and therefore its biological significance is unknown.");
        return sb.toString();
    }

    public static String synonymousSummary() {
        return "This is a synonymous mutation and is not annotated by OncoKB.";
    }

    public static String variantSummary(Gene gene, Alteration exactMatchAlteration, List<Alteration> alterations, Query query) {
        if (!StringUtils.isEmpty(query.getAlteration()) && query.getAlteration().toLowerCase().startsWith(InferredMutation.ONCOGENIC_MUTATIONS.getVariant().toLowerCase())) {
            return ONCOGENIC_MUTATIONS_DEFAULT_SUMMARY;
        }
        return getOncogenicSummarySubFunc(gene, exactMatchAlteration, alterations, query);
    }

    private static String getOncogenicSummarySubFunc(Gene gene, Alteration exactMatchAlteration, List<Alteration> alterations, Query query) {
        StringBuilder sb = new StringBuilder();

        Oncogenicity oncogenic = null;

        boolean isHotspot = false;
        boolean isVus = false;
        String queryAlteration = query.getAlteration();
        Alteration alteration = null;

        if (AlterationUtils.isGeneralAlterations(queryAlteration, true)) {
            queryAlteration = queryAlteration.substring(0, 1).toUpperCase() + queryAlteration.substring(1);
        }

        // if the gene is Other Biomarker, return the mutation effect description for alteration instead
        if (gene.getHugoSymbol().equals(SpecialStrings.OTHERBIOMARKERS)) {
            if (exactMatchAlteration != null) {
                List<Evidence> evidences = EvidenceUtils.getEvidence(Collections.singletonList(exactMatchAlteration), Collections.singleton(EvidenceType.MUTATION_EFFECT), null);

                // Technically the list should only contain no more than one record.
                for (Evidence evidence : evidences) {
                    if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(evidence.getDescription())) {
                        return evidence.getDescription();
                    }
                }
                return "";
            } else {
                return "";
            }
        }

        // Give predefined TERT promoter summary
        if (gene.getHugoSymbol().equals("TERT")) {
            String altStr = exactMatchAlteration == null ? query.getAlteration().trim() : exactMatchAlteration.getAlteration();
            if (altStr.toLowerCase().contains("promoter")) {
                return TERT_PROMOTER_MUTATION_SUMMARY;
            }
        }

        if (exactMatchAlteration != null) {
            // Synonymous Summary
            if (exactMatchAlteration.getConsequence().getTerm().equals("synonymous_variant")) {
                return synonymousSummary();
            }

            // Find oncogenic info from exact matched variant
            List<Evidence> oncogenicEvidences = EvidenceUtils.getEvidence(Collections.singletonList(exactMatchAlteration), Collections.singleton(EvidenceType.ONCOGENIC), null);
            if (oncogenicEvidences != null && oncogenicEvidences.size() > 0) {
                Set<Oncogenicity> oncogenicities = new HashSet<>();
                for (Evidence evidence : oncogenicEvidences) {
                    Oncogenicity tmpOncogenic = Oncogenicity.getByEvidence(evidence);
                    if (tmpOncogenic != null) {
                        oncogenicities.add(tmpOncogenic);
                    }
                }
                oncogenic = MainUtils.findHighestOncogenicity(oncogenicities);
            }
            alteration = exactMatchAlteration;
        } else {
            alteration = AlterationUtils.getAlteration(gene.getHugoSymbol(), query.getAlteration(),
                AlterationType.getByName(query.getAlterationType()), query.getConsequence(), query.getProteinStart(), query.getProteinEnd(), query.getReferenceGenome());
        }

        if (oncogenic != null && !oncogenic.equals(Oncogenicity.UNKNOWN)) {
            return getOncogenicSummaryFromOncogenicity(oncogenic, alteration, query);
        }

        isHotspot = HotspotUtils.isHotspot(alteration);
        isVus = MainUtils.isVUS(alteration);

        if (AlterationUtils.isPositionedAlteration(alteration)) {
            return positionalVariantSummary(alteration, query, isHotspot);
        }

        if (isHotspot) {
            if (alteration != null && isVus) {
                return vusAndHotspotSummary(alteration, query, isHotspot);
            } else {
                return hotspotSummary(alteration, query, false);
            }
        }

        if (oncogenic == null || oncogenic.equals(Oncogenicity.UNKNOWN)) {
            // Get oncogenic summary from alternative alleles
            List<Alteration> alternativeAlleles = AlterationUtils.getAlleleAlterations(query.getReferenceGenome(), alteration);
            List<Alteration> alternativeAllelesWithoutVUS = AlterationUtils.excludeVUS(gene, alternativeAlleles);

            // VUS alternative alleles are not accounted into oncogenic summary calculation
            if (alternativeAllelesWithoutVUS.size() > 0) {
                sb.append(alleleSummary(query.getReferenceGenome(), alteration, query.getHugoSymbol(), isVus));
                if (StringUtils.isNotEmpty(sb.toString().trim())) {
                    return sb.toString();
                }
            }

            // Get oncogenic info from rest of relevant alterations except AA
            alterations.removeAll(alternativeAlleles);
            List<Evidence> oncogenicityEvidences = new ArrayList<>();
            for (Alteration a : alterations) {
                List<Evidence> altOncogenicEvidences = EvidenceUtils.getEvidence(Collections.singletonList(a), Collections.singleton(EvidenceType.ONCOGENIC), null);
                if (altOncogenicEvidences != null && altOncogenicEvidences.size() > 0) {
                    oncogenicityEvidences.addAll(altOncogenicEvidences);
                }
            }

            Evidence evidence = MainUtils.findHighestOncogenicityEvidence(oncogenicityEvidences);
            if (evidence != null) {
                Optional<Alteration> relevantAltOpt = evidence.getAlterations().stream().filter(alt -> alterations.contains(alt)).findFirst();
                if (relevantAltOpt.isPresent()) {
                    Alteration relevantAlt = relevantAltOpt.get();
                    // we only want to use umbrella term summary for Truncating Mutations for now
                    if (AlterationUtils.isTruncatingMutations(relevantAlt.getName()) && !manuallyAssignedTruncatingMutation(query)) {
                        return variantSummaryForTruncatingMutation(queryAlteration, alteration, Oncogenicity.getByEvidence(evidence));
                    }
                }
                oncogenic = Oncogenicity.getByEvidence(evidence);
            }
        }

        if (oncogenic != null && !oncogenic.equals(Oncogenicity.UNKNOWN)) {
            return getOncogenicSummaryFromOncogenicity(oncogenic, alteration, query);
        }

        if (alteration != null && MainUtils.isVUS(alteration)) {
            return getVUSOncogenicSummary(query.getReferenceGenome(), alteration, query);
        }

        if (query.getAlteration().toLowerCase().contains("truncating mutation") || (alteration.getConsequence() != null && alteration.getConsequence().getIsGenerallyTruncating())) {
            if (gene.getOncogene()) {
                return query.getHugoSymbol() + " is considered an oncogene and truncating mutations in oncogenes are typically nonfunctional.";
            } else if (!gene.getTSG() && oncogenic == null) {
                return "It is unknown whether a truncating mutation in " + query.getHugoSymbol() + " is oncogenic.";
            }
        }

        String summary = unknownOncogenicSummary(gene, query.getReferenceGenome(), query, alteration);
        summary = summary.replace("[[gene]]", query.getHugoSymbol());
        return summary;
    }

    /**
     * Get the curated date that is associated with the variant of significance.
     *
     * @param alteration Variant of significance.
     * @return last edit in format MM/dd/YYY (01/01/2020). If VUS does not have a date, null is returned
     */
    private static String getVusDate(Alteration alteration) {
        List<Evidence> evidences = EvidenceUtils.getEvidence(Collections.singletonList(alteration), Collections.singleton(EvidenceType.VUS), null);
        Date lastEdit = null;
        for (Evidence evidence : evidences) {
            if (evidence.getLastEdit() == null) {
                continue;
            }
            if (lastEdit == null) {
                lastEdit = evidence.getLastEdit();
            } else if (lastEdit.compareTo(evidence.getLastEdit()) < 0) {
                lastEdit = evidence.getLastEdit();
            }
        }
        if (lastEdit != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
            return sdf.format(lastEdit);
        }
        return null;
    }

    private static String getVUSOncogenicSummary(ReferenceGenome referenceGenome, Alteration alteration, Query query) {
        StringBuilder sb = new StringBuilder();
        sb.append(getVUSSummary(alteration, getGeneMutationNameInVariantSummary(alteration.getGene(), referenceGenome, query.getHugoSymbol(), alteration.getAlteration()), false));
        sb.append(" and therefore its biological significance is unknown.");
        return sb.toString();
    }

    public static String getVUSSummary(Alteration vus, String altStr, boolean fullSentence) {
        StringBuilder sb = new StringBuilder();
        String lastEdit = getVusDate(vus);
        sb.append("There is no available functional data about the " + altStr);
        if (lastEdit != null) {
            sb.append(" (last reviewed on ");
            sb.append(lastEdit);
            sb.append(")");
        }
        if (fullSentence) {
            sb.append(".");
        } else {
            sb.append(",");
        }
        return sb.toString();
    }

    private static String getOncogenicSummaryFromOncogenicity(Oncogenicity oncogenicity, Alteration alteration, Query query) {
        StringBuilder sb = new StringBuilder();
        String queryAlteration = query.getAlteration();
        String altName = getGeneMutationNameInVariantSummary(alteration.getGene(), query.getReferenceGenome(), query.getHugoSymbol(), queryAlteration);
        Boolean isPlural = false;
        if (specialAlterations.stream().anyMatch(sa -> altName.toLowerCase().contains(sa.toLowerCase() + "s"))) {
            isPlural = true;
        }
        Boolean appendThe = appendThe(queryAlteration, isPlural);
        if (oncogenicity != null) {
            if (manuallyAssignedTruncatingMutation(query)) {
                return "This " + alteration.getGene().getHugoSymbol() + " " + query.getSvType().name().toLowerCase() + " may be a truncating alteration and is " + getOncogenicSubTextFromOncogenicity(oncogenicity) + ".";
            }

            if (oncogenicity.equals(Oncogenicity.INCONCLUSIVE)) {
                return inconclusiveSummary(alteration.getGene(), query.getReferenceGenome(), query);
            }

            if (oncogenicity.equals(Oncogenicity.RESISTANCE)) {
                return resistanceOncogenicitySummary(alteration.getGene(), query);
            }
            if (appendThe) {
                sb.append("The ");
            }
            sb.append(altName);

            if (isPlural) {
                sb.append(" are");
            } else {
                sb.append(" is");
            }

            if (oncogenicity.equals(Oncogenicity.LIKELY_NEUTRAL)) {
                sb.append(" likely neutral.");
            } else {
                if (oncogenicity.equals(Oncogenicity.LIKELY)) {
                    sb.append(" likely");
                } else if (oncogenicity.equals(Oncogenicity.YES)) {
                    sb.append(" known to be");
                }

                sb.append(" oncogenic.");
            }
        }
        return sb.toString();
    }

    private static String getOncogenicSubTextFromOncogenicity(Oncogenicity oncogenicity) {
        if (oncogenicity == null)
            return "";
        StringBuilder sb = new StringBuilder();
        if (oncogenicity.equals(Oncogenicity.LIKELY_NEUTRAL)) {
            sb.append("considered likely neutral.");
        } else {
            if (oncogenicity.equals(Oncogenicity.LIKELY)) {
                sb.append("considered likely");
            } else if (oncogenicity.equals(Oncogenicity.YES)) {
                sb.append("known to be");
            } else {
                // For Unknown
                return "";
            }
            sb.append(" oncogenic");
        }
        return sb.toString();
    }

    public static String geneSummary(Gene gene, String queryHugoSymbol, boolean isGermline) {
        if (gene != null && gene.getHugoSymbol().equals(SpecialStrings.OTHERBIOMARKERS)) {
            return "";
        }
        return enrichGeneEvidenceDescription(EvidenceType.GENE_SUMMARY, gene, StringUtils.isEmpty(queryHugoSymbol) ? gene.getHugoSymbol() : queryHugoSymbol);
    }

    public static String geneBackground(Gene gene, String queryHugoSymbol) {
        if (gene != null && gene.getHugoSymbol().equals(SpecialStrings.OTHERBIOMARKERS)) {
            return "";
        }
        return enrichGeneEvidenceDescription(EvidenceType.GENE_BACKGROUND, gene, StringUtils.isEmpty(queryHugoSymbol) ? gene.getHugoSymbol() : queryHugoSymbol);
    }

    private static String enrichGeneEvidenceDescription(EvidenceType evidenceType, Gene gene, String hugoSymbol) {
        Set<Evidence> geneBackgroundEvs = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, Collections.singleton(evidenceType));
        String summary = "";
        if (!geneBackgroundEvs.isEmpty()) {
            Evidence ev = geneBackgroundEvs.iterator().next();
            if (ev != null) {
                summary = ev.getDescription();
            }
        }

        if (summary == null) {
            summary = "";
        }
        summary = summary.trim();
        summary = summary.endsWith(".") ? summary : summary + ".";
        summary = CplUtils.annotateGene(summary, hugoSymbol);
        return summary;
    }

    public static String variantSummaryForTruncatingMutation(String altQuery, Alteration alteration, Oncogenicity oncogenicity) {
        StringBuilder sb = new StringBuilder();
        String hugoSymbol = alteration.getGene().getHugoSymbol();
        sb.append("The ");
        if (!altQuery.contains(hugoSymbol)) {
            sb.append(hugoSymbol);
            sb.append(" ");
        }
        sb.append(altQuery);
        if (altQuery.toLowerCase().contains("fusion")) {
            sb.append(" leads to truncation of the " + hugoSymbol + " tumor suppressor gene and is considered ");
        } else {
            sb.append(" is a truncating mutation");
            if (alteration.getGene().getTSG()) {
                if (alteration.getGene().getOncogene()) {
                    sb.append("; truncating mutations in this gene are considered ");
                } else {
                    sb.append(" in a tumor suppressor gene");
                    sb.append(", and therefore is ");
                }
            } else {
                sb.append(hugoSymbol);
                sb.append(", and therefore is ");
            }
        }
        sb.append(oncogenicity.getOncogenic().toLowerCase());
        sb.append(".");
        return sb.toString();
    }

    public static String joinStringsInSentence(List<String> strings) {
        if (strings == null || strings.isEmpty()) return "";
        if (strings.size() == 1) return strings.get(0);
        if (strings.size() == 2) return StringUtils.join(strings, " and ");
        String sentence = "";
        sentence = StringUtils.join(strings.subList(0, strings.size() - 1), ", ");
        sentence = sentence + StringUtils.join(strings.subList(strings.size() - 1, strings.size()), ", and ");
        return sentence;
    }

    public static String alleleSummaryWithOncogenicities(String hugoSymbol, Oncogenicity oncogenicity, Set<Alteration> alterations) {
        StringBuilder sb = new StringBuilder();
        sb.append(hugoSymbol);
        sb.append(" ");
        sb.append(allelesToStr(alterations));
        if (Oncogenicity.RESISTANCE.equals(oncogenicity)) {
            sb.append((alterations.size() > 1 ? " have" : " has"));
        } else {
            sb.append((alterations.size() > 1 ? " are" : " is"));
        }
        switch (oncogenicity) {
            case YES:
                sb.append(" known to be oncogenic");
                break;
            case RESISTANCE:
                sb.append(" been found in the context of resistance to a targeted therapy(s)");
                break;
            default:
                sb.append(" " + oncogenicity.getOncogenic().toLowerCase());
                break;
        }
        return sb.toString();
    }

    public static Oncogenicity getRefAlleleOncogenicityBasedOnAltAlleles(List<Oncogenicity> oncogenicities) {
        if (oncogenicities.size() == 0) {
            return Oncogenicity.UNKNOWN;
        } else if (oncogenicities.size() == 1) {
            if (Arrays.asList(Oncogenicity.YES, Oncogenicity.LIKELY).contains(oncogenicities.get(0))) {
                return Oncogenicity.LIKELY;
            } else {
                return Oncogenicity.UNKNOWN;
            }
        } else {
            if (oncogenicities.stream().anyMatch(oncogenicity -> Arrays.asList(Oncogenicity.YES, Oncogenicity.LIKELY).contains(oncogenicity))) {
                return Oncogenicity.LIKELY;
            } else {
                return Oncogenicity.UNKNOWN;
            }
        }
    }

    public static String alleleSummary(ReferenceGenome referenceGenome, Alteration alteration, String queryHugoSymbol, boolean isVus) {
        StringBuilder sb = new StringBuilder();

        Set<Alteration> alleles = new HashSet<>(AlterationUtils.getAlleleAlterations(referenceGenome, alteration));

        Map<Oncogenicity, Set<Alteration>> map = geAlterationsWithHighestOncogenicity(new HashSet<>(alleles));
        List<Oncogenicity> allowedOncogenicity = Arrays.asList(Oncogenicity.YES, Oncogenicity.LIKELY, Oncogenicity.LIKELY_NEUTRAL, Oncogenicity.RESISTANCE);
        List<Oncogenicity> allelesOncogenicities = allowedOncogenicity
            .stream()
            .filter(oncogenicity -> map.containsKey(oncogenicity))
            .collect(Collectors.toList());
        boolean hasOncogenic = allelesOncogenicities
            .stream()
            .filter(oncogenicity -> Arrays.asList(Oncogenicity.YES, Oncogenicity.LIKELY).contains(oncogenicity))
            .count() > 0;
        List<String> oncogenicitySentences = allelesOncogenicities
            .stream()
            .map(oncogenicity -> alleleSummaryWithOncogenicities(alteration.getGene().getHugoSymbol(), oncogenicity, map.get(oncogenicity)))
            .collect(Collectors.toList());

        if (oncogenicitySentences.size() > 0) {
            String altStr = getGeneMutationNameInVariantSummary(alteration.getGene(), referenceGenome, queryHugoSymbol, alteration.getAlteration());
            Oncogenicity referredOncogenicity = getRefAlleleOncogenicityBasedOnAltAlleles(allelesOncogenicities);
            if (referredOncogenicity == null) {
                referredOncogenicity = Oncogenicity.UNKNOWN;
            }
            if (isVus) {
                sb.append(getVUSSummary(alteration, altStr, true));
            } else {
                sb.append("The " + altStr + " has not specifically been reviewed by the OncoKB team.");
            }

            boolean isHoweverCondition = (hasOncogenic || allelesOncogenicities.size() > 1) && referredOncogenicity != Oncogenicity.UNKNOWN;
            if (isHoweverCondition) {
                sb.append(" However, ");
            } else {
                sb.append(" While ");
            }
            sb.append(joinStringsInSentence(oncogenicitySentences));
            if (oncogenicitySentences.size() == 1) {
                sb.append(",");
                if (hasOncogenic && isHoweverCondition) {
                    sb.append(" and therefore");
                } else {
                    sb.append(" the oncogenic effect of");
                }
            } else {
                if (isHoweverCondition) {
                    sb.append("; therefore");
                }
                if (!hasOncogenic) {
                    sb.append(", the oncogenic effect of");
                }
            }
            sb.append(" ");
            sb.append(alteration.getGene().getHugoSymbol() + " " + alteration.getAlteration() + " is ");
            if (hasOncogenic) {
                sb.append("considered ");
            }
            sb.append(getRefAlleleOncogenicityBasedOnAltAlleles(allelesOncogenicities).getOncogenic().toLowerCase());
            sb.append(".");
        }

        return sb.toString();
    }

    public static String resistanceOncogenicitySummary(Gene gene, Query query) {
        StringBuilder sb = new StringBuilder();
        sb.append("The ");
        sb.append(gene.getHugoSymbol());
        sb.append(" ");
        sb.append(query.getAlteration());
        sb.append(" has been found in the context of resistance to a targeted therapy(s).");
        return sb.toString();
    }

    public static String inconclusiveSummary(Gene gene, ReferenceGenome referenceGenome, Query query) {
        StringBuilder sb = new StringBuilder();
        sb.append("There is conflicting and/or weak data describing the biological significance of the ");
        sb.append(getGeneMutationNameInVariantSummary(gene, referenceGenome, query.getHugoSymbol(), query.getAlteration()));
        sb.append(".");
        return sb.toString();
    }

    public static String positionalVariantSummary(Alteration alteration, Query query, boolean isHotspot) {
        if (isHotspot) {
            return hotspotSummary(alteration, query, false, true);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("OncoKB assigns biological and oncogenic effects at the allele level, not the positional level.");
            Set<Alteration> alleles = new HashSet<>(AlterationUtils.getAlleleAlterations(query.getReferenceGenome(), alteration));
            if (alleles.size() > 0) {
                sb.append(" Biological and oncogenic effects are curated for the following " + query.getHugoSymbol() + " " + query.getAlteration() + " allele" + (alleles.size() > 1 ? "s" : "") + ": ");
                sb.append(allelesToStr(alleles));
                sb.append(".");
            }
            return sb.toString();
        }
    }

    public static String hotspotSummary(Alteration alteration, Query query, Boolean usePronoun) {
        return hotspotSummary(alteration, query, usePronoun, false);
    }

    private static boolean addPrefixArticleToMutation(boolean isPositionalVariant, String alteration) {
        return StringUtils.isNotEmpty(alteration) &&
            !isPositionalVariant &&
            !AlterationUtils.isGeneralAlterations(alteration) &&
            !alteration.toLowerCase().equals("truncating mutation");
    }

    public static String hotspotSummary(Alteration alteration, Query query, Boolean usePronoun, boolean isPositionalVariant) {
        StringBuilder sb = new StringBuilder();
        if (usePronoun == null) {
            usePronoun = false;
        }
        String altName = "";
        if (isPositionalVariant) {
            altName = query.getHugoSymbol() + " " + query.getAlteration();
        } else {
            altName = getGeneMutationNameInVariantSummary(alteration.getGene(), query.getReferenceGenome(), query.getHugoSymbol(), query.getAlteration());
        }
        if (usePronoun) {
            sb.append("It");
        } else {
            if (addPrefixArticleToMutation(isPositionalVariant, alteration.getAlteration())) {
                sb.append("The ");
            }
            sb.append(altName);
        }
        sb.append(" has been identified as a statistically significant hotspot and ");
        if (isPositionalVariant) {
            sb.append("variants at this position are considered likely oncogenic");
        } else {
            sb.append("is likely to be oncogenic");
        }
        sb.append(".");
        return sb.toString();
    }

    private static String vusAndHotspotSummary(Alteration alteration, Query query, Boolean isHotspot) {
        StringBuilder sb = new StringBuilder();
        String altStr = getGeneMutationNameInVariantSummary(alteration.getGene(), query.getReferenceGenome(), query.getHugoSymbol(), alteration.getAlteration());
        sb.append(getVUSSummary(alteration, altStr, true));

        if (isHotspot) {
            sb.append(" However, it has been identified as a statistically significant hotspot and is likely to be oncogenic.");
        }
        return sb.toString();
    }

    private static String alleleNamesStr(Set<Alteration> alterations) {
        if (alterations != null && alterations.size() > 0) {
            Alteration tmp = alterations.iterator().next();
            String residue = tmp.getRefResidues();
            String location = Integer.toString(tmp.getProteinStart());
            Set<String> variantResidue = new TreeSet<>();
            Set<Alteration> withoutVariantResidues = new HashSet<>();

            for (Alteration alteration : alterations) {
                if (alteration.getVariantResidues() == null) {
                    withoutVariantResidues.add(alteration);
                } else {
                    variantResidue.add(alteration.getVariantResidues());
                }
            }

            StringBuilder sb = new StringBuilder();

            if (variantResidue.size() > 0) {
                sb.append(residue + location + StringUtils.join(variantResidue, "/"));
            }

            if (withoutVariantResidues.size() > 0) {
                List<String> alterationNames = new ArrayList<>();
                for (Alteration alteration : withoutVariantResidues) {
                    alterationNames.add(alteration.getName());
                }
                if (variantResidue.size() > 0) {
                    sb.append(", ");
                }
                sb.append(MainUtils.listToString(alterationNames, ", "));
            }


            return sb.toString();
        } else {
            return "";
        }
    }

    public static String allelesToStr(Set<Alteration> alterations) {
        List<String> alterationNames = new ArrayList<>();
        Map<Integer, Set<Alteration>> locationBasedAlts = new HashMap<>();
        Set<Alteration> specialAlts = new HashSet<>();

        for (Alteration alteration : alterations) {
            if (alteration.getProteinStart() != null && alteration.getProteinEnd() != null &&
                alteration.getProteinStart().equals(alteration.getProteinEnd())) {
                if (!locationBasedAlts.containsKey(alteration.getProteinStart()))
                    locationBasedAlts.put(alteration.getProteinStart(), new HashSet<Alteration>());
                locationBasedAlts.get(alteration.getProteinStart()).add(alteration);
            } else {
                specialAlts.add(alteration);
            }
        }

        for (Map.Entry entry : locationBasedAlts.entrySet()) {
            alterationNames.add(alleleNamesStr((Set<Alteration>) entry.getValue()));
        }

        for (Alteration alteration : specialAlts) {
            alterationNames.add(alleleNamesStr(Collections.singleton(alteration)));
        }

        return MainUtils.listToString(alterationNames);
    }

    private static Map<Oncogenicity, Set<Alteration>> geAlterationsWithHighestOncogenicity(Set<Alteration> alleles) {
        Map<Oncogenicity, Set<Alteration>> oncoCate = new HashMap<>();

        // Get oncogenicity info in alleles
        for (Alteration alt : alleles) {
            Set<EvidenceType> evidenceTypes = new HashSet<>();
            evidenceTypes.add(EvidenceType.ONCOGENIC);
            List<Evidence> allelesOnco = EvidenceUtils.getEvidence(Collections.singletonList(alt), evidenceTypes, null);

            for (Evidence evidence : allelesOnco) {
                String oncoStr = evidence.getKnownEffect();
                if (oncoStr == null)
                    continue;

                Oncogenicity oncogenicity = Oncogenicity.getByEffect(oncoStr);
                if (!oncoCate.containsKey(oncogenicity))
                    oncoCate.put(oncogenicity, new HashSet<Alteration>());

                oncoCate.get(oncogenicity).add(alt);
            }
        }
        return oncoCate;
    }

    private static Boolean appendThe(String queryAlteration, boolean isPlural) {
        Boolean appendThe = true;

        if (queryAlteration.toLowerCase().contains("deletion")
            || queryAlteration.toLowerCase().contains("amplification")
            || queryAlteration.toLowerCase().matches("gain")
            || queryAlteration.toLowerCase().matches("loss")
            || isPlural) {
            appendThe = false;
        }
        return appendThe;
    }

    private static Map<String, Object> getTumorTypeSummaryFromEvidences(List<Evidence> evidences) {
        Map<String, Object> summary = null;

        if (evidences != null && evidences.size() > 0) {
            evidences = EvidenceUtils.sortTumorTypeEvidenceBasedNumOfAlts(evidences, false);

            Evidence ev = evidences.get(0);
            String tumorTypeSummary = ev.getDescription();
            if (tumorTypeSummary != null) {
                summary = newTumorTypeSummary();
                summary.put("summary", tumorTypeSummary.trim());
                summary.put("lastEdit", ev.getLastEdit());
            }
        }
        return summary;
    }

    public static String getGeneMutationNameInVariantSummary(Gene gene, ReferenceGenome referenceGenome, String queryHugoSymbol, String queryAlteration) {
        StringBuilder sb = new StringBuilder();
        if (queryAlteration == null) {
            return "";
        } else {
            queryAlteration = queryAlteration.trim();
        }
        Alteration alteration = AlterationUtils.findAlteration(gene, referenceGenome, queryAlteration);
        if (alteration == null) {
            alteration = AlterationUtils.getAlteration(gene == null ? null : gene.getHugoSymbol(), queryAlteration, null, null, null, null, referenceGenome);
            AlterationUtils.annotateAlteration(alteration, queryAlteration);
        }
        if (AlterationUtils.isGeneralAlterations(queryAlteration, true)) {
            sb.append(queryHugoSymbol + " " + queryAlteration.toLowerCase());
        } else if (StringUtils.equalsIgnoreCase(queryAlteration, "gain")) {
            queryAlteration = "amplification (gain)";
            sb.append(queryHugoSymbol + " " + queryAlteration);
        } else if (StringUtils.equalsIgnoreCase(queryAlteration, "loss")) {
            queryAlteration = "deletion (loss)";
            sb.append(queryHugoSymbol + " " + queryAlteration);
        } else if (StringUtils.equalsIgnoreCase(queryAlteration, "fusion") || StringUtils.equalsIgnoreCase(queryAlteration, "fusions")) {
            sb.append(queryHugoSymbol + " " + queryAlteration.toLowerCase());
        } else if (StringUtils.containsIgnoreCase(queryAlteration, "fusion")) {
            queryAlteration = queryAlteration.replace("Fusion", "fusion");
            sb.append(queryAlteration);
        } else if (AlterationUtils.isGeneralAlterations(queryAlteration, false)
            || (alteration.getConsequence() != null
            && (alteration.getConsequence().getTerm().equals(IN_FRAME_DELETION)
            || alteration.getConsequence().getTerm().equals(IN_FRAME_INSERTION)))
            || StringUtils.containsIgnoreCase(queryAlteration, "indel")
            || StringUtils.containsIgnoreCase(queryAlteration, "dup")
            || StringUtils.containsIgnoreCase(queryAlteration, "del")
            || StringUtils.containsIgnoreCase(queryAlteration, "ins")
            || StringUtils.containsIgnoreCase(queryAlteration, "splice")
            || MainUtils.isEGFRTruncatingVariants(queryAlteration)
        ) {
            if (NamingUtils.hasAbbreviation(queryAlteration)) {
                sb.append(queryHugoSymbol).append(" ").append(NamingUtils.getFullName(queryAlteration)).append(" (").append(queryAlteration).append(")");
            } else {
                String mappedAltName = altNameShouldConvertToLowerCase(alteration) ? lowerCaseAlterationName(alteration.getName()) : alteration.getName();
                sb.append(queryHugoSymbol).append(" ").append(mappedAltName);
                String lman = ((mappedAltName.endsWith("s") && mappedAltName.length() > 2) ? mappedAltName.substring(0, mappedAltName.length() - 1) : mappedAltName).toLowerCase();
                List<String> matchedSpecialAlterations = specialAlterations.stream().filter(lman::contains).collect(Collectors.toList());
                if (matchedSpecialAlterations.size() == 0) {
                    sb.append(" alteration");
                }
            }
        } else {
            if (NamingUtils.hasAbbreviation(queryAlteration)) {
                sb.append(queryHugoSymbol).append(" ").append(NamingUtils.getFullName(queryAlteration)).append(" (").append(queryAlteration).append(")");
            } else {
                String mappedAltName = altNameShouldConvertToLowerCase(alteration) ? lowerCaseAlterationName(alteration.getName()) : alteration.getName();
                if ((gene != null && mappedAltName.contains(gene.getHugoSymbol())) || mappedAltName.contains(queryHugoSymbol)) {
                    sb.append(mappedAltName);
                } else {
                    sb.append(queryHugoSymbol).append(" ").append(mappedAltName);
                }
                String finalStr = sb.toString();
                if (specialAlterations.stream().noneMatch(sa -> finalStr.toLowerCase().contains(sa))) {
                    sb.append(" mutation");
                }
            }
        }
        return sb.toString();
    }

    public static String getGeneMutationNameInTumorTypeSummary(Gene gene, ReferenceGenome referenceGenome, String queryHugoSymbol, String queryAlteration) {
        StringBuilder sb = new StringBuilder();
        if (queryAlteration == null) {
            return "";
        } else {
            queryAlteration = queryAlteration.trim();
        }
        Alteration alteration = AlterationUtils.findAlteration(gene, referenceGenome, queryAlteration);
        if (alteration == null) {
            alteration = AlterationUtils.getAlteration(queryHugoSymbol, queryAlteration, null, null, null, null, referenceGenome);
        }
        if (StringUtils.containsIgnoreCase(queryAlteration, "fusion")) {
            if (queryAlteration.toLowerCase().equals("fusions")) {
                queryAlteration = queryHugoSymbol + " fusion";
            }
            queryAlteration = queryAlteration.replace("Fusion", "fusion");
            sb.append(queryAlteration + "-positive");
        } else if (StringUtils.equalsIgnoreCase(queryAlteration, "gain")
            || StringUtils.equalsIgnoreCase(queryAlteration, "amplification")) {
            queryAlteration = queryHugoSymbol + "-amplified";
            sb.append(queryAlteration);
        } else {
            if (!queryAlteration.contains(queryHugoSymbol)) {
                sb.append(queryHugoSymbol + " ");
            }
            if (AlterationUtils.isGeneralAlterations(queryAlteration, true)) {
                sb.append(queryAlteration.toLowerCase());
            } else if (StringUtils.equalsIgnoreCase(queryAlteration, "loss")) {
                queryAlteration = "deletion";
                sb.append(queryAlteration);
            } else if (AlterationUtils.isGeneralAlterations(queryAlteration, false)
                || (alteration.getConsequence() != null
                && (alteration.getConsequence().getTerm().equals(IN_FRAME_DELETION)
                || alteration.getConsequence().getTerm().equals(IN_FRAME_INSERTION)))
                || StringUtils.containsIgnoreCase(queryAlteration, "indel")
                || StringUtils.containsIgnoreCase(queryAlteration, "dup")
                || StringUtils.containsIgnoreCase(queryAlteration, "del")
                || StringUtils.containsIgnoreCase(queryAlteration, "ins")
                || StringUtils.containsIgnoreCase(queryAlteration, "splice")
                || NamingUtils.hasAbbreviation(queryAlteration)
                || MainUtils.isEGFRTruncatingVariants(queryAlteration)
            ) {
                sb.append(queryAlteration + " altered");
            } else if (!queryAlteration.endsWith("mutation")) {
                sb.append(queryAlteration + " mutant");
            }
        }
        return sb.toString();
    }


    public static String convertTumorTypeNameInSummary(String tumorType) {
        if (tumorType != null) {
            String[] specialWords = {"Wilms", "IgA", "IgG", "IgM", "Sezary", "Down", "Hodgkin", "Ewing", "Merkel"};
            List<String> specialWordsList = Arrays.asList(specialWords);
            String lowerCaseStr = tumorType.toLowerCase();

            StringBuilder sb = new StringBuilder(lowerCaseStr);

            for (String item : specialWordsList) {
                Integer startIndex = tumorType.indexOf(item);
                if (startIndex != -1) {
                    sb.replace(startIndex, startIndex + item.length(), item);
                }
            }

            // Find all uppercased string
            Pattern p = Pattern.compile("(\\b[A-Z0-9]+\\b)");
            Matcher m = p.matcher(tumorType);

            while (m.find()) {
                sb.replace(m.start(), m.end(), m.group(1));
            }

            tumorType = sb.toString();
        }

        if (tumorType != null) {
            tumorType = tumorType.trim();
            if (tumorType.endsWith(" tumor")) {
                tumorType = tumorType.substring(0, tumorType.lastIndexOf(" tumor")) + " tumors";
            }
        }
        return tumorType;
    }

    private static Map<String, Object> newTumorTypeSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("summary", "");
        return summary;
    }

    private static String getGeneArticle(Gene gene) {
        String[] vowels = {"A", "E", "I", "O", "U"};
        boolean isVowel = false;
        if (gene != null && gene.getHugoSymbol() != null) {
            for (int i = 0; i < vowels.length; i++) {
                if (gene.getHugoSymbol().startsWith(vowels[i])) {
                    isVowel = true;
                    break;
                }
            }
        }
        return isVowel ? "an" : "a";
    }
}
