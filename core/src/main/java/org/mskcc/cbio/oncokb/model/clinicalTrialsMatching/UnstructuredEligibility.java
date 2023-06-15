package org.mskcc.cbio.oncokb.model.clinicalTrialsMatching;

public class UnstructuredEligibility {
    private String description;
    private Integer displayOrder;
    private Boolean inclusionIndicator;

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getDisplayOrder() {
        return this.displayOrder;
    }

    public void setInclusionIndicator(Boolean inclusionIndicator) {
        this.inclusionIndicator = inclusionIndicator;
    }

    public Boolean getInclusionIndicator() {
        return this.inclusionIndicator;
    }
}
