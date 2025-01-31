package org.mskcc.cbio.oncokb.model;

import java.util.*;

public enum Pathogenicity {
    YES("Pathogenic"),
    LIKELY("Likely Pathogenic"),
    LIKELY_BENIGN("Likely Benign"),
    BENIGN("Benign"),
    UNKNOWN("Unknown");

    private Pathogenicity(String pathogenic) {
        this.pathogenic = pathogenic;
    }

    private final String pathogenic;

    public String getPathogenic() {
        return pathogenic;
    }

    private static final Map<String, Pathogenicity> map = new HashMap<String, Pathogenicity>();

    static {
        for (Pathogenicity pathogenicity : Pathogenicity.values()) {
            map.put(pathogenicity.getPathogenic(), pathogenicity);
        }
    }

    public static Pathogenicity getByEffect(String pathogenicity) {
        return map.get(pathogenicity);
    }

    public static int compare(Pathogenicity o1, Pathogenicity o2) {
        //0 indicates o1 has the same pathogenicity with o2
        //positive number indicates o1 has higher pathogenicity than o2
        //negative number indicates o2 has higher pathogenicity than o1
        List<Pathogenicity> pathogenicityValues = new ArrayList<>(Arrays.asList(BENIGN, LIKELY_BENIGN, UNKNOWN, LIKELY, YES));
        if (o1 == null && o2 == null) return 0;
        else if (o1 == null) return -1;
        else if (o2 == null) return 1;
        else return pathogenicityValues.indexOf(o1) - pathogenicityValues.indexOf(o2);
    }

    public static Pathogenicity getByEvidence(Evidence evidence) {
        if (evidence != null) {
            return getByEffect(evidence.getKnownEffect());
        }
        return null;
    }
}
