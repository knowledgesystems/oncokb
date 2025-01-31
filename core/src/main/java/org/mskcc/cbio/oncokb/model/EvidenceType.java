package org.mskcc.cbio.oncokb.model;

/**
 * @author jgao
 */
public enum EvidenceType {
    GENE_SUMMARY("Gene summary"),
    MUTATION_SUMMARY("Mutation summary"),
    TUMOR_TYPE_SUMMARY("Tumor type summary"),
    GENE_TUMOR_TYPE_SUMMARY("Gene tumor type summary"),
    PROGNOSTIC_SUMMARY("Prognostic summary"),
    DIAGNOSTIC_SUMMARY("Diagnostic summary"),
    GENE_BACKGROUND("Gene background"),
    ONCOGENIC("Oncogenic"),
    MUTATION_EFFECT("Mutation effect"),

    VUS("Variant of unknown significance"),
    PROGNOSTIC_IMPLICATION("Prognostic implications"),
    DIAGNOSTIC_IMPLICATION("Diagnostic implications"),
    STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_SENSITIVITY("Standard therapeutic implications for drug sensitivity"),
    STANDARD_THERAPEUTIC_IMPLICATIONS_FOR_DRUG_RESISTANCE("Standard therapeutic implications for drug resistance"),
    INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_SENSITIVITY("Investigational therapeutic implications for drug sensitivity"),
    INVESTIGATIONAL_THERAPEUTIC_IMPLICATIONS_DRUG_RESISTANCE("Investigational therapeutic implications for drug resistance"),

    PATHOGENIC("Pathogenic"),
    GENOMIC_INDICATOR("Genomic Indicator"),
    GENOMIC_INDICATOR_ALLELE_STATE("Genomic Indicator Allele State"),
    GENE_PENETRANCE("Gene Penetrance"),
    GENE_INHERITANCE_MECHANISM("Gene Inheritance Mechanism"),
    GENE_CANCER_RISK("Gene Cancer Risk"),

    VARIANT_PENETRANCE("Variant Penetrance"),
    VARIANT_INHERITANCE_MECHANISM("Variant Inheritance Mechanism"),
    VARIANT_CANCER_RISK("Variant Cancer Risk");

    EvidenceType(String label) {
        this.label = label;
    }

    private final String label;

    public String label() {
        return label;
    }
}
