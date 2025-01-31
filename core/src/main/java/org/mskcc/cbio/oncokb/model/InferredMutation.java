package org.mskcc.cbio.oncokb.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Hongxin Zhang on 2/12/18.
 */
public enum InferredMutation implements OncoKBVariant {
    ONCOGENIC_MUTATIONS("Oncogenic Mutations"),
    PATHOGENIC_VARIANTS("Pathogenic Variants"),
    GAIN_OF_FUNCTION_MUTATIONS("Gain-of-function Mutations"),
    LOSS_OF_FUNCTION_MUTATIONS("Loss-of-function Mutations"),
    SWITCH_OF_FUNCTION_MUTATIONS("Switch-of-function Mutations"),
    VUS("Variants of Unknown Significance");

    private final String mutation;

    InferredMutation(String mutation) {
        this.mutation = mutation;
    }

    public String getVariant() {
        return mutation;
    }

}
