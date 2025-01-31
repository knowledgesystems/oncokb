package org.mskcc.cbio.oncokb.util;

import java.util.*;
import org.mskcc.cbio.oncokb.model.EvidenceType;

/**
 * Created by Hongxin on 11/09/16.
 */
public class EvidenceTypeUtils {
    public static Set<EvidenceType> getGeneEvidenceTypes(Boolean germline) {
        Set<EvidenceType> evidenceTypes = new HashSet<>();
        if(Boolean.TRUE.equals(germline)) {
            evidenceTypes.add(EvidenceType.GENE_SUMMARY);
        } else {
            evidenceTypes.add(EvidenceType.GENE_SUMMARY);
        }
        evidenceTypes.add(EvidenceType.GENE_BACKGROUND);
        return evidenceTypes;
    }

    public static Set<EvidenceType> getMutationEvidenceTypes(Boolean germline) {
        Set<EvidenceType> evidenceTypes = new HashSet<>();
        if (Boolean.TRUE.equals(germline)) {
            evidenceTypes.addAll(getGermlineVariantEvidenceTypes());
        } else {
            evidenceTypes.addAll(getSomaticMutationEvidenceTypes());
        }
        evidenceTypes.add(EvidenceType.VUS);
        evidenceTypes.add(EvidenceType.MUTATION_SUMMARY);
        return evidenceTypes;
    }

    public static Set<EvidenceType> getSomaticMutationEvidenceTypes() {
        Set<EvidenceType> evidenceTypes = new HashSet<>();
        evidenceTypes.add(EvidenceType.ONCOGENIC);
        evidenceTypes.add(EvidenceType.MUTATION_EFFECT);
        return evidenceTypes;
    }

    public static Set<EvidenceType> getGermlineVariantEvidenceTypes() {
        Set<EvidenceType> evidenceTypes = new HashSet<>();
        evidenceTypes.add(EvidenceType.PATHOGENIC);
        evidenceTypes.add(EvidenceType.GENOMIC_INDICATOR);
        evidenceTypes.add(EvidenceType.GENE_PENETRANCE);
        evidenceTypes.add(EvidenceType.GENE_CANCER_RISK);
        evidenceTypes.add(EvidenceType.GENE_INHERITANCE_MECHANISM);
        evidenceTypes.add(EvidenceType.VARIANT_PENETRANCE);
        evidenceTypes.add(EvidenceType.VARIANT_CANCER_RISK);
        evidenceTypes.add(EvidenceType.VARIANT_INHERITANCE_MECHANISM);
        return evidenceTypes;
    }

    public static Set<EvidenceType> getTumorTypeEvidenceTypes() {
        Set<EvidenceType> evidenceTypes = new HashSet<>();

        evidenceTypes.add(EvidenceType.TUMOR_TYPE_SUMMARY);
        evidenceTypes.add(EvidenceType.DIAGNOSTIC_SUMMARY);
        evidenceTypes.add(EvidenceType.PROGNOSTIC_SUMMARY);


        evidenceTypes.add(EvidenceType.DIAGNOSTIC_IMPLICATION);
        evidenceTypes.add(EvidenceType.PROGNOSTIC_IMPLICATION);

        evidenceTypes.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
        evidenceTypes.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);

        evidenceTypes.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE);
        evidenceTypes.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE);
        return evidenceTypes;
    }

    public static Set<EvidenceType> getSensitiveTreatmentEvidenceTypes() {
        Set<EvidenceType> types = new HashSet<>();
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);
        return types;
    }

    public static Set<EvidenceType> getTreatmentEvidenceTypes() {
        Set<EvidenceType> types = new HashSet<>();
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);
        return types;
    }

    public static Set<EvidenceType> getImplicationEvidenceTypes(){
        Set<EvidenceType> types = new HashSet<>();
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY);
        types.add(EvidenceType.STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE);
        types.add(EvidenceType.INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY);
        types.add(EvidenceType.DIAGNOSTIC_IMPLICATION);
        types.add(EvidenceType.PROGNOSTIC_IMPLICATION);
        return types;
    }

    public static List<EvidenceType> getAllEvidenceTypes(Boolean germline) {
        List<EvidenceType> evidenceTypes = new ArrayList<>();
        evidenceTypes.addAll(getGeneEvidenceTypes(germline));
        evidenceTypes.addAll(getMutationEvidenceTypes(germline));
        evidenceTypes.addAll(getTumorTypeEvidenceTypes());
        return evidenceTypes;
    }
}
