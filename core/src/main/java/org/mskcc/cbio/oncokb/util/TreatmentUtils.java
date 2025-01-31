/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mskcc.cbio.oncokb.util;

import java.util.*;
import org.mskcc.cbio.oncokb.model.*;

/**
 * @author zhangh2
 */
public final class TreatmentUtils {
    private TreatmentUtils() {
        throw new AssertionError();
    }

    public static Set<Treatment> getTreatmentsByGene(Gene gene, boolean germline) {
        if (gene == null) {
            return new HashSet<>();
        }
        List<Alteration> alterations = AlterationUtils.getAllAlterations(null, gene);
        List<Evidence> evidences = EvidenceUtils.getEvidence(new ArrayList<Alteration>(alterations),
            EvidenceTypeUtils.getTreatmentEvidenceTypes(), null);
        Set<Treatment> treatments = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (evidence.getForGermline().equals(germline))
                treatments.addAll(evidence.getTreatments());
        }
        return treatments;
    }

    public static Set<Treatment> getTreatmentsByLevels(Set<LevelOfEvidence> levels, boolean germline) {
        if (levels == null || levels.size() == 0) {
            return new HashSet<>();
        }
        return getTreatmentsByGenesAndLevels(CacheUtils.getAllGenes(), levels, germline);
    }

    public static String getTreatmentName(Set<Treatment> treatments) {
        List<String> treatmentNames = getTreatments(treatments);
        return MainUtils.listToString(treatmentNames, ", ");
    }

    public static List<Treatment> sortTreatmentsByName(List<Treatment> treatments) {
        Collections.sort(treatments, new Comparator<Treatment>() {
            public int compare(Treatment t1, Treatment t2) {
                return t1.getName().compareTo(t2.getName());
            }
        });
        return treatments;
    }

    public static void sortTreatmentsByPriority(List<Treatment> treatments) {
        Collections.sort(treatments, new TreatmentComparatorByPriority());
    }

    public static List<String> getTreatments(Set<Treatment> treatments) {
        List<String> treatmentNames = new ArrayList<>();
        List<Treatment> sortedTreatment = new ArrayList<>(treatments);
        sortTreatmentsByPriority(sortedTreatment);

        for (Treatment treatment : sortedTreatment) {
            List<String> drugNames = new ArrayList<>();
            for (Drug drug : treatment.getDrugs()) {
                if (drug.getDrugName() != null) {
                    drugNames.add(drug.getDrugName().trim());
                }
            }
            treatmentNames.add(MainUtils.listToString(drugNames, "+"));
        }
        return treatmentNames;
    }

    public static Set<Treatment> getTreatmentsByGeneAndLevels(Gene gene, Set<LevelOfEvidence> levels, boolean germline) {
        if (levels == null) {
            return getTreatmentsByGene(gene, germline);
        }
        if (gene == null) {
            getTreatmentsByLevels(levels, germline);
        }
        return getTreatmentsByGenesAndLevels(Collections.singleton(gene), levels, germline);
    }

    private static Set<Treatment> getTreatmentsByGenesAndLevels(Set<Gene> genes, Set<LevelOfEvidence> levels, boolean germline) {
        Map<Gene, Set<Evidence>> allEvidence = EvidenceUtils.getEvidenceByGenes(genes);
        Set<Treatment> treatments = new HashSet<>();

        Iterator iterator = allEvidence.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Gene, Set<Evidence>> map = (Map.Entry) iterator.next();
            for (Evidence evidence : map.getValue()) {
                if (evidence.getLevelOfEvidence() != null
                    && levels.contains(evidence.getLevelOfEvidence())) {
                    treatments.addAll(evidence.getTreatments());
                }
            }
        }

        return treatments;
    }

    public static Integer compareTreatmentEvidenceByTumorTypesName(Evidence t1, Evidence t2) {
        return TumorTypeUtils.getTumorTypesName(t1.getCancerTypes()).compareTo(TumorTypeUtils.getTumorTypesName(t2.getCancerTypes()));
    }
}


class TreatmentComparatorByPriority implements Comparator<Treatment> {
    @Override
    public int compare(Treatment t1, Treatment t2) {
        if (t1.getPriority() == null) {
            if (t2.getPriority() == null) {
                int nameComparison = TreatmentUtils.getTreatmentName(Collections.singleton(t1)).compareTo(TreatmentUtils.getTreatmentName(Collections.singleton(t2)));
                if (nameComparison == 0) {
                    return TreatmentUtils.compareTreatmentEvidenceByTumorTypesName(t1.getEvidence(), t2.getEvidence());
                } else {
                    return nameComparison;
                }
            } else {
                return 1;
            }
        }
        if (t2.getPriority() == null) {
            return -1;
        }

        if (t1.getPriority().equals(t2.getPriority())) {
            int nameComparison = TreatmentUtils.getTreatmentName(Collections.singleton(t1)).compareTo(TreatmentUtils.getTreatmentName(Collections.singleton(t2)));
            if (nameComparison == 0) {
                return TreatmentUtils.compareTreatmentEvidenceByTumorTypesName(t1.getEvidence(), t2.getEvidence());
            } else {
                return nameComparison;
            }
        }
        return t1.getPriority() - t2.getPriority();
    }
}
