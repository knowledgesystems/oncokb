package org.mskcc.cbio.oncokb.model.clinicalTrialsMatching;

import java.util.List;

/**
 * Created by Yifu Yao on 2020-09-08
 */
public class Tumor {
    private String nciCode;
    private String nciMainType;
    private List<Trial> trials;
    private String tumorName;

    public String getNciCode() {
        return nciCode;
    }

    public void setNciCode(String nciCode) {
        this.nciCode = nciCode;
    }

    public String getNciMainType() {
        return nciMainType;
    }

    public void setNciMainType(String nciMainType) {
        this.nciMainType = nciMainType;
    }

    public List<Trial> getTrials() {
        return trials;
    }

    public void setTrials(List<Trial> trials) {
        this.trials = trials;
    }

    public String getTumorName() {
        return tumorName;
    }

    public void setTumorName(String tumorName) {
        this.tumorName = tumorName;
    }
}
