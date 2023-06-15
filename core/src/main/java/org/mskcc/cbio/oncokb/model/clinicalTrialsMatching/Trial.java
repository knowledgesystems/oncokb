package org.mskcc.cbio.oncokb.model.clinicalTrialsMatching;

import java.util.List;

public class Trial {
    private String briefTitle;
    private String currentTrialStatus;
    private String currentTrialStatusDate;
    private String nctId;
    private List<Arm> arms;
    private Boolean isUSTrial = null;
    private String principalInvestigator = null;
    private List<Collaborator> collaborators;
    private List<String> sites;
    private Eligibility eligibility;
    private String phase;

    public Boolean getIsUSTrial() {
        return isUSTrial;
    }

    public void setIsUSTrial(Boolean isUSTrial) {
        this.isUSTrial = isUSTrial;
    }

    public String getBriefTitle() {
        return briefTitle;
    }

    public void setBriefTitle(String briefTitle) {
        this.briefTitle = briefTitle;
    }

    public String getCurrentTrialStatus() {
        return currentTrialStatus;
    }

    public void setCurrentTrialStatus(String currentTrialStatus) {
        this.currentTrialStatus = currentTrialStatus;
    }

    public String getCurrentTrialStatusDate() {
        return currentTrialStatusDate;
    }

    public void setCurrentTrialStatusDate(String currentTrialStatusDate) {
        this.currentTrialStatusDate = currentTrialStatusDate;
    }

    public String getPrincipalInvestigator() {
        return principalInvestigator;
    }

    public void setPrincipalInvestigator(String principalInvestigator) {
        this.principalInvestigator = principalInvestigator;
    }

    public String getNctId() {
        return nctId;
    }

    public void setNctId(String nctId) {
        this.nctId = nctId;
    }

    public List<Arm> getArms() {
        return arms;
    }

    public void setArms(List<Arm> arms) {
        this.arms = arms;
    }

    public List<Collaborator> getCollaborators() {
        return this.collaborators;
    }

    public void setCollaborators(List<Collaborator> collaborators) {
        this.collaborators = collaborators;
    }

    public List<String> getSites() {
        return this.sites;
    }

    public void setSites(List<String> sites) {
        this.sites = sites;
    }

    public Eligibility getEligibility() {
        return this.eligibility;
    }

    public void setEligibility(Eligibility eligibility) {
        this.eligibility = eligibility;
    }

    public String getPhase() {
        return this.phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }
}
