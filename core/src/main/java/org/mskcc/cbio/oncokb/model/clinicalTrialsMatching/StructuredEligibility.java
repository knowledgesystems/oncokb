package org.mskcc.cbio.oncokb.model.clinicalTrialsMatching;

public class StructuredEligibility {
    private Boolean acceptsHealthyVolunteers;
    private String gender;
    private String maxAgeInYears;
    private String minAgeInYears;

    public void setAcceptsHealthyVolunteers(Boolean acceptsHealthyVolunteers) {
        this.acceptsHealthyVolunteers = acceptsHealthyVolunteers;
    }

    public Boolean getAcceptsHealthyVolunteers() {
        return this.acceptsHealthyVolunteers;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getGender() {
        return this.gender;
    }

    public void setMaxAgeInYears(String maxAgeInYears) {
        this.maxAgeInYears = maxAgeInYears;
    }

    public String getMaxAgeInYears() {
        return this.maxAgeInYears;
    }

    public void setMinAgeInYears(String minAgeInYears) {
        this.minAgeInYears = minAgeInYears;
    }

    public String getMinAgeInYears() {
        return this.minAgeInYears;
    }
    
}
