/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.dao;

import java.util.List;
import java.util.Map;

import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.clinicalTrialsMatching.Tumor;

/**
 *
 * @author jgao
 */
public interface EvidenceDao extends GenericDao<Evidence, Integer> {

    /**
     * Find AlterationActivityEvidences by alterations
     * @param alteration
     * @return
     */
    List<Evidence> findEvidencesByAlteration(Alteration alteration);

    /**
     *
     * @param alteration
     * @param tumorType
     * @return
     */
    List<Evidence> findEvidencesByAlterationAndTumorType(Alteration alteration, TumorType tumorType);


    /**
     *
     * @param alterations
     * @param tumorTypes
     * @param evidenceTypes
     * @return
     */
    List<Evidence> findEvidencesByAlterationsAndTumorTypesAndEvidenceTypes(List<Alteration> alterations, List<TumorType> tumorTypes, List<EvidenceType> evidenceTypes);

    /**
     *
     * @param alterations
     * @param tumorTypes
     * @param evidenceTypes
     * @param levelOfEvidences
     * @return
     */
    List<Evidence> findEvidencesByAlterationsAndTumorTypesAndEvidenceTypesAndLevelOfEvidence(List<Alteration> alterations, List<TumorType> tumorTypes, List<EvidenceType> evidenceTypes, List<LevelOfEvidence> levelOfEvidences);

    /**
     * Find AlterationActivityEvidences by alterations
     * @param alteration
     * @param evidenceType
     * @return
     */
    List<Evidence> findEvidencesByAlteration(Alteration alteration, EvidenceType evidenceType);

    /**
     * Find AlterationActivityEvidences by alterations
     * @param alteration
     * @param evidenceType
     * @return
     */
    List<Evidence> findEvidencesByAlterationAndLevels(Alteration alteration, EvidenceType evidenceType, LevelOfEvidence levelOfEvidence);

    /**
     * General searching between cancerType and subtype
     * @param alteration
     * @param evidenceType
     * @param tumorType
     * @return
     */
    List<Evidence> findEvidencesByAlterationAndTumorType(Alteration alteration, EvidenceType evidenceType, TumorType tumorType);

    /**
     * Find AlterationActivityEvidences by Entrez Gene ID
     * @param gene
     * @return
     */
    List<Evidence> findEvidencesByGene(Gene gene);

    /**
     *
     * @param gene
     * @param evidenceType
     * @return
     */
    List<Evidence> findEvidencesByGene(Gene gene, EvidenceType evidenceType);

    /**
     *
     * @param ids
     * @return
     */
    List<Evidence> findEvidencesByIds(List<Integer> ids);

    /**
     *
     * @param alterations
     * @return
     */
    List<Object> findTumorTypesWithEvidencesForAlterations(List<Alteration> alterations);

    /**
     *
     * @param alterations
     * @return
     */
    List<Object> findCancerTypesWithEvidencesForAlterations(List<Alteration> alterations);

    /**
     *
     * @param alterations
     * @return
     */
    List<Object> findSubtypesWithEvidencesForAlterations(List<Alteration> alterations);

    List<Evidence> findEvidenceByUUIDs(List<String> uuids);
}
