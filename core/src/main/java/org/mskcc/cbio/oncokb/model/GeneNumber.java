package org.mskcc.cbio.oncokb.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;


@ApiModel(description = "")
public class GeneNumber {

    private Gene gene = null;
    private Integer alteration = null;
    private Integer tumorType = null;
    private String highestSensitiveLevel = null;
    private String highestResistanceLevel = null;
    private String highestDiagnosticImplicationLevel = null;
    private String highestPrognosticImplicationLevel = null;
    private String highestFdaLevel = null;
    private String penetrance = null;
    private String inheritanceMechanism = null;

    /**
     **/
    @ApiModelProperty(value = "")
    @JsonProperty("gene")
    public Gene getGene() {
        return gene;
    }

    public void setGene(Gene gene) {
        this.gene = gene;
    }

    /**
     **/
    @ApiModelProperty(value = "")
    @JsonProperty("alteration")
    public Integer getAlteration() {
        return alteration;
    }

    public void setAlteration(Integer alteration) {
        this.alteration = alteration;
    }


    /**
     **/
    @ApiModelProperty(value = "")
    @JsonProperty("tumorType")
    public Integer getTumorType() {
        return tumorType;
    }

    public void setTumorType(Integer tumorType) {
        this.tumorType = tumorType;
    }


    public String getHighestSensitiveLevel() {
        return highestSensitiveLevel;
    }

    public void setHighestSensitiveLevel(String highestSensitiveLevel) {
        this.highestSensitiveLevel = highestSensitiveLevel;
    }

    public String getHighestResistanceLevel() {
        return highestResistanceLevel;
    }

    public void setHighestResistanceLevel(String highestResistanceLevel) {
        this.highestResistanceLevel = highestResistanceLevel;
    }

    public String getHighestDiagnosticImplicationLevel() {
        return highestDiagnosticImplicationLevel;
    }

    public void setHighestDiagnosticImplicationLevel(String highestDiagnosticImplicationLevel) {
        this.highestDiagnosticImplicationLevel = highestDiagnosticImplicationLevel;
    }

    public String getHighestPrognosticImplicationLevel() {
        return highestPrognosticImplicationLevel;
    }

    public void setHighestPrognosticImplicationLevel(String highestPrognosticImplicationLevel) {
        this.highestPrognosticImplicationLevel = highestPrognosticImplicationLevel;
    }

    public String getHighestFdaLevel() {
        return highestFdaLevel;
    }

    public void setHighestFdaLevel(String highestFdaLevel) {
        this.highestFdaLevel = highestFdaLevel;
    }

    public String getPenetrance() {
        return penetrance;
    }

    public void setPenetrance(String penetrance) {
        this.penetrance = penetrance;
    }

    public String getInheritanceMechanism() {
        return inheritanceMechanism;
    }

    public void setInheritanceMechanism(String inheritanceMechanism) {
        this.inheritanceMechanism = inheritanceMechanism;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeneNumber)) return false;
        GeneNumber that = (GeneNumber) o;
        return Objects.equals(getGene(), that.getGene()) &&
            Objects.equals(getAlteration(), that.getAlteration()) &&
            Objects.equals(getTumorType(), that.getTumorType()) &&
            Objects.equals(getHighestSensitiveLevel(), that.getHighestSensitiveLevel()) &&
            Objects.equals(getHighestResistanceLevel(), that.getHighestResistanceLevel()) &&
            Objects.equals(getHighestDiagnosticImplicationLevel(), that.getHighestDiagnosticImplicationLevel()) &&
            Objects.equals(getHighestPrognosticImplicationLevel(), that.getHighestPrognosticImplicationLevel()) &&
            Objects.equals(getHighestFdaLevel(), that.getHighestFdaLevel()) &&
            Objects.equals(getPenetrance(), that.getPenetrance()) &&
            Objects.equals(getInheritanceMechanism(), that.getInheritanceMechanism());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGene(), getAlteration(), getTumorType(), getHighestSensitiveLevel(), getHighestResistanceLevel(), getHighestDiagnosticImplicationLevel(), getHighestPrognosticImplicationLevel(), getHighestFdaLevel(), getPenetrance(), getInheritanceMechanism());
    }

    @Override
    public String toString() {
        return "GeneNumber{" +
            "gene=" + gene +
            ", alteration=" + alteration +
            ", tumorType=" + tumorType +
            ", highestSensitiveLevel='" + highestSensitiveLevel + '\'' +
            ", highestResistanceLevel='" + highestResistanceLevel + '\'' +
            ", highestDiagnosticImplicationLevel='" + highestDiagnosticImplicationLevel + '\'' +
            ", highestPrognosticImplicationLevel='" + highestPrognosticImplicationLevel + '\'' +
            ", highestFdaLevel='" + highestFdaLevel + '\'' +
            ", penetrance='" + penetrance + '\'' +
            ", inheritanceMechanism='" + inheritanceMechanism + '\'' +
            '}';
    }
}
