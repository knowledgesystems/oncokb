/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.dao.impl;

import org.mskcc.cbio.oncokb.dao.EvidenceDao;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.clinicalTrialsMatching.Tumor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jgao
 */
public class EvidenceDaoImpl
    extends GenericDaoImpl<Evidence, Integer>
    implements EvidenceDao {
    @Override
    public List<Evidence> findEvidencesByAlteration(Alteration alteration) {
        return findByNamedQuery("findEvidencesByAlteration", alteration.getId());
    }

    @Override
    public List<Evidence> findEvidencesByAlterationAndTumorType(Alteration alteration, TumorType tumorType) {
        return findByNamedQuery("findEvidencesByAlterationAndTumorType", alteration, tumorType);
    }

    @Override
    public List<Evidence> findEvidencesByAlterationsAndTumorTypesAndEvidenceTypes(List<Alteration> alterations, List<TumorType> tumorTypes, List<EvidenceType> evidenceTypes) {
        List<Integer> alterationIds = new ArrayList<>();
        for (Alteration alteration : alterations) {
            alterationIds.add(alteration.getId());
        }

        String[] params = {"alts", "tts", "ets"};
        List[] values = {alterationIds, tumorTypes, evidenceTypes};

        return findByNamedQueryAndNamedParam("findEvidencesByAlterationsAndTumorTypesAndEvidenceTypes", params, values);
    }

    @Override
    public List<Evidence> findEvidencesByAlterationsAndTumorTypesAndEvidenceTypesAndLevelOfEvidence(List<Alteration> alterations, List<TumorType> tumorTypes, List<EvidenceType> evidenceTypes, List<LevelOfEvidence> levelOfEvidences) {
        List<Integer> alterationIds = new ArrayList<>();
        for (Alteration alteration : alterations) {
            alterationIds.add(alteration.getId());
        }

        String[] params = {"alts", "tts", "ets", "les"};
        List[] values = {alterationIds, tumorTypes, evidenceTypes, levelOfEvidences};

        return findByNamedQueryAndNamedParam("findEvidencesByAlterationsAndTumorTypesAndEvidenceTypesAndLevelOfEvidence", params, values);
    }

    @Override
    public List<Evidence> findEvidencesByAlteration(Alteration alteration, EvidenceType evidenceType) {
        if (evidenceType == null) return findEvidencesByAlteration(alteration);
        return findByNamedQuery("findEvidencesByAlterationAndEvidenceType", alteration.getId(), evidenceType);
    }


    @Override
    public List<Evidence> findEvidencesByAlterationAndLevels(Alteration alteration, EvidenceType evidenceType, LevelOfEvidence levelOfEvidence) {
        if (evidenceType == null) return findEvidencesByAlteration(alteration);
        if (levelOfEvidence == null) return findEvidencesByAlteration(alteration, evidenceType);
        return findByNamedQuery("findEvidencesByAlterationAndEvidenceTypeAndLevels", alteration.getId(), evidenceType, levelOfEvidence);
    }

    @Override
    public List<Evidence> findEvidencesByAlterationAndTumorType(Alteration alteration, EvidenceType evidenceType, TumorType tumorType) {
        return findByNamedQuery("findEvidencesByAlterationAndEvidenceTypeAndTumorType", alteration, evidenceType,  tumorType);
    }

    @Override
    public List<Evidence> findEvidencesByGene(Gene gene) {
        return findByNamedQuery("findEvidencesByGene", gene);
    }

    @Override
    public List<Evidence> findEvidencesByGene(Gene gene, EvidenceType evidenceType) {
        if (evidenceType == null) return findEvidencesByGene(gene);
        return findByNamedQuery("findEvidencesByGeneAndEvidenceType", gene, evidenceType);
    }

    @Override
    public List<Evidence> findEvidencesByIds(List<Integer> ids) {
        if (ids == null) return new ArrayList<>();
        String[] params = {"ids"};
        List[] values = {ids};
        return (List<Evidence>) getHibernateTemplate().findByNamedQueryAndNamedParam("findEvidencesByIds", params, values);
    }

    @Override
    public List<Object> findTumorTypesWithEvidencesForAlterations(List<Alteration> alterations) {
        List<Integer> alts = new ArrayList<>();
        for (Alteration alteration : alterations) {
            alts.add(alteration.getId());
        }
        String[] params = {"alts"};
        List[] values = {alts};
        return (List<Object>) getHibernateTemplate().findByNamedQueryAndNamedParam("findTumorTypesWithEvidencesForAlterations", params, values);
    }

    @Override
    public List<Object> findCancerTypesWithEvidencesForAlterations(List<Alteration> alterations) {
        List<Integer> alts = new ArrayList<>();
        for (Alteration alteration : alterations) {
            alts.add(alteration.getId());
        }
        String[] params = {"alts"};
        List[] values = {alts};
        return (List<Object>) getHibernateTemplate().findByNamedQueryAndNamedParam("findCancerTypesWithEvidencesForAlterations", params, values);
    }

    @Override
    public List<Object> findSubtypesWithEvidencesForAlterations(List<Alteration> alterations) {
        List<Integer> alts = new ArrayList<>();
        for (Alteration alteration : alterations) {
            alts.add(alteration.getId());
        }
        String[] params = {"alts"};
        List[] values = {alts};
        return (List<Object>) getHibernateTemplate().findByNamedQueryAndNamedParam("findSubtypesWithEvidencesForAlterations", params, values);
    }

    @Override
    public List<Evidence> findEvidenceByUUIDs(List<String> uuids) {
        String[] params = {"uuids"};
        List[] values = {uuids};
        return (List<Evidence>) getHibernateTemplate().findByNamedQueryAndNamedParam("findEvidenceByUUIDs", params, values);
    }
}
