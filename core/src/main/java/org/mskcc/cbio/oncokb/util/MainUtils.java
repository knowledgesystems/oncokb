package org.mskcc.cbio.oncokb.util;

import org.apache.commons.lang3.StringUtils;
import org.cbioportal.genome_nexus.model.GenomicLocation;
import org.mskcc.cbio.oncokb.apiModels.*;
import org.mskcc.cbio.oncokb.model.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Hongxin Zhang on 4/5/16.
 */
public class MainUtils {
    private static final List<Oncogenicity> PRIORITIZED_ONCOGENICITY = Collections.unmodifiableList(
        Arrays.asList(
            Oncogenicity.UNKNOWN,
            Oncogenicity.INCONCLUSIVE,
            Oncogenicity.LIKELY_NEUTRAL,
            Oncogenicity.RESISTANCE,
            Oncogenicity.LIKELY,
            Oncogenicity.YES
        )
    );
    private static final List<Pathogenicity> PRIORITIZED_PATHOGENICITY = Collections.unmodifiableList(
            Arrays.asList(
                    Pathogenicity.BENIGN,
                    Pathogenicity.LIKELY_BENIGN,
                    Pathogenicity.UNKNOWN,
                    Pathogenicity.LIKELY,
                    Pathogenicity.YES
            )
    );
    private static final List<MutationEffect> PRIORITIZED_MUTATION_EFFECTS = Collections.unmodifiableList(
        Arrays.asList(MutationEffect.GAIN_OF_FUNCTION,
            MutationEffect.LIKELY_GAIN_OF_FUNCTION,
            MutationEffect.LIKELY_SWITCH_OF_FUNCTION,
            MutationEffect.SWITCH_OF_FUNCTION,
            MutationEffect.LIKELY_LOSS_OF_FUNCTION,
            MutationEffect.LOSS_OF_FUNCTION,
            MutationEffect.INCONCLUSIVE,
            MutationEffect.LIKELY_NEUTRAL,
            MutationEffect.NEUTRAL,
            MutationEffect.UNKNOWN
        )
    );

    private static final List<AnnotationSearchQueryType> PRIORITIZED_QUERY_TYPES = Collections.unmodifiableList(
        Arrays.asList(
            AnnotationSearchQueryType.GENE,
            AnnotationSearchQueryType.VARIANT,
            AnnotationSearchQueryType.CANCER_TYPE,
            AnnotationSearchQueryType.DRUG
        )
    );

    public static Oncogenicity getCuratedAlterationOncogenicity(Alteration alteration) {
        List<Evidence> selfAltOncogenicEvis = EvidenceUtils.getEvidence(Collections.singletonList(alteration),
            Collections.singleton(EvidenceType.ONCOGENIC), null);
        if (selfAltOncogenicEvis != null) {
            Evidence highestOncogenicEvidenceByEvidence = MainUtils.findHighestOncogenicEvidenceByEvidences(new HashSet<>(selfAltOncogenicEvis));
            if (highestOncogenicEvidenceByEvidence != null) {
                return Oncogenicity.getByEffect(highestOncogenicEvidenceByEvidence.getKnownEffect());
            }
        }
        return null;
    }

    public static Pathogenicity getCuratedAlterationPathogenicity(Alteration alteration) {
        List<Evidence> selfAltOncogenicEvis = EvidenceUtils.getEvidence(Collections.singletonList(alteration),
                Collections.singleton(EvidenceType.PATHOGENIC), null);
        if (selfAltOncogenicEvis != null) {
            Evidence highestPathogenicEvidenceByEvidence = MainUtils.findHighestPathogenicEvidenceByEvidences(new HashSet<>(selfAltOncogenicEvis));
            if (highestPathogenicEvidenceByEvidence != null) {
                return Pathogenicity.getByEffect(highestPathogenicEvidenceByEvidence.getKnownEffect());
            }
        }
        return null;
    }

    public static boolean isEGFRTruncatingVariants(String alteration) {
        return alteration == null ? false : (alteration.trim().matches("^v(II|III|IV(a|b|c)|V)?$"));
    }

    public static MutationEffect findHighestMutationEffect(Set<MutationEffect> mutationEffect) {
        Integer index = 100;
        for (MutationEffect effect : mutationEffect) {
            if (PRIORITIZED_MUTATION_EFFECTS.indexOf(effect) < index) {
                index = PRIORITIZED_MUTATION_EFFECTS.indexOf(effect);
            }
        }
        return (index == 100 || index < 0) ? null : PRIORITIZED_MUTATION_EFFECTS.get(index);
    }

    public static IndicatorQueryMutationEffect findHighestMutationEffectByEvidence(Set<Evidence> evidences) {
        int index = 100;
        IndicatorQueryMutationEffect indicatorQueryMutationEffect = new IndicatorQueryMutationEffect();
        for (Evidence evidence : evidences) {
            MutationEffect mutationEffect = MutationEffect.getByName(evidence.getKnownEffect());
            int _index = PRIORITIZED_MUTATION_EFFECTS.indexOf(mutationEffect);
            if (_index >= 0 && _index < index) {
                indicatorQueryMutationEffect.setMutationEffect(mutationEffect);
                indicatorQueryMutationEffect.setMutationEffectEvidence(evidence);
                index = _index;
            }
        }
        return indicatorQueryMutationEffect;
    }

    public static Set<Evidence> getMutationEffectFromAlternativeAlleles(Set<Evidence> evidences) {
        final int DEFAULT_INDEX = 100;
        int index = DEFAULT_INDEX;
        Map<MutationEffect, Set<Evidence>> meMap = new HashMap<>();
        for (Evidence evidence : evidences) {
            MutationEffect mutationEffect = MutationEffect.getByName(evidence.getKnownEffect());
            if (mutationEffect == null) {
                continue;
            }
            MutationEffect likelyMutationEffect = getLikelyMutationEffect(mutationEffect);
            if (likelyMutationEffect != null) {
                mutationEffect = likelyMutationEffect;
            }
            if (!meMap.containsKey(mutationEffect)) {
                meMap.put(mutationEffect, new HashSet<>());
            }
            meMap.get(mutationEffect).add(evidence);
            int _index = PRIORITIZED_MUTATION_EFFECTS.indexOf(mutationEffect);
            if (_index >= 0 && _index < index) {
                index = _index;
            }
        }
        Set<MutationEffect> typesOfMutationEffect = meMap.keySet();
        typesOfMutationEffect.remove(MutationEffect.UNKNOWN);
        if (typesOfMutationEffect.size() > 1) {
            return new HashSet<>();
        }
        return index == DEFAULT_INDEX ? new HashSet<>() : meMap.get(PRIORITIZED_MUTATION_EFFECTS.get(index));
    }

    private static MutationEffect getLikelyMutationEffect(MutationEffect mutationEffect) {
        if (mutationEffect == null) {
            return null;
        }
        return MutationEffect.getByName("Likely " + mutationEffect.getMutationEffect().replaceAll("(?i)likely", "").trim());
    }

    public static MutationEffect setToAlternativeAlleleMutationEffect(MutationEffect mutationEffect) {
        if (mutationEffect != null) {
            MutationEffect likeME = getLikelyMutationEffect(mutationEffect);

            // likeME will be null if mutation effect without related likely mutation effect.
            if (likeME == null || likeME.equals(MutationEffect.LIKELY_NEUTRAL))
                return null;
            return likeME;
        }
        return null;
    }

    public static Oncogenicity findHighestOncogenicity(Set<Oncogenicity> oncogenicitySet) {
        Integer index = -1;

        for (Oncogenicity datum : oncogenicitySet) {
            if (datum != null) {
                Integer oncogenicIndex = PRIORITIZED_ONCOGENICITY.indexOf(datum);
                if (index < oncogenicIndex) {
                    index = oncogenicIndex;
                }
            }
        }
        return index == -1 ? null : PRIORITIZED_ONCOGENICITY.get(index);
    }

    public static Pathogenicity findHighestPathogenicity(Set<Pathogenicity> pathogenicitySet) {
        Integer index = -1;

        for (Pathogenicity datum : pathogenicitySet) {
            if (datum != null) {
                Integer pathogenicIndex = PRIORITIZED_PATHOGENICITY.indexOf(datum);
                if (index < pathogenicIndex) {
                    index = pathogenicIndex;
                }
            }
        }
        return index == -1 ? null : PRIORITIZED_PATHOGENICITY.get(index);
    }

    public static Evidence findHighestOncogenicityEvidence(List<Evidence> oncogenicitySet) {
        Integer index = -1;

        if (oncogenicitySet.size() == 0) {
            return null;
        }
        oncogenicitySet.sort((e1, e2) -> compareOncogenicity(Oncogenicity.getByEvidence(e1), Oncogenicity.getByEvidence(e2), true));
        for (Evidence evidence : oncogenicitySet) {
            if (EvidenceType.ONCOGENIC.equals(evidence.getEvidenceType())) {
                Oncogenicity oncogenicity = Oncogenicity.getByEvidence(evidence);
                Integer oncogenicIndex = PRIORITIZED_ONCOGENICITY.indexOf(oncogenicity);
                if (index < oncogenicIndex) {
                    index = oncogenicIndex;
                }
            }
        }
        return oncogenicitySet.get(0);
    }

    public static boolean manuallyAssignedTruncatingMutation(Query query) {
        return query.getAlteration().toLowerCase().contains("truncating mutation") && query.getSvType() != null;
    }

    public static Integer compareOncogenicity(Oncogenicity o1, Oncogenicity o2, Boolean asc) {
        if (asc == null) {
            asc = true;
        }
        if (o1 == null) {
            if (o2 == null)
                return 0;
            return asc ? 1 : -1;
        }
        if (o2 == null)
            return asc ? -1 : 1;
        return (PRIORITIZED_ONCOGENICITY.indexOf(o2) - PRIORITIZED_ONCOGENICITY.indexOf(o1)) * (asc ? 1 : -1);
    }

    public static Oncogenicity findHighestOncogenicByEvidences(Set<Evidence> evidences) {
        Set<Oncogenicity> oncogenicitySet = new HashSet<>();

        if (evidences != null) {
            for (Evidence evidence : evidences) {
                if (evidence.getKnownEffect() != null) {
                    oncogenicitySet.add(Oncogenicity.getByEffect(evidence.getKnownEffect()));
                }
            }
        }

        return findHighestOncogenicity(oncogenicitySet);
    }

    public static Pathogenicity findHighestPathogenicByEvidences(Set<Evidence> evidences) {
        Set<Pathogenicity> pathogenicitySet = new HashSet<>();

        if (evidences != null) {
            for (Evidence evidence : evidences) {
                if (evidence.getKnownEffect() != null) {
                    pathogenicitySet.add(Pathogenicity.getByEffect(evidence.getKnownEffect()));
                }
            }
        }

        return findHighestPathogenicity(pathogenicitySet);
    }

    public static Evidence findHighestOncogenicEvidenceByEvidences(Set<Evidence> evidences) {
        Oncogenicity oncogenicity = findHighestOncogenicByEvidences(evidences);
        Evidence evidencePicked = null;

        if (oncogenicity != null) {
            for (Evidence evidence : evidences) {
                if (evidence.getKnownEffect().equals(oncogenicity.getOncogenic())) {
                    if (evidencePicked == null) {
                        evidencePicked = evidence;
                    } else if (evidencePicked.getLastEdit() == null) {
                        evidencePicked = evidence;
                    } else if (evidence.getLastEdit() != null && evidence.getLastEdit().after(evidencePicked.getLastEdit())) {
                        evidencePicked = evidence;
                    }
                }
            }
        }
        return evidencePicked;
    }

    public static Evidence findHighestPathogenicEvidenceByEvidences(Set<Evidence> evidences) {
        Pathogenicity pathogenicity = findHighestPathogenicByEvidences(evidences);
        Evidence evidencePicked = null;

        if (pathogenicity != null) {
            for (Evidence evidence : evidences) {
                if (evidence.getKnownEffect().equals(pathogenicity.getPathogenic())) {
                    if (evidencePicked == null) {
                        evidencePicked = evidence;
                    } else if (evidencePicked.getLastEdit() == null) {
                        evidencePicked = evidence;
                    } else if (evidence.getLastEdit() != null && evidence.getLastEdit().after(evidencePicked.getLastEdit())) {
                        evidencePicked = evidence;
                    }
                }
            }
        }
        return evidencePicked;
    }

    public static Oncogenicity setToAlleleOncogenicity(Oncogenicity oncogenicity) {
        if (oncogenicity == null) {
            return null;
        }
        if (oncogenicity.equals(Oncogenicity.YES) || oncogenicity.equals(Oncogenicity.LIKELY)) {
            return Oncogenicity.LIKELY;
        } else {
            return null;
        }
    }

    public static String getAlleleConflictsMutationEffect(Set<String> mutationEffects) {
        Set<String> clean = new HashSet<>();

        for (String mutationEffect : mutationEffects) {
            if (mutationEffect != null) {
                mutationEffect = mutationEffect.replaceAll("(?i)likely", "");
                mutationEffect = mutationEffect.replaceAll("\\s", "");
                clean.add(mutationEffect);
            }
        }

        if (clean.size() > 1) {
            return "Unknown";
        } else if (clean.size() == 1) {
            return "Likely " + clean.iterator().next();
        } else {
            return "";
        }
    }


    public static Long getCurrentTimestamp() {
        return new Date().getTime();
    }

    public static Long getTimestampDiff(Long old) {
        return new Date().getTime() - old;
    }

    public static String getDataVersion() {
        return CacheUtils.getInfo().getDataVersion();
    }

    public static String getDataVersionDate() {
        Date dataVersionDate = CacheUtils.getInfo().getDataVersionDate();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        return dateFormat.format(dataVersionDate);
    }

    public static String listToString(List<String> list, String separator) {
        return listToString(list, separator, false);
    }

    public static String listToString(List<String> list, String separator, boolean sort) {
        if (list.isEmpty()) {
            return "";
        }

        if (sort) {
            Collections.sort(list);
        }

        int n = list.size();
        StringBuilder sb = new StringBuilder();
        sb.append(list.get(0));
        if (n == 1) {
            return sb.toString();
        }

        for (int i = 1; i < n; i++) {
            sb.append(separator).append(list.get(i));
        }

        return sb.toString();
    }

    public static String listToString(List<String> list) {
        if (list.isEmpty()) {
            return "";
        }

        int n = list.size();
        StringBuilder sb = new StringBuilder();
        sb.append(list.get(0));
        if (n == 1) {
            return sb.toString();
        } else if (n == 2) {
            return sb.append(" and ").append(list.get(1)).toString();
        }

        for (int i = 1; i < n - 1; i++) {
            sb.append(", ").append(list.get(i));
        }
        sb.append(" and ").append(list.get(n - 1));

        return sb.toString();
    }

    public static List<Integer> stringToIntegers(String ids) {
        if (ids == null) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        for (String id : ids.trim().split("\\s*,\\s*")) {
            Integer match = Integer.parseInt(id);

            if (match != null) {
                result.add(match);
            }
        }
        return result;
    }

    public static List<EvidenceType> stringToEvidenceTypes(String string, String separator) {
        List<EvidenceType> evidenceTypes = new ArrayList<>();
        if (string != null) {
            if (separator == null) {
                separator = ",";
            }
            for (String type : string.trim().split("\\s*" + separator + "\\s*")) {
                EvidenceType et = EvidenceType.valueOf(type);
                evidenceTypes.add(et);
            }
        }
        return evidenceTypes;
    }

    public static <T extends Enum<?>> T searchEnum(Class<T> enumeration,
                                                   String search) {
        for (T each : enumeration.getEnumConstants()) {
            if (each.name().compareToIgnoreCase(search) == 0) {
                return each;
            }
        }
        return null;
    }

    public static boolean isValidHotspotOncogenicity(Oncogenicity oncogenicity) {
        if (oncogenicity == null)
            return false;
        Set<Oncogenicity> oncogenicities = new HashSet<>();
        oncogenicities.add(Oncogenicity.YES);
        oncogenicities.add(Oncogenicity.LIKELY);
        oncogenicities.add(Oncogenicity.RESISTANCE);
        oncogenicities.add(Oncogenicity.LIKELY_NEUTRAL);
        oncogenicities.add(Oncogenicity.INCONCLUSIVE);

        return oncogenicities.contains(oncogenicity);
    }

    public static boolean isOncogenic(Oncogenicity oncogenicity) {
        return oncogenicity != null && (oncogenicity.equals(Oncogenicity.YES) || oncogenicity.equals(Oncogenicity.LIKELY) || oncogenicity.equals(Oncogenicity.RESISTANCE));
    }

    public static boolean isPathogenic(Pathogenicity pathogenicity) {
        return pathogenicity != null && (pathogenicity.equals(Pathogenicity.YES) || pathogenicity.equals(Pathogenicity.LIKELY));
    }

    public static Set<BiologicalVariant> getBiologicalVariants(Gene gene, boolean germline) {
        Set<BiologicalVariant> variants = new HashSet<>();
        if (gene != null) {
            List<Alteration> alterations = AlterationUtils.getAllAlterations(null, gene);

            Set<EvidenceType> evidenceTypes = new HashSet<EvidenceType>() {{
                add(EvidenceType.MUTATION_EFFECT);
            }};
            if(germline) {
                evidenceTypes.add(EvidenceType.PATHOGENIC);
                evidenceTypes.add(EvidenceType.VARIANT_PENETRANCE);
                evidenceTypes.add(EvidenceType.VARIANT_INHERITANCE_MECHANISM);
                evidenceTypes.add(EvidenceType.VARIANT_CANCER_RISK);
            } else {
                evidenceTypes.add(EvidenceType.ONCOGENIC);
            }
            Map<Alteration, Map<EvidenceType, Set<Evidence>>> evidences = new HashMap<>();

            for (Alteration alteration : alterations) {
                Map<EvidenceType, Set<Evidence>> map = new HashMap<>();
                map.put(EvidenceType.MUTATION_EFFECT, new HashSet<>());
                if (germline) {
                    map.put(EvidenceType.PATHOGENIC, new HashSet<>());
                    map.put(EvidenceType.VARIANT_PENETRANCE, new HashSet<>());
                    map.put(EvidenceType.VARIANT_INHERITANCE_MECHANISM, new HashSet<>());
                    map.put(EvidenceType.VARIANT_CANCER_RISK, new HashSet<>());
                } else {
                    map.put(EvidenceType.ONCOGENIC, new HashSet<>());
                }
                evidences.put(alteration, map);
            }

            Map<Gene, Set<Evidence>> geneEvidences =
                EvidenceUtils.getEvidenceByGenesAndEvidenceTypes(Collections.singleton(gene), evidenceTypes);

            for (Evidence evidence : geneEvidences.get(gene)) {
                if (evidence.getForGermline().equals(germline)) {
                    for (Alteration alteration : evidence.getAlterations()) {
                        if (evidences.containsKey(alteration)) {
                            evidences.get(alteration).get(evidence.getEvidenceType()).add(evidence);
                        }
                    }
                }
            }

            for (Map.Entry<Alteration, Map<EvidenceType, Set<Evidence>>> entry : evidences.entrySet()) {
                Alteration alteration = entry.getKey();
                Map<EvidenceType, Set<Evidence>> map = entry.getValue();

                BiologicalVariant variant = new BiologicalVariant();
                variant.setVariant(alteration);
                Oncogenicity oncogenicity = EvidenceUtils.getOncogenicityFromEvidence(map.get(EvidenceType.ONCOGENIC));
                Pathogenicity pathogenicity = EvidenceUtils.getPathogenicityFromEvidence(map.get(EvidenceType.PATHOGENIC));
                Set<Evidence> mutationEffectEvidences = map.get(EvidenceType.MUTATION_EFFECT);
                MutationEffect mutationEffect = EvidenceUtils.getMutationEffectFromEvidence(mutationEffectEvidences);
                if (oncogenicity != null || mutationEffect != null || pathogenicity != null) {
                    if (oncogenicity != null) {
                        variant.setOncogenic(oncogenicity.getOncogenic());
                        variant.setOncogenicPmids(EvidenceUtils.getPmids(map.get(EvidenceType.ONCOGENIC)));
                        variant.setOncogenicAbstracts(EvidenceUtils.getAbstracts(map.get(EvidenceType.ONCOGENIC)));
                    }
                    if (pathogenicity != null) {
                        variant.setPathogenic(pathogenicity.getPathogenic());
                        Set<Evidence> pathogenicityEvidences = map.get(EvidenceType.PATHOGENIC);
                        variant.setPathogenicPmids(EvidenceUtils.getPmids(pathogenicityEvidences));
                        variant.setPathogenicAbstracts(EvidenceUtils.getAbstracts(pathogenicityEvidences));

                        if (mutationEffectEvidences != null && mutationEffectEvidences.size() > 0) {
                            variant.setMutationEffectDescription(mutationEffectEvidences.iterator().next().getDescription());
                        }
                        if (map.get(EvidenceType.VARIANT_PENETRANCE) != null && !map.get(EvidenceType.VARIANT_PENETRANCE).isEmpty()) {
                            variant.setPenetrance(map.get(EvidenceType.VARIANT_PENETRANCE).iterator().next().getKnownEffect());
                        }
                        if (map.get(EvidenceType.VARIANT_INHERITANCE_MECHANISM) != null && !map.get(EvidenceType.VARIANT_INHERITANCE_MECHANISM).isEmpty()) {
                            variant.setPenetrance(map.get(EvidenceType.VARIANT_INHERITANCE_MECHANISM).iterator().next().getKnownEffect());
                        }
                        if (map.get(EvidenceType.VARIANT_CANCER_RISK) != null && !map.get(EvidenceType.VARIANT_CANCER_RISK).isEmpty()) {
                            variant.setPenetrance(map.get(EvidenceType.VARIANT_CANCER_RISK).iterator().next().getKnownEffect());
                        }
                    }
                    if (mutationEffect != null) {
                        variant.setMutationEffect(mutationEffect.getMutationEffect());
                        variant.setMutationEffectPmids(EvidenceUtils.getPmids(mutationEffectEvidences));
                        variant.setMutationEffectAbstracts(EvidenceUtils.getAbstracts(mutationEffectEvidences));
                        variant.setMutationEffectDescription(mutationEffectEvidences.iterator().next().getDescription());
                    }
                    variants.add(variant);
                }
            }
        }
        return variants;
    }

    private static Alteration createSpecialAlteration(Gene gene, String proteinChange) {
        Alteration alt = new Alteration();
        alt.setGene(gene);
        alt.setName(proteinChange);
        alt.setAlteration(proteinChange);
        alt.setProteinStart(AlterationPositionBoundary.START.getValue());
        alt.setProteinEnd(AlterationPositionBoundary.END.getValue());
        Set<ReferenceGenome> referenceGenomes = new HashSet<>();
        referenceGenomes.add(ReferenceGenome.GRCh37);
        referenceGenomes.add(ReferenceGenome.GRCh38);
        alt.setReferenceGenomes(referenceGenomes);
        return alt;
    }

    public static Evidence convertSpecialESR1Evidence(Evidence evidence) {
        if (evidence.getUuid() != null && evidence.getUuid().equals("f2527ea6-c1ca-4629-a517-8456addfaf99")) {
            Alteration esr1Alt = createSpecialAlteration(evidence.getGene(), "Oncogenic Ligand-Binding Domain Missense Mutations");
            evidence = new Evidence(evidence, null);
            evidence.setAlterations(Collections.singleton(esr1Alt));
        } else if (evidence.getUuid() != null && evidence.getUuid().equals("0e5d873c-c96e-4c98-a4fb-ff66690e86e8")) {
            Alteration esr1Alt = createSpecialAlteration(evidence.getGene(), "Oncogenic Ligand-Binding Domain In-Frame Insertions or Deletions");
            evidence = new Evidence(evidence, null);
            evidence.setAlterations(Collections.singleton(esr1Alt));
        }
        return evidence;
    }

    public static Set<ClinicalVariant> getClinicalVariants(Gene gene, boolean germline) {
        Set<ClinicalVariant> variants = new HashSet<>();
        if (gene != null) {
            List<Alteration> alterations;
            alterations = AlterationUtils.excludeVUS(gene, new ArrayList<>(AlterationUtils.getAllAlterations(null, gene)));
            Set<EvidenceType> evidenceTypes = EvidenceTypeUtils.getImplicationEvidenceTypes();
            Map<Alteration, Map<LevelOfEvidence, Set<Evidence>>> evidences = new HashMap<>();
            Set<LevelOfEvidence> publicLevels = LevelUtils.getPublicLevels();

            for (Alteration alteration : alterations) {
                evidences.put(alteration, new HashMap<>());
            }

            Map<Gene, Set<Evidence>> geneEvidences =
                EvidenceUtils.getEvidenceByGenesAndEvidenceTypes(Collections.singleton(gene), evidenceTypes);

            for (Evidence evidence : geneEvidences.get(gene)) {
                if (!evidence.getCancerTypes().isEmpty() && evidence.getForGermline().equals(germline)) {
                    if (evidence.getGene().getHugoSymbol().equals("ESR1")) {
                        evidence = convertSpecialESR1Evidence(evidence);
                        for (Alteration alteration : evidence.getAlterations()) {
                            if (!evidences.containsKey(alteration)) {
                                evidences.put(alteration, new HashMap<>());
                            }
                        }
                    }
                    for (Alteration alteration : evidence.getAlterations()) {
                        if (evidences.containsKey(alteration)) {
                            if (publicLevels.contains(evidence.getLevelOfEvidence())) {
                                LevelOfEvidence levelOfEvidence = evidence.getLevelOfEvidence();
                                if (!evidences.get(alteration).containsKey(levelOfEvidence)) {
                                    evidences.get(alteration).put(levelOfEvidence, new HashSet<Evidence>());
                                }
                                evidences.get(alteration).get(levelOfEvidence).add(evidence);
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Alteration, Map<LevelOfEvidence, Set<Evidence>>> entry : evidences.entrySet()) {
                Alteration alteration = entry.getKey();
                IndicatorQueryOncogenicity oncogenicity = IndicatorUtils.getOncogenicity(alteration, AlterationUtils.getAlleleAlterations(null, alteration), AlterationUtils.getRelevantAlterations(null, alteration));
                String oncogenicityString = null;
                if (oncogenicity.getOncogenicity() != null) {
                    oncogenicityString = oncogenicity.getOncogenicity().getOncogenic();
                }

                for (Map.Entry<LevelOfEvidence, Set<Evidence>> __entry : entry.getValue().entrySet()) {
                    for (Evidence evidence : __entry.getValue()) {
                        ClinicalVariant variant = new ClinicalVariant();
                        variant.setCancerTypes(evidence.getCancerTypes());
                        variant.setExcludedCancerTypes(evidence.getExcludedCancerTypes());
                        variant.setVariant(alteration);
                        variant.setOncogenic(oncogenicityString);
                        variant.setLevel(evidence.getLevelOfEvidence().getLevel());
                        if (evidence.getFdaLevel() != null) {
                            variant.setFdaLevel(evidence.getFdaLevel().getLevel());
                        }
                        variant.setDrug(EvidenceUtils.getDrugs(Collections.singleton(evidence)));
                        variant.setDrugPmids(EvidenceUtils.getPmids(Collections.singleton(evidence)));
                        variant.setDrugAbstracts(EvidenceUtils.getAbstracts(Collections.singleton(evidence)));
                        variant.setDrugDescription(CplUtils.annotate(
                            evidence.getDescription(),
                            gene.getHugoSymbol(),
                            variant.getVariant().getName(),
                            null,
                            null,
                            gene,
                            null
                        ));
                        variants.add(variant);
                    }
                }
            }
        }
        return variants;
    }

    public static boolean containsCaseInsensitive(String s, Set<String> l) {
        for (String string : l) {
            if (string.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    public static String getCurrentTime() {
        return getCurrentTime(null);
    }

    public static String getCurrentTime(String timeFormat) {
        if (timeFormat == null) {
            timeFormat = "MM/dd/yyy hh:mm:ss";
        }
        return new SimpleDateFormat(timeFormat).format(new Date());
    }

    public static String getTimeByDate(Date date) {
        return getTimeByDate(date, null);
    }

    public static String getTimeByDate(Date date, String timeFormat) {
        if (date == null)
            return null;
        if (timeFormat == null) {
            timeFormat = "MM/dd/yyy hh:mm:ss";
        }
        return new SimpleDateFormat(timeFormat).format(date);
    }

    public static Boolean isVUS(Alteration alteration) {
        List<Evidence> evidenceList = EvidenceUtils.getEvidence(Collections.singletonList(alteration), Collections.singleton(EvidenceType.VUS), null);
        return !(evidenceList == null || evidenceList.isEmpty());
    }

    public static Map<String, Boolean> validateTrials(List<String> nctIds) throws ParserConfigurationException, SAXException, IOException {
        Map<String, Boolean> result = new HashMap<>();
        if (nctIds == null || nctIds.size() == 0) {
            return result;
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        for (String nctId : nctIds) {
            String strUrl = "https://clinicaltrials.gov/show/" + nctId + "?displayxml=true";
            try {
                Document doc = db.parse(strUrl);
                result.put(nctId, true);
            } catch (IOException e) {
                result.put(nctId, false);
            }
        }
        return result;
    }

    public static Citations getCitationsByEvidence(Evidence evidence) {
        Citations citations = new Citations();
        if (evidence.getArticles() == null) {
            return citations;
        }
        for (Article article : evidence.getArticles()) {
            if (article.getPmid() != null) {
                citations.getPmids().add(article.getPmid());
            }
            if (article.getAbstractContent() != null) {
                ArticleAbstract articleAbstract = new ArticleAbstract();
                articleAbstract.setAbstractContent(article.getAbstractContent());
                articleAbstract.setLink(article.getLink());
                citations.getAbstracts().add(articleAbstract);
            }
        }
        return citations;
    }

    public static void sortAnnotatedVariants(List<AnnotatedVariant> variants) {
        Collections.sort(variants, new Comparator<AnnotatedVariant>() {
            @Override
            public int compare(AnnotatedVariant a1, AnnotatedVariant a2) {
                // Gene
                int result = a1.getGene().compareTo(a2.getGene());

                // Alteration
                if (result == 0) {
                    result = a1.getVariant().compareTo(a2.getVariant());

                    // Oncogenicity
                    if (result == 0) {
                        result = MainUtils.compareOncogenicity(
                            Oncogenicity.getByEffect(a1.getOncogenicity()),
                            Oncogenicity.getByEffect(a2.getOncogenicity()),
                            true
                        );

                        // Mutation Effect
                        if (result == 0) {
                            result = a1.getMutationEffect() == null ? 1 : a1.getMutationEffect().compareTo(a2.getMutationEffect());
                        }
                    }
                }
                return result;
            }
        });
    }

    public static void sortVusVariants(List<VariantOfUnknownSignificance> variants) {
        Collections.sort(variants, Comparator.comparing(VariantOfUnknownSignificance::getGene).thenComparing(VariantOfUnknownSignificance::getVariant));
    }

    public static void sortActionableVariants(List<ActionableGene> variants) {
        Collections.sort(variants, new Comparator<ActionableGene>() {
            @Override
            public int compare(ActionableGene a1, ActionableGene a2) {
                // Level
                int result = LevelUtils.compareLevel(
                    LevelOfEvidence.getByLevel(a1.getLevel()),
                    LevelOfEvidence.getByLevel(a2.getLevel())
                );
                // Gene
                if (result == 0) {
                    result = a1.getGene().compareTo(a2.getGene());

                    // Cancer Type
                    if (result == 0) {
                        result = a1.getCancerType().compareTo(a2.getCancerType());

                        // Alteration
                        if (result == 0) {
                            result = a1.getVariant().compareTo(a2.getVariant());
                        }
                    }
                }
                return result;
            }
        });
    }

    public static void sortCuratedGenes(List<CuratedGene> genes) {
        Collections.sort(genes, new Comparator<CuratedGene>() {
            @Override
            public int compare(CuratedGene g1, CuratedGene g2) {
                return g1.getHugoSymbol().compareTo(g2.getHugoSymbol());
            }
        });
    }

    public static String replaceLast(String text, String regex, String replacement) {
        // the code is from https://stackoverflow.com/a/2282998
        return text.replaceFirst("(?s)" + regex + "(?!.*?" + regex + ")", replacement);
    }

    public static boolean rangesIntersect(Integer start1, Integer end1, Integer start2, Integer end2) {
        if (start1 == null) {
            start1 = Integer.MIN_VALUE;
        }
        if (start2 == null) {
            start2 = Integer.MIN_VALUE;
        }
        if (end1 == null) {
            end1 = Integer.MAX_VALUE;
        }
        if (end2 == null) {
            end2 = Integer.MAX_VALUE;
        }
        if (start1 > end2 || end1 < start2) {
            return false;
        } else {
            return true;
        }
    }

    public static String removeDigits(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\d", "");
    }

    public static boolean altNameShouldConvertToLowerCase(Alteration alteration) {
        if (!Objects.equals(alteration.getName(), alteration.getAlteration())) {
            return true;
        } else {
            return (alteration.getProteinStart() == null || alteration.getProteinStart() == -1) && !isEGFRTruncatingVariants(alteration.getAlteration());
        }
    }

    public static String lowerCaseAlterationName(String text) {
        Pattern pattern = Pattern.compile("[A-Z]{2,}|\\b[A-Z]+\\b");
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        int currentIndex = 0;
        while (matcher.find()) {
            sb.append(text.substring(currentIndex, matcher.start()).toLowerCase());
            sb.append(text.substring(matcher.start(), matcher.end()));
            currentIndex = matcher.end();
        }
        if (currentIndex != text.length() - 1) {
            sb.append(text.substring(currentIndex).toLowerCase());
        } else if (sb.length() != text.length()) {
            sb.append(text.toLowerCase());
        }
        return sb.toString();
    }

    public static <T> LinkedHashSet<T> getLimit(LinkedHashSet<T> result, Integer limit) {
        final Integer DEFAULT_RETURN_LIMIT = 5;
        if (limit == null)
            limit = DEFAULT_RETURN_LIMIT;
        Integer count = 0;
        LinkedHashSet<T> firstFew = new LinkedHashSet<>();
        Iterator<T> itr = result.iterator();
        while (itr.hasNext() && count < limit) {
            firstFew.add(itr.next());
            count++;
        }
        return firstFew;
    }

    public static Integer compareAnnotationSearchQueryType(AnnotationSearchQueryType q1, AnnotationSearchQueryType q2, Boolean asc) {
        if (asc == null) {
            asc = true;
        }
        if (q1 == null) {
            if (q2 == null)
                return 0;
            return asc ? 1 : -1;
        }
        if (q2 == null)
            return asc ? -1 : 1;
        return (PRIORITIZED_QUERY_TYPES.indexOf(q1) - PRIORITIZED_QUERY_TYPES.indexOf(q2)) * (asc ? 1 : -1);
    }

    public static int findDigitEndIndex(String str, int startIndex) {
        if (startIndex < 0) {
            return -1;
        }
        int i = startIndex;
        while (i < str.length() && Character.isDigit(str.charAt(i))) {
            i++;
        }
        return i == startIndex ? -1 : i;
    }

    /**
     * We don't intend to check if the HGVSg is valid. If you need a method that validates and parses,
     * then use the NotationConverter.parseHGVSg() method.
     * @param hgvsg
     * @return null if cannot parse, otherwise the GenomicLocation
     */
    public static GenomicLocation parseChromosomeAndRangeFromHGVSg(String hgvsg) {
        if (hgvsg == null) {
            return null;
        }
        GenomicLocation location = new GenomicLocation();
        int start, end;

        // Step 1: Split by ":g."
        String[] parts = hgvsg.split(":g\\.");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        location.setChromosome(parts[0]);  // Chromosome is the part before ":g."
        String coordinates = parts[1];  // This is the part after "g."

        // Step 2: Handle the coordinates part
        int underscoreIndex = coordinates.indexOf('_');
        try {
            if (underscoreIndex != -1) {
                // If there is an underscore, we have both start and end values
                start = Integer.parseInt(coordinates.substring(0, underscoreIndex));
                // Find where the digits after the underscore end (before any letters)
                int endIndex = findDigitEndIndex(coordinates, underscoreIndex + 1);
                end = Integer.parseInt(coordinates.substring(underscoreIndex + 1, endIndex));
            } else {
                // No underscore means start = end
                int endIndex = findDigitEndIndex(coordinates, 0);
                start = Integer.parseInt(coordinates.substring(0, endIndex));
                end = start;
            }
            location.setStart(start);
            location.setEnd(end);
        } catch (NumberFormatException exception) {
            return null;
        } catch (IndexOutOfBoundsException exception) {
            return null;
        }
        return location;
    }
}
