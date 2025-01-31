package org.mskcc.cbio.oncokb.model;

public class IndicatorQueryPathogenicity {
    private Pathogenicity pathogenicity;
    private Evidence pathogenicityEvidence;

    public IndicatorQueryPathogenicity(Pathogenicity pathogenicity, Evidence pathogenicityEvidence) {
        this.pathogenicity = pathogenicity;
        this.pathogenicityEvidence = pathogenicityEvidence;
    }

    public Pathogenicity getPathogenicity() {
        return pathogenicity;
    }

    public void setPathogenicity(Pathogenicity pathogenicity) {
        this.pathogenicity = pathogenicity;
    }

    public Evidence getPathogenicityEvidence() {
        return pathogenicityEvidence;
    }

    public void setPathogenicityEvidence(Evidence pathogenicityEvidence) {
        this.pathogenicityEvidence = pathogenicityEvidence;
    }
}
