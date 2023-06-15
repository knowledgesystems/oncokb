package org.mskcc.cbio.oncokb.bo.impl;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.bo.EvidenceBo;
import org.mskcc.cbio.oncokb.dao.EvidenceDao;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.util.CacheUtils;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.mskcc.cbio.oncokb.model.clinicalTrialsMatching.Tumor;
import org.mskcc.cbio.oncokb.util.EvidenceUtils;
import org.mskcc.cbio.oncokb.util.TumorTypeUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jgao
 */
public class EvidenceBoImpl extends GenericBoImpl<Evidence, EvidenceDao> implements EvidenceBo {

    @Override
    public List<Evidence> findEvidencesByAlteration(Collection<Alteration> alterations) {
        Set<Evidence> set = new LinkedHashSet<Evidence>();
        Set<Alteration> alterationSet = new HashSet<>(alterations);
        List<Evidence> evidences = EvidenceUtils.getAllEvidencesByAlterationsGenes(alterations);
        for (int i = 0; i < evidences.size(); i++) {
            Evidence evidence = evidences.get(i);
            if (!Collections.disjoint(evidence.getAlterations(), alterationSet)) {
                set.add(evidence);
            }
        }
        return new ArrayList<>(set);
    }

    @Override
    public List<Evidence> findEvidencesByAlteration(Collection<Alteration> alterations, Collection<EvidenceType> evidenceTypes) {
        Set<Evidence> set = new LinkedHashSet<>();
        List<Evidence> evidences = EvidenceUtils.getAllEvidencesByAlterationsGenes(alterations);
        for (int i = 0; i < evidences.size(); i++) {
            Evidence evidence = evidences.get(i);
            if (evidenceTypes.contains(evidence.getEvidenceType()) && !Collections.disjoint(evidence.getAlterations(), alterations)) {
                set.add(evidence);
            }
        }
        return new ArrayList<>(set);
    }

    @Override
    public List<Evidence> findEvidencesByAlterationWithLevels(Collection<Alteration> alterations, Collection<EvidenceType> evidenceTypes, Collection<LevelOfEvidence> levelOfEvidences) {
        if (evidenceTypes == null) {
            return findEvidencesByAlteration(alterations, evidenceTypes);
        }
        Set<Evidence> set = new LinkedHashSet<>();
        List<Evidence> evidences = EvidenceUtils.getAllEvidencesByAlterationsGenes(alterations);
        for (int i = 0; i < evidences.size(); i++) {
            Evidence evidence = evidences.get(i);
            if (Sets.intersection(evidence.getAlterations(), new HashSet(alterations)).size() > 0
                && evidenceTypes.contains(evidence.getEvidenceType())
                && levelOfEvidences.contains(evidence.getLevelOfEvidence())) {
                set.add(evidence);
            }
        }
        return new ArrayList<>(set);
    }

    @Override
    public List<Evidence> findEvidencesByAlteration(Collection<Alteration> alterations, Collection<EvidenceType> evidenceTypes, TumorType matchedTumorType, List<TumorType> tumorTypes) {
        if (matchedTumorType == null && tumorTypes == null) {
            if (evidenceTypes == null) {
                return findEvidencesByAlteration(alterations);
            }
            return findEvidencesByAlteration(alterations, evidenceTypes);
        }

        Set<Evidence> alterationEvidences = new HashSet<>(findEvidencesByAlteration(alterations, evidenceTypes));
        List<Evidence> evidences = new ArrayList<>();
        for (Evidence evidence : alterationEvidences) {
            boolean hasJointOnSubtype = !Collections.disjoint(TumorTypeUtils.findEvidenceRelevantCancerTypes(evidence), matchedTumorType == null ? tumorTypes : Collections.singleton(matchedTumorType));
            if (hasJointOnSubtype) {
                evidences.add(evidence);
            }
        }

        // Now all evidences left are relevant to the matchedTumorType or tumorTypes.
        // We need to rank the evidences based on cancer type relevancy
        List<TumorType> relevantMatchedTumorTypes = new ArrayList<>();
        if (matchedTumorType != null) {
            relevantMatchedTumorTypes = TumorTypeUtils.findRelevantTumorTypes(
                TumorTypeUtils.getTumorTypeName(matchedTumorType),
                StringUtils.isEmpty(matchedTumorType.getSubtype()),
                RelevantTumorTypeDirection.UPWARD
            );
        } else if (tumorTypes != null) {
            relevantMatchedTumorTypes = tumorTypes;
        }

        List<Evidence> sortedEvidences = new ArrayList<>();
        for (TumorType tumorType : relevantMatchedTumorTypes) {
            for (int i = 0; i < evidences.size(); i++) {
                Evidence evidence = evidences.get(i);
                if (evidence.getCancerTypes().contains(tumorType)) {
                    sortedEvidences.add(evidence);
                    evidences.remove(evidence);
                }
            }
        }
        sortedEvidences.addAll(evidences);
        return sortedEvidences;
    }

    @Override
    public List<Evidence> findEvidencesByAlteration(Collection<Alteration> alterations, Collection<EvidenceType> evidenceTypes, TumorType matchedTumorType, List<TumorType> tumorTypes, Collection<LevelOfEvidence> levelOfEvidences) {
        return findEvidencesByAlteration(alterations, evidenceTypes, matchedTumorType, tumorTypes).stream().filter(evidence -> levelOfEvidences.contains(evidence.getLevelOfEvidence())).collect(Collectors.toList());
    }

    @Override
    public List<Evidence> findEvidencesByGene(Collection<Gene> genes) {
        Set<Evidence> set = new LinkedHashSet<Evidence>();
        for (Gene gene : genes) {
            set.addAll(CacheUtils.getEvidences(gene));
        }
        return new ArrayList<Evidence>(set);
    }

    @Override
    public List<Evidence> findEvidencesByGeneFromDB(Collection<Gene> genes) {
        Set<Evidence> set = new LinkedHashSet<>();
        for (Gene gene : genes) {
            set.addAll(getDao().findEvidencesByGene(gene));
        }
        return new ArrayList<>(set);
    }

    @Override
    public List<Evidence> findEvidencesByGene(Collection<Gene> genes, Collection<EvidenceType> evidenceTypes) {
        Set<Evidence> set = new LinkedHashSet<Evidence>();
        for (Gene gene : genes) {
            for (Evidence evidence : CacheUtils.getEvidences(gene)) {
                if (evidenceTypes.contains(evidence.getEvidenceType())) {
                    set.add(evidence);
                }
            }
        }
        return new ArrayList<Evidence>(set);
    }

    @Override
    public List<Evidence> findEvidencesByGene(Collection<Gene> genes, Collection<EvidenceType> evidenceTypes, Collection<TumorType> tumorTypes) {
        Set<Evidence> set = new LinkedHashSet<Evidence>();
        for (Gene gene : genes) {
            set.addAll(CacheUtils.getEvidences(gene).stream().filter(evidence -> evidenceTypes.contains(evidence.getEvidenceType()) && !Collections.disjoint(TumorTypeUtils.findEvidenceRelevantCancerTypes(evidence), tumorTypes)).collect(Collectors.toList()));
        }
        return new ArrayList<>(set);
    }

    @Override
    public List<Evidence> findEvidencesByIds(List<Integer> ids) {
        List<Evidence> evidences = new ArrayList<>();
        for (Evidence evidence : CacheUtils.getAllEvidences()) {
            if (ids.contains(evidence.getId())) {
                evidences.add(evidence);
            }
            if (evidences.size() == ids.size()) {
                break;
            }
        }
        return evidences;
    }

    @Override
    public List<Drug> findDrugsByAlterations(Collection<Alteration> alterations) {
        List<Evidence> evidences = new ArrayList<Evidence>();

        Set<EvidenceType> evidenceTypes = new HashSet<>();
        evidenceTypes.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
        evidenceTypes.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);
        evidences = findEvidencesByAlteration(alterations, evidenceTypes);


        Set<Drug> set = new HashSet<Drug>();
        for (Evidence ev : evidences) {
            for (Treatment t : ev.getTreatments()) {
                set.addAll(t.getDrugs());
            }
        }
        return new ArrayList<Drug>(set);
    }

    @Override
    public List<Object> findTumorTypesWithEvidencesForAlterations(List<Alteration> alterations) {
        return getDao().findTumorTypesWithEvidencesForAlterations(alterations);
    }

    @Override
    public List<Object> findCancerTypesWithEvidencesForAlterations(List<Alteration> alterations) {
        return getDao().findCancerTypesWithEvidencesForAlterations(alterations);
    }

    @Override
    public List<Object> findSubtypesWithEvidencesForAlterations(List<Alteration> alterations) {
        return getDao().findSubtypesWithEvidencesForAlterations(alterations);
    }

    @Override
    public List<Evidence> findEvidenceByUUIDs(List<String> uuids) {
        return new ArrayList<>(CacheUtils.getEvidencesByUUIDs(new HashSet<>(uuids)));
    }
}
