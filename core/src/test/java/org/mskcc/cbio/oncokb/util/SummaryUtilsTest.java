package org.mskcc.cbio.oncokb.util;

import org.junit.Test;
import org.mskcc.cbio.oncokb.model.Gene;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.mskcc.cbio.oncokb.model.clinicalTrialsMatching.Tumor;

import static org.junit.Assert.assertEquals;
import static org.mskcc.cbio.oncokb.Constants.DEFAULT_REFERENCE_GENOME;

/**
 * Created by Hongxin on 12/5/16.
 */
public class SummaryUtilsTest {
    @Test
    public void testGetGeneMutationNameInVariantSummary() throws Exception {
        Gene gene = GeneUtils.getGene("ERBB2");
        assertEquals("ERBB2 amplification", SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Amplification"));
        assertEquals("ERBB2 amplification (gain)", SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "gain"));
        assertEquals(SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Amplification"), SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "amplification"));
        assertEquals(SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Amplification"), SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), " amplification"));

        assertEquals("ERBB2 deletion", SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Deletion"));
        assertEquals("ERBB2 deletion (loss)", SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "loss"));
        assertEquals(SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Deletion"), SummaryUtils.getGeneMutationNameInVariantSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "deLetion"));
    }

    @Test
    public void testGetGeneMutationNameInTumorTypeSummary() throws Exception {
        Gene gene = GeneUtils.getGene("ERBB2");
        assertEquals("ERBB2-amplified", SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Amplification"));
        assertEquals(SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Amplification"), SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "amplification"));
        assertEquals(SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Amplification"), SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), " amplification"));
        assertEquals(SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Amplification"), SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "gain"));

        assertEquals("ERBB2 deletion", SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Deletion"));
        assertEquals(SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Deletion"), SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "loss"));
        assertEquals(SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Deletion"), SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), " loss"));
        assertEquals(SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "Deletion"), SummaryUtils.getGeneMutationNameInTumorTypeSummary(gene, DEFAULT_REFERENCE_GENOME, gene.getHugoSymbol(), "deLetion"));
    }
}
