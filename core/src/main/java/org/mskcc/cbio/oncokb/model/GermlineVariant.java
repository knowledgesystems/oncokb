package org.mskcc.cbio.oncokb.model;

import java.util.ArrayList;
import java.util.List;

public class GermlineVariant {
    List<String> genomicIndicators = new ArrayList<>();
    String penetrance = "";
    String penetranceDescription = "";
    String pathogenic = "";
    String description = "";
    String inheritanceMechanism = "";
    String inheritanceMechanismDescription = "";
    String cancerRisk = "";
    String clinVarId = "";

    public List<String> getGenomicIndicators() {
        return genomicIndicators;
    }

    public void setGenomicIndicators(List<String> genomicIndicators) {
        this.genomicIndicators = genomicIndicators;
    }

    public String getPenetrance() {
        return penetrance;
    }

    public void setPenetrance(String penetrance) {
        this.penetrance = penetrance;
    }

    public String getPathogenic() {
        return pathogenic;
    }

    public void setPathogenic(String pathogenic) {
        this.pathogenic = pathogenic;
    }

    public String getInheritanceMechanism() {
        return inheritanceMechanism;
    }

    public void setInheritanceMechanism(String inheritanceMechanism) {
        this.inheritanceMechanism = inheritanceMechanism;
    }

    public String getCancerRisk() {
        return cancerRisk;
    }

    public void setCancerRisk(String cancerRisk) {
        this.cancerRisk = cancerRisk;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getClinVarId() {
        return clinVarId;
    }

    public void setClinVarId(String clinVarId) {
        this.clinVarId = clinVarId;
    }

    public String getPenetranceDescription() {
        return penetranceDescription;
    }

    public void setPenetranceDescription(String penetranceDescription) {
        this.penetranceDescription = penetranceDescription;
    }

    public String getInheritanceMechanismDescription() {
        return inheritanceMechanismDescription;
    }

    public void setInheritanceMechanismDescription(String inheritanceMechanismDescription) {
        this.inheritanceMechanismDescription = inheritanceMechanismDescription;
    }
}
