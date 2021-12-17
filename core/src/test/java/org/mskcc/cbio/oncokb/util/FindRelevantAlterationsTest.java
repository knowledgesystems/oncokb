package org.mskcc.cbio.oncokb.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mskcc.cbio.oncokb.model.Alteration;
import org.mskcc.cbio.oncokb.model.AlterationType;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mskcc.cbio.oncokb.Constants.DEFAULT_REFERENCE_GENOME;

/**
 * Created by Hongxin on 12/23/16.
 */

@RunWith(Parameterized.class)
public class FindRelevantAlterationsTest {
    private String hugoSymbol;
    private String alteration;
    private String alterationType;
    private String expectedRelevantAlterations;

    public FindRelevantAlterationsTest(String hugoSymbol, String alteration, String alterationType, String expectedRelevantAlterations) {
        this.hugoSymbol = hugoSymbol;
        this.alteration = alteration;
        this.alterationType = alterationType;
        this.expectedRelevantAlterations = expectedRelevantAlterations;
    }

    @Parameterized.Parameters
    public static Collection<String[]> getParameters() {
        return Arrays.asList(
            new String[][]{
                {"MSH2", null, null, ""},
                {"MSH2", "", null, ""},

                // Critical cases
                {"BRAF", "V600E", null, "V600E, V600A, V600D, V600G, V600K, V600L, V600M, V600Q, V600R, VK600EI, V600, Oncogenic Mutations"},
                {"SMARCB1", "R374Q", null, "R374Q, R374W, Oncogenic Mutations"},

                // Check Fusions
                {"BRAF", "PAPSS1-BRAF Fusion", null, "PAPSS1-BRAF Fusion, Fusions, Oncogenic Mutations {excluding V600}, Oncogenic Mutations {excluding V600E ; V600K}, Oncogenic Mutations"},

                // The revert fusion should get picked
                {"ABL1", "ABL1-BCR fusion", null, "BCR-ABL1 Fusion, Fusions"},
                {"ABL1", "BCR-ABL1 fusion", null, "BCR-ABL1 Fusion, Fusions"},

                // Tumor suppressor should be mapped with Truncating Mutations. (The code does not check whether gene
                // is tumor suppressor, just check whether Fusions is curated, is not, link Truncating Mutations)
                {"PIK3R1", "KCTD16-PIK3R1 fusion", null, "KCTD16-PIK3R1 fusion, Truncating Mutations"},

                // General truncating consequence should be associated with Truncating Mutations
                // Check splice
                // TP53 Oncogenic Mutations does not have any information we are ready to relase
                {"TP53", "X33_splice", null, "X33_splice, Truncating Mutations, Oncogenic Mutations"},
                {"MET", "X1010_splice", null, "X1010_splice, 963_1010splice, 981_1028splice, Oncogenic Mutations"},
                {"MET", "X1010splice", null, "X1010_splice, 963_1010splice, 981_1028splice, Oncogenic Mutations"},

                // Check stop_gained
                {"MAP2K4", "R304*", null, "R304*, Truncating Mutations"},

                // EGFR exon deletion
                {"EGFR", "vIII", null, "vIII, Oncogenic Mutations"},
                {"EGFR", "CTD", null, "C-terminal domain, Oncogenic Mutations"},
                {"EGFR", "vIV", null, "C-terminal domain, Oncogenic Mutations"},
                {"EGFR", "vIVa", null, "C-terminal domain, Oncogenic Mutations"},
                {"EGFR", "vIVb", null, "C-terminal domain, Oncogenic Mutations"},
                {"EGFR", "vIVc", null, "C-terminal domain, Oncogenic Mutations"},

                // Check range
                {"MED12", "G44S", null, "G44S, G44A, G44C, G44D, G44V, 34_68mis"},
                {"MED12", "G44D", null, "G44D, G44A, G44C, G44S, G44V, 34_68mis"},
                {"MED12", "G44*", null, "Truncating Mutations"},
                {"MED12", "K42_N46del", null, ""},
                {"NOTCH1", "Q2405Rfs*17", null, "Q2405Rfs*17, T2375_K2555trunc, Truncating Mutations, Oncogenic Mutations"},

                // VUS should get mapped to hotspot VUS, but should not get Oncogenic Mutations from the hotspot VUS.
                // In this case VUS N109_R113del is covered by VUS I99_R113del, and I99_R113del is a hotpot.
                // No longer applicable
//                {"MAP2K1", "N109_R113del", null, "N109_R113del, I99_R113del"},

                // Range missense variant
                {"PDGFRA", "D842I", null, "D842I, D842H, D842Y, D842_I843delinsIM, C814_S852mis, Oncogenic Mutations"},

                // D842V should not be mapped as alternative allele
                {"PDGFRA", "D842I", null, "D842I, D842H, D842Y, D842_I843delinsIM, C814_S852mis, Oncogenic Mutations"},
                {"PDGFRA", "D842V", null, "D842V, D842H, D842I, D842Y, D842_I843delinsIM, C814_S852mis, Oncogenic Mutations"},

                // Check whether the overlapped variants(with the same consequence) will be mapped
                {"MAP2K1", "E41_F53del", null, "E41_F53del, E41_L54del, L42_K57del, E51_Q58del, F53_Q58del, F53_Q58delinsL, Oncogenic Mutations"},

                // Truncating Mutations in the Oncogene should not be mapped to any range mutation unless the consequence is truncating
                {"KIT", "K509Nfs*2", null, "K509Nfs*2"},
                {"MED12", "Q1836Lfs*57", null, "Truncating Mutations"},
                {"PIK3CA", "*1069Ffs*5", null, ""},

                // For oncogene, we do not map mut range to truncating mutations.
                // But we do map if gene is oncogene and TSG. TSG here is a Oncogene+TSG
                {"MED12", "A34*", null, "Truncating Mutations"},

                {"NOTCH1", "Q2405Rfs*17", null, "Q2405Rfs*17, T2375_K2555trunc, Truncating Mutations, Oncogenic Mutations"},

                // Deletion
                // With specific Deletion curated
                {"BRCA2", "Deletion", null, "Deletion, Oncogenic Mutations"},
                // Without specific Deletion curated
                {"MAP2K4", "Deletion", null, "Truncating Mutations"},

                // Abbreviated alterations
                {"EGFR", "KDD", null, "Kinase Domain Duplication, Oncogenic Mutations"},
                {"EGFR", "Kinase Domain Duplication", null, "Kinase Domain Duplication, Oncogenic Mutations"},
                {"EGFR", "CTD", null, "C-terminal domain, Oncogenic Mutations"},
                {"EGFR", "C-terminal domain", null, "C-terminal domain, Oncogenic Mutations"},
                {"EGFR", "vII", null, "vII, Oncogenic Mutations"},
                {"EGFR", "vIII", null, "vIII, Oncogenic Mutations"},

                // Do not get alternative alleles or positional variant for ABL1 T315I
                {"ABL1", "T315I", null, "T315I"},

                // 654 is a hotspot position
                {"KIT", "V654G", null, "V654A, Oncogenic Mutations"},
                {"KIT", "T670A", null, "T670I, I669_T670delinsMI, Oncogenic Mutations"},


                // Do not map Oncogenic Mutations to Amplification
                {"KIT", "Amplification", null, "Amplification"},

                // Do not map missense alteration with different reference AA as relevant
                {"BRAF", "A600E", null, ""},



                // Check non_truncating_variant
//                {"MED12", "A22*", null, "1_33mut, Truncating Mutations"},
//                {"MED12", "G22D", null, "1_33nontrunc, 1_33mut"},

                // Check Variants of Unknown Significance is associated
                {"BRCA2", "V159M", null, "V159M, V159L, Variants of Unknown Significance"}, // this is curated VUS
                {"BRCA2", "R2659G", null, "R2659G, R2659K, R2659T"}, // this is curated inconclusive
                {"BRCA2", "E3002K", null, "E3002K, E3002D, Oncogenic Mutations"}, // this is curated likely oncogenic
                {"BRCA2", "Y3035C", null, "Y3035C, Y3035S"}, // this is curated likely neutral
                {"BRCA2", "E790*", null, "Truncating Mutations, Oncogenic Mutations"}, // this is not curated truncating mutation
                {"BRCA2", "E10000A", null, "Variants of Unknown Significance"}, // this is not curated
            });
    }

    @Test
    public void testAnnotateAlteration() throws Exception {
        // Particularly test consequence
        Alteration alt = AlterationUtils.getAlteration(hugoSymbol, alteration, AlterationType.getByName(alterationType), null, null, null, null);

        LinkedHashSet<Alteration> relevantAlterations =
            ApplicationContextSingleton.getAlterationBo()
                .findRelevantAlterations(DEFAULT_REFERENCE_GENOME, alt, AlterationUtils.getAllAlterations(DEFAULT_REFERENCE_GENOME, alt.getGene()), true);
        String relevantAltsName = AlterationUtils.toString(relevantAlterations);

        assertEquals("Relevant alterations are not matched on case " +
            hugoSymbol + " " + alteration + " " + alterationType + " ", expectedRelevantAlterations, relevantAltsName);
    }

}
