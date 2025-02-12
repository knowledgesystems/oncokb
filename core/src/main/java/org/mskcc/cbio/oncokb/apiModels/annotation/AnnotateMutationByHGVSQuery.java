package org.mskcc.cbio.oncokb.apiModels.annotation;

import java.util.Objects;

/**
 * Created by Hongxin Zhang on 2019-03-25.
 */
public class AnnotateMutationByHGVSQuery extends AnnotationQuery implements java.io.Serializable{
    private String hgvs;

    public String getHgvs() {
        return hgvs;
    }

    public void setHgvs(String hgvs) {
        this.hgvs = hgvs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getHgvs());
    }
}
