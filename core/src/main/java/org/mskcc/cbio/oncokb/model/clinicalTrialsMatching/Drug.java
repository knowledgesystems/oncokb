package org.mskcc.cbio.oncokb.model.clinicalTrialsMatching;

/**
 * Created by Yifu Yao on 2020-09-08
 */
public class Drug {
    private String drugName;
    private String ncitCode;

    public String getDrugName() {
        return drugName;
    }

    public void setDrugName(String drugName) {
        this.drugName = drugName;
    }

    public String getNcitCode() {
        return ncitCode;
    }

    public void setNcitCode(String ncitCode) {
        this.ncitCode = ncitCode;
    }
    
}
