package org.mskcc.cbio.oncokb.model.clinicalTrialsMatching;

import java.util.List;

public class Eligibility {
    private StructuredEligibility structured;
    private List<UnstructuredEligibility> unstructured;

    public void setStructured(StructuredEligibility structured) {
        this.structured = structured;
    }

    public StructuredEligibility getStructured() {
        return this.structured;
    }

    public void setUnstructured(List<UnstructuredEligibility> unstructured) {
        this.unstructured = unstructured;
    }

    public List<UnstructuredEligibility> getUnstructured() {
        return this.unstructured;
    }
}
