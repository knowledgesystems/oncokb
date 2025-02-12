package org.mskcc.cbio.oncokb.apiModels.annotation;

import java.util.ArrayList;
import java.util.List;

public class AnnotateSampleQuery implements java.io.Serializable {
    private List<AnnotateStructuralVariantQuery> structuralVariants = new ArrayList<>();
    private List<AnnotateCopyNumberAlterationQuery> copyNumberAlterations = new ArrayList<>();
    private MutationsQuery mutations = new MutationsQuery();

    public static class MutationsQuery {
        private List<AnnotateMutationByGenomicChangeQuery> genomicChange = new ArrayList<>();
        private List<AnnotateMutationByHGVSQuery> cDnaChange = new ArrayList<>();
        private List<AnnotateMutationByProteinChangeQuery> proteinChange = new ArrayList<>();
        private List<AnnotateMutationByHGVSQuery> hgvsg = new ArrayList<>();

        public List<AnnotateMutationByGenomicChangeQuery> getGenomicChange() {
            return genomicChange;
        }

        public void setGenomicChange(List<AnnotateMutationByGenomicChangeQuery> genomicChange) {
            this.genomicChange = genomicChange;
        }

        public List<AnnotateMutationByHGVSQuery> getcDnaChange() {
            return cDnaChange;
        }

        public void setcDnaChange(List<AnnotateMutationByHGVSQuery> cDnaChange) {
            this.cDnaChange = cDnaChange;
        }

        public List<AnnotateMutationByProteinChangeQuery> getProteinChange() {
            return proteinChange;
        }

        public void setProteinChange(List<AnnotateMutationByProteinChangeQuery> proteinChange) {
            this.proteinChange = proteinChange;
        }

        public List<AnnotateMutationByHGVSQuery> getHgvsg() {
            return hgvsg;
        }

        public void setHgvsg(List<AnnotateMutationByHGVSQuery> hgvsg) {
            this.hgvsg = hgvsg;
        }
    }

    public List<AnnotateStructuralVariantQuery> getStructuralVariants() {
        return structuralVariants;
    }

    public void setStructuralVariants(List<AnnotateStructuralVariantQuery> structuralVariants) {
        this.structuralVariants = structuralVariants;
    }

    public List<AnnotateCopyNumberAlterationQuery> getCopyNumberAlterations() {
        return copyNumberAlterations;
    }

    public void setCopyNumberAlterations(List<AnnotateCopyNumberAlterationQuery> copyNumberAlterations) {
        this.copyNumberAlterations = copyNumberAlterations;
    }

    public MutationsQuery getMutations() {
        return mutations;
    }

    public void setMutations(MutationsQuery mutations) {
        this.mutations = mutations;
    }
}