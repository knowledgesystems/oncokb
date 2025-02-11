package org.mskcc.cbio.oncokb.util;

import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.genome_nexus.client.VariantAnnotation;
import org.junit.Test;
import org.mskcc.cbio.oncokb.apiModels.Implication;
import org.mskcc.cbio.oncokb.apiModels.MainType;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.genomeNexus.TranscriptSummaryAlterationResult;
import org.mskcc.cbio.oncokb.apiModels.TumorType;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.Assert.*;
import static org.mskcc.cbio.oncokb.Constants.*;
import static org.mskcc.cbio.oncokb.util.SummaryUtils.ONCOGENIC_MUTATIONS_DEFAULT_SUMMARY;

/**
 * Created by Hongxin on 12/23/16.
 */
public class IndicatorUtilsTest {
    @Test
    public void testProcessQuery() throws Exception {
        // We do not check gene/variant/tumor type summaries here. The test will be done in SummaryUtilsTest.

        // Gene not exists
        Query query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TEST", "V123M", null, null, "Pancreatic Adenocarcinoma", null, null, null, null);
        IndicatorQueryResp indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist in the response is not false, but it should be.", indicatorQueryResp.getGeneExist() == false);
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There is treatment(s) in the response, but it should no have any.", indicatorQueryResp.getTreatments().size() == 0);

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "CCT8L2-CCT8L2", null, "structural_variant", StructuralVariantType.DELETION, "Pancreatic Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("Gene should not exist, but it does.", false, indicatorQueryResp.getGeneExist());
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There is treatment(s) in the response, but it should not have any.", indicatorQueryResp.getTreatments().size() == 0);

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "CCT8L2-CCT8L2", null, "structural_variant", StructuralVariantType.DELETION, "Pancreatic Adenocarcinoma", "fusion", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("Gene should not exist, but it does.", false, indicatorQueryResp.getGeneExist());
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There is treatment(s) in the response, but it should not have any.", indicatorQueryResp.getTreatments().size() == 0);

        // The last update should be a date even if we don't have any annotation for the gene/varaint
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
        try {

            Date date = formatter.parse(indicatorQueryResp.getLastUpdate());
            assertTrue("The last update should be a valid date format MM/dd/yyyy, but it is not.", date != null);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        // Alteration not available
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "MSH2", "", null, null, "CANCER", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());

        // Oncogenic should always match with oncogenic summary, similar to likely oncogenic
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TP53", "R248Q", null, null, "Pancreatic Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not matched in variant summary.", "The TP53 R248Q mutation is likely oncogenic.", indicatorQueryResp.getVariantSummary());
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KRAS", "V14I", null, null, "Pancreatic Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not matched in variant summary.", "The KRAS V14I mutation is likely oncogenic.", indicatorQueryResp.getVariantSummary());

        // Check critical case
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "Melanoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist in the response is not true, but it should.", indicatorQueryResp.getGeneExist() == true);
        assertTrue("The variantExist in the response is not true, but it should.", indicatorQueryResp.getVariantExist() == true);
        assertTrue("The alleleExist in the response is not true, but it should.", indicatorQueryResp.getAlleleExist() == true);
        assertTrue("The query is not hotspot, but it should.", indicatorQueryResp.getHotspot() == true);
        assertTrue("The query is VUS, but it should not be.", indicatorQueryResp.getVUS() == false);
        assertEquals("The oncogenicity should be 'Oncogenic'", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be 1", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());
        assertTrue("The highest resistance level should be null", indicatorQueryResp.getHighestResistanceLevel() == null);
        assertTrue("Should have no other significant level", indicatorQueryResp.getOtherSignificantSensitiveLevels().size() == 0);

        // Check fusion.
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "CUL1-BRAF Fusion", null, null, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The highest sensitive level of CUL1-BRAF fusion should be Level 3A", LevelOfEvidence.LEVEL_3A, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The oncogenicity of CUL1-BRAF fusion should be Likely Oncogenic", "Likely Oncogenic", indicatorQueryResp.getOncogenic());

        // Both genes have relevant alterations, should return highest level then highest oncogenicity
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF-TMPRSS2", null, "fusion", null, "Prostate Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The highest sensitive level of BRAF-TMPRSS2 fusion should be Level 3A", LevelOfEvidence.LEVEL_3B, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The oncogenicity of BRAF-TMPRSS2 fusion should be Likely Oncogenic", "Likely Oncogenic", indicatorQueryResp.getOncogenic());

        // Test Intragenic Mutation
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ATM", "ATM-intragenic", null, null, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity of ATM-intragenic should be Likely Oncogenic", "Likely Oncogenic", indicatorQueryResp.getOncogenic());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ATM", "ATM intragenic", null, null, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity of ATM intragenic should be Likely Oncogenic", "Likely Oncogenic", indicatorQueryResp.getOncogenic());

        // Check mutation effect
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "MEL", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The mutation effect should not be empty", StringUtils.isNotEmpty(indicatorQueryResp.getMutationEffect().getKnownEffect()));
        assertTrue("The mutation effect description should not be empty", StringUtils.isNotEmpty(indicatorQueryResp.getMutationEffect().getDescription()));
        assertTrue("There should be pmids associated", indicatorQueryResp.getMutationEffect().getCitations().getPmids().size() > 0);

        // check variant exist
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, null, null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The variantExist should be true, but it's not", indicatorQueryResp.getVariantExist());
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PIK3CA", "V105_R108delVGNR", null, null, null, null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The variantExist should be true, but it's not", indicatorQueryResp.getVariantExist());
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PIK3CA", "V105_R108del", null, null, null, null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The variantExist should be true, but it's not", indicatorQueryResp.getVariantExist());
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PIK3CA", "V10000", null, null, null, null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertFalse("The variantExist should be false, but it's not", indicatorQueryResp.getVariantExist());

        // For treatments include both 2B and 3A, 3A should be shown first
        // Test disabled: we no longer have 2B which means the other significant levels are not used
//        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "RET", "Fusions", null, null, "Medullary Thyroid Cancer", null, null, null, null);
//        indicatorQueryResp = IndicatorUtils.processQuery(query, null true, null, false);
//        assertEquals("The highest sensitive level should be 2B", LevelOfEvidence.LEVEL_2B, indicatorQueryResp.getHighestSensitiveLevel());
//        assertTrue("The highest resistance level should be null", indicatorQueryResp.getHighestResistanceLevel() == null);
//        assertTrue("Should have any significant level", indicatorQueryResp.getOtherSignificantSensitiveLevels().size() > 0);
//        assertTrue(treatmentsContainLevel(indicatorQueryResp.getTreatments(), LevelOfEvidence.LEVEL_2B));
//        assertTrue(treatmentsContainLevel(indicatorQueryResp.getTreatments(), LevelOfEvidence.LEVEL_3A));
//        assertEquals("The level 3A should be shown before 2A", LevelOfEvidence.LEVEL_3A, indicatorQueryResp.getTreatments().get(0).getLevel());

        // Test for likely oncogenic for hotspot mutation
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PIK3R1", "K567E", null, null, "Pancreatic Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Likely Oncogenic'", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The isHotspot is not true, but it should be.", Boolean.TRUE, indicatorQueryResp.getHotspot());

        // Test for 3d hotspot which should not be predicted oncogenic
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KEAP1", "Y525S", null, null, "Pancreatic Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should not be 'Likely Oncogenic', it should be unknown.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The isHotspot is true, but it should not be.", Boolean.FALSE, indicatorQueryResp.getHotspot());

        // ALK R401Q should not be hotspot. It later was removed from the hotspot list.
        // The position has high truncating rate
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ALK", "R401Q", null, null, "Colon Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should not be 'Likely Oncogenic'", Oncogenicity.LIKELY_NEUTRAL.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The ALK R401Q mutation is likely neutral.", indicatorQueryResp.getVariantSummary());
        assertEquals("The isHotspot is not false, but it should be.", Boolean.FALSE, indicatorQueryResp.getHotspot());

        // No longer test 3A. KRAS has been downgraded to level 4
//        assertEquals("The highest sensitive level should be null, the level 3A evidence under Colorectal Cancer has been maked as NO propagation.",
//            null, indicatorQueryResp.getHighestSensitiveLevel());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KRAS", "Q61K", null, null, "Colorectal Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Likely Oncogenic'", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be 4",
            LevelOfEvidence.LEVEL_4, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The highest resistance level should be R1",
            LevelOfEvidence.LEVEL_R1, indicatorQueryResp.getHighestResistanceLevel());

        // Check special variant Oncogenic Mutations
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", InferredMutation.ONCOGENIC_MUTATIONS.getVariant(), null, null, "Colorectal Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Yes'", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The mutation effect is not unknown, but it should be.", MutationEffect.UNKNOWN.getMutationEffect(), indicatorQueryResp.getMutationEffect().getKnownEffect());

        assertEquals("The variant summary does not match",
            ONCOGENIC_MUTATIONS_DEFAULT_SUMMARY, indicatorQueryResp.getVariantSummary());

        // // Check special variant VUS Mutations
        // query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRD4", InferredMutation.VUS.getVariant(), null, null, null, null, null, null, null);
        // indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        // assertTrue("The variantExist in the response is not true, but it should.", indicatorQueryResp.getVariantExist() == true);
        // assertEquals("The oncogenicity should be 'Unknown'", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        // assertEquals("The mutation effect is not unknown, but it should be.", MutationEffect.UNKNOWN.getMutationEffect(), indicatorQueryResp.getMutationEffect().getKnownEffect());

        // // Check special variant VUS abbreviation
        // query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRD4", "VUS", null, null, null, null, null, null, null);
        // indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        // assertTrue("The variantExist in the response is not true, but it should.", indicatorQueryResp.getVariantExist() == true);

        // Test R2 data
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ALK", "I1171N", null, null, "Lung Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Oncogenic'", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be null",
            null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The highest resistance level should be R1",
            LevelOfEvidence.LEVEL_R2, indicatorQueryResp.getHighestResistanceLevel());

        // Test cases generated through MSK-IMPACT reports which ran into issue before
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "S768_V769delinsIL", null, null, "Non-Small Cell Lung Cancer", MISSENSE_VARIANT, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("Gene should exist", true, indicatorQueryResp.getGeneExist());
        // this is equivalent to SV768IL which we curated
        // The variant SV768IL has been deleted from the curation platform
        // assertEquals("Variant should exist", true, indicatorQueryResp.getVariantExist());
        assertEquals("Is expected to be Likely Oncogenic", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be 1",
            LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TMPRSS2-ERG", null, "Fusion", null, "Prostate Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("Gene should exist", true, indicatorQueryResp.getGeneExist());
        assertEquals("The Oncogenicity is not YES, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be null",
            null, indicatorQueryResp.getHighestSensitiveLevel());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "CDKN2A", "M1?", null, null, "Colon Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("Gene should exist", true, indicatorQueryResp.getGeneExist());
        assertEquals("The Oncogenicity is not Likely Oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());

        // If alternative allele has resistance treatment, all sensitive treatments related to it should not be applied.
        // PDGFRA D842Y Gastrointestinal Stromal Tumor
        // Dasatinib should not be listed as D842V has resistance treatment
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PDGFRA", "D842Y", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("Gene should exist", true, indicatorQueryResp.getGeneExist());
        assertEquals("The Oncogenicity is not Likely Oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be 1, but it is not.",
            LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The highest resistance level should be null, but it is not.",
            null, indicatorQueryResp.getHighestResistanceLevel());
        assertEquals("The number of treatments should be two",
            1, indicatorQueryResp.getTreatments().size());

        // Manually curated likely neutral should overwrite hotspot predicted oncogenic rule
        // EGFR A289D is manually curated as likely neutral.
        // Update at 01/29/2018 A289D is no longer curated as likely neutral
//        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "A289D", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
//        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
//        assertEquals("The Oncogenicity is not likely neutral, but it should be.", Oncogenicity.LIKELY_NEUTRAL.getOncogenic(), indicatorQueryResp.getOncogenic());

        // BRAF R462I is manually curated as Likely Neutral, then the oncogenic mutations shouldn't be associated.
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "R462I", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not likely neutral, but it should be.", Oncogenicity.LIKELY_NEUTRAL.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level of BRAF R462I should be null.", null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The highest resistance level of BRAF R462I should be null.", null, indicatorQueryResp.getHighestResistanceLevel());
        assertEquals("The tumor type summary does not match.", "There are no FDA-approved or NCCN-compendium listed treatments specifically for patients with BRAF R462I mutant gastrointestinal stromal tumors.", indicatorQueryResp.getTumorTypeSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "Arg462Ile", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not likely neutral, but it should be.", Oncogenicity.LIKELY_NEUTRAL.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level of BRAF R462I should be null.", null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The highest resistance level of BRAF R462I should be null.", null, indicatorQueryResp.getHighestResistanceLevel());
        assertEquals("The tumor type summary does not match.", "There are no FDA-approved or NCCN-compendium listed treatments specifically for patients with BRAF R462I mutant gastrointestinal stromal tumors.", indicatorQueryResp.getTumorTypeSummary());

        // Likely Neutral oncogenicity should not be propagated to alternative allele
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "AKT1", "P42I", null, null, "Anaplastic Astrocytoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not unknown, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The mutation effect is not unknown, but it should be.", MutationEffect.UNKNOWN.getMutationEffect(), indicatorQueryResp.getMutationEffect().getKnownEffect());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "AKT1", "Pro42Ile", null, null, "Anaplastic Astrocytoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not unknown, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The mutation effect is not unknown, but it should be.", MutationEffect.UNKNOWN.getMutationEffect(), indicatorQueryResp.getMutationEffect().getKnownEffect());

        // Check EGFR vIII vII vIV
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "EGFRvIII", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR vIII alteration is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "vIII", null, null, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR vIII alteration is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "vII", null, null, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR vII alteration is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "vV", null, null, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR vV alteration is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        // Check EGFR CTD
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "CTD", null, null, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR C-terminal domain (CTD) is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        // Check EGFR KDD
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "KDD", null, null, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR kinase domain duplication (KDD) is known to be oncogenic.", indicatorQueryResp.getVariantSummary());
        assertEquals("The highest sensitive level should be 1", LevelOfEvidence.LEVEL_3A, indicatorQueryResp.getHighestSensitiveLevel());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "KDD", "structural_variant", StructuralVariantType.DELETION, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR kinase domain duplication (KDD) is known to be oncogenic.", indicatorQueryResp.getVariantSummary());
        assertEquals("The highest sensitive level should be 1", LevelOfEvidence.LEVEL_3A, indicatorQueryResp.getHighestSensitiveLevel());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "kinase domain duplication", null, null, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR kinase domain duplication is known to be oncogenic.", indicatorQueryResp.getVariantSummary());
        assertEquals("The highest sensitive level should be 1", LevelOfEvidence.LEVEL_3A, indicatorQueryResp.getHighestSensitiveLevel());

        // Check FLT3 ITD
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "FLT3", "ITD", null, null, "AML", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The FLT3 internal tandem duplication (ITD) is known to be oncogenic.", indicatorQueryResp.getVariantSummary());
        assertEquals("The highest sensitive level should be 1", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());
        assertTrue("There should be level 1 treatment in the list", treatmentsContainLevel(indicatorQueryResp.getTreatments(), LevelOfEvidence.LEVEL_1));
        assertTrue("There should be level 3A treatment in the list", treatmentsContainLevel(indicatorQueryResp.getTreatments(), LevelOfEvidence.LEVEL_3A));

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR-EGFR", "vIII", "structural_variant", StructuralVariantType.DELETION, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR vIII alteration is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR-EGFR", "vIII", "structural_variant", StructuralVariantType.DELETION, "NSCLC", "", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR vIII alteration is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "vIII", "structural_variant", StructuralVariantType.DELETION, "NSCLC", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", "The EGFR vIII alteration is known to be oncogenic.", indicatorQueryResp.getVariantSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ALK", "", "structural_variant", StructuralVariantType.DELETION, "Gastrointestinal Stromal Tumor", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not unknown.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ALK", "", "structural_variant", StructuralVariantType.DELETION, "Gastrointestinal Stromal Tumor", "fusion", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not likely oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());

        // For duplication, proteinStart/proteinEnd in OncoKB annotation should overwrite the input from outside
        // The hotspot range is 65_77indel.
        // In original design, if the caller calls the duplication happened at 78, this variant will not be qualified for predicted oncogenic. But it could be treated the insertion happened at 68.
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "AKT1", "P68_C77dup", null, null, "Gastrointestinal Stromal Tumor", "In_Frame_Ins", 78, 78, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not Likely Oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The isHotspot is not true, but it should be.", Boolean.TRUE, indicatorQueryResp.getHotspot());

        // For variant has VUS as relevant alteration and the this VUS happens to be a hotspot, this variant should not be annotated as hotspot mutation.
        // No longer applicable
//        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "MAP2K1", "N109_R113del", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
//        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
//        assertEquals("The Oncogenicity is not unknown, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
//        assertEquals("The isHotspot is true, but it should not be.", Boolean.FALSE, indicatorQueryResp.getHotspot());
//        assertEquals("The highest level of sensitive treatment is not null, but it should be.", null, indicatorQueryResp.getHighestSensitiveLevel());

        // For non-functional fusion, the Deletion should still be mapped
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRCA2", null, "structural_variant", StructuralVariantType.DELETION, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not Oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The mutation effect is not expected.", "Loss-of-function", indicatorQueryResp.getMutationEffect().getKnownEffect());
        assertEquals("The highest level of sensitive treatment is not level 1, but it should be.", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());

        // For special case in cBioPortal
        // The fusion event may not have the keyword `fusion` in the protein change, but the mutation type correctly added as Fusion
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "NTRK3", "ETV6-NTRK3", AlterationType.FUSION.label(), null, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not Oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "NTRK3", "ETV6-NTRK3", "Mutation", null, "Ovarian Cancer", "Fusion", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not Oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());

        // Inconclusive should be respected
        // PIK3CA C378Y is inconclusive, even C378R is likely oncogenic, we should still use inconclusive for C378Y
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PIK3CA", "C378Y", null, null, null, null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The alteration should exist.", true, indicatorQueryResp.getVariantExist());
        assertEquals("The Oncogenicity is not inconclusive, but it should be.", Oncogenicity.INCONCLUSIVE.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The mutation summary does not match.", "There is conflicting and/or weak data describing the biological significance of the PIK3CA C378Y mutation.", indicatorQueryResp.getVariantSummary());
        assertEquals("There should not be any sensitive therapeutic associated.", null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("There should not be any resistance therapeutic associated.", null, indicatorQueryResp.getHighestResistanceLevel());

        // Check the predefined TERT Promoter summary
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TERT", "Promoter", null, null, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not Likely Oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", SummaryUtils.TERT_PROMOTER_MUTATION_SUMMARY, indicatorQueryResp.getVariantSummary());
        assertEquals("The tumor type summary is not expected.", SummaryUtils.TERT_PROMOTER_NO_THERAPY_TUMOR_TYPE_SUMMARY.replace("[[tumor type]]", "ovarian cancer"), indicatorQueryResp.getTumorTypeSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TERT", "promoter ", null, null, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not Likely Oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The variant summary is not expected.", SummaryUtils.TERT_PROMOTER_MUTATION_SUMMARY, indicatorQueryResp.getVariantSummary());
        assertEquals("The tumor type summary is not expected.", SummaryUtils.TERT_PROMOTER_NO_THERAPY_TUMOR_TYPE_SUMMARY.replace("[[tumor type]]", "ovarian cancer"), indicatorQueryResp.getTumorTypeSummary());

        // Check the case when alteration is empty but consequence is specified. This avoids positional variants
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KIT", "", null, null, "Ovarian Cancer", MISSENSE_VARIANT, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not unknown, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There should not be any treatments", indicatorQueryResp.getTreatments().isEmpty());


        // Test enforced consequence, especially a silent mutation could be a splice mutation by specifying the consequence in the request
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TP53", "X187=", null, null, "CLLSLL", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There highest prognostic level should be empty", indicatorQueryResp.getHighestPrognosticImplicationLevel() == null);

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TP53", "X187=", null, null, "CLLSLL", "splice_region_variant", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not likely oncogenic, but it should.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There highest prognostic level should not be empty", indicatorQueryResp.getHighestPrognosticImplicationLevel() != null);


        // Give a default mutation effect when it is not available: Unknown
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "CHEK2", "R111111C", null, null, "Myelodysplastic Workup", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The Oncogenicity is not empty, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The Oncogenicity is not empty, but it should be.", MutationEffect.UNKNOWN.getMutationEffect(), indicatorQueryResp.getMutationEffect().getKnownEffect());
        assertEquals("There should not be any sensitive therapeutic associated.", null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("There should not be any resistance therapeutic associated.", null, indicatorQueryResp.getHighestResistanceLevel());

        // For hotspot mutation, if the tumor type is unknown from oncotree, we should not propagate the treatment from oncogenic mutations
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PIK3R1", "W583del", null, null, null, null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The mutation effect is null, but it should not be.", indicatorQueryResp.getMutationEffect() != null);
        assertEquals("The mutation effect is not unknown, but it should be.", "Unknown", indicatorQueryResp.getMutationEffect().getKnownEffect());


        // Test resistance mutation
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "C797S", null, null, "Lung Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Resistance'", Oncogenicity.RESISTANCE.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be 4",
            null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The highest resistance level should be R1",
            LevelOfEvidence.LEVEL_R2, indicatorQueryResp.getHighestResistanceLevel());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "Cys797Ser", null, null, "Lung Adenocarcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Resistance'", Oncogenicity.RESISTANCE.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level should be 4",
            null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The highest resistance level should be R1",
            LevelOfEvidence.LEVEL_R2, indicatorQueryResp.getHighestResistanceLevel());

        // The oncogenic mutations should not be mapped to Resistance mutation. So the summary from OM should not apply here.
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KIT", "D820E", null, null, "AMLRUNX1RUNX1T1", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Resistance'", Oncogenicity.RESISTANCE.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The tumor type summary is not expected.",
            "There are no FDA-approved or NCCN-compendium listed treatments specifically for patients with KIT D820E mutant AML with t(8;21)(q22;q22.1);RUNX1-RUNX1T1.", indicatorQueryResp.getTumorTypeSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KIT", "Asp820Glu", null, null, "AMLRUNX1RUNX1T1", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertEquals("The oncogenicity should be 'Resistance'", Oncogenicity.RESISTANCE.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The tumor type summary is not expected.",
            "There are no FDA-approved or NCCN-compendium listed treatments specifically for patients with KIT D820E mutant AML with t(8;21)(q22;q22.1);RUNX1-RUNX1T1.", indicatorQueryResp.getTumorTypeSummary());


        // The oncogenic mutations should not be mapped to Resistance mutation. So the summary from OM should not apply here.
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "MEL", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertTrue("The data version should not be empty.", StringUtils.isNotEmpty(indicatorQueryResp.getDataVersion()));
        assertEquals("The data version is not expected.", MainUtils.getDataVersion(), indicatorQueryResp.getDataVersion());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "Val600Glu", null, null, "MEL", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);
        assertTrue("The data version should not be empty.", StringUtils.isNotEmpty(indicatorQueryResp.getDataVersion()));
        assertEquals("The data version is not expected.", MainUtils.getDataVersion(), indicatorQueryResp.getDataVersion());

        /**
         * Comparing between two queries
         */
        Query query1, query2;
        IndicatorQueryResp resp1, resp2;

        // Match Gain with Amplification
        query1 = new Query("PTEN", "Gain", null);
        query2 = new Query("PTEN", "Amplification", null);
        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);
        assertTrue("The oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));

        // Match Loss with Deletion
        query1 = new Query("PTEN", "Loss", null);
        query2 = new Query("PTEN", "Deletion", null);
        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);
        assertTrue("The oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));

        // Match three-letter amino acid code with one-letter amino acid code
        query1 = new Query("BRAF", "Val600Glu", null);
        query2 = new Query("BRAF", "V600E", null);
        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);
        assertTrue("The oncogenicities are not the same, but they should be.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));


        // Match intragenic to structural variant deletion
        query1 = new Query("ESR1", "ESR1 intragenic", "Melanoma");
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ESR1", null, "structural_variant", StructuralVariantType.DELETION, "Melanoma", null, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);
        assertTrue("The oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));


        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, 2099, null, null, "structural_variant", StructuralVariantType.DELETION, "Melanoma", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ESR1", null, "structural_variant", StructuralVariantType.DELETION, "Melanoma", null, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);
        assertTrue("The oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));

        // Match Truncating Mutations section to Deletion if no Deletion section specifically curated
        // In this test case, MAP3K1 does not have Deletion beening curated yet, but this may be changed due to
        // continue annotating process.
        query1 = new Query("MAP3K1", "T779*", null);
        query2 = new Query("MAP3K1", "Deletion", null);
        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);
        assertTrue("The oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));

        // Check unknown denominator fusion, it should return same data as querying specific fusion.
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "CUL1-BRAF Fusion", null, null, "Ovarian Cancer", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "CUL1-BRAF", null, "fusion", null, "Ovarian Cancer", null, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        // Check hugoSymbol and entrezGene pair
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, 673, null, "V600E", null, null, "Melanoma", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "Melanoma", null, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        // Compare the result when consequence is empty/null/missense_mutation
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, 673, null, "V600E", null, null, "Melanoma", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, 673, null, "V600E", null, null, "Melanoma", "", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, 673, null, "V600E", null, null, "Melanoma", "missense_mutation", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));


        //EntrezGeneId has higher priority then hugoSymbol
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, 673, "EGFR", "V600E", null, null, "Melanoma", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "Melanoma", null, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        //Check whether empty hugoSymbol will effect the result
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, 673, "", "V600E", null, null, "Melanoma", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "Melanoma", null, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        //Check both fusion name formats are supproted(GeneA-GeneB Fusion, GeneA::GeneB)
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "AGK-BRAF", null, AlterationType.STRUCTURAL_VARIANT.name(), null, "Melanoma", "fusion", null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "AGK::BRAF", null, AlterationType.STRUCTURAL_VARIANT.name(), null, "Melanoma", "fusion", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "AGK-BRAF", null, null, StructuralVariantType.FUSION, "Melanoma", "fusion", null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "AGK::BRAF", null, null, StructuralVariantType.FUSION, "Melanoma", "fusion", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PRKACA-DNAJB1", null, AlterationType.STRUCTURAL_VARIANT.name(), null, "Melanoma", "fusion", null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "PRKACA::DNAJB1", null, AlterationType.STRUCTURAL_VARIANT.name(), null, "Melanoma", "fusion", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        // Compare EGFR CTD AND EGFR, EGFR CTD
        // Check EGFR CTD
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "EGFR CTD", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
        resp1 = IndicatorUtils.processQuery(query, null, true, null, false);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "CTD", null, null, "Gastrointestinal Stromal Tumor", null, null, null, null);
        resp2 = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("The Oncogenicities are not the same.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));


        //Other Biomarker tests - MSI-H
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, null, "MSI-H", null, null, "Colorectal Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist is not false, but it should be.", indicatorQueryResp.getGeneExist() == false);
        assertEquals("The oncogenicity is not Oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level is not 1, but it should be.", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The gene summary is not empty, but it should be.", "", indicatorQueryResp.getGeneSummary());
        assertEquals("The variant summary is not expected.", "Genetic or epigenetic alterations resulting in loss of function of mismatch repair (MMR) genes can lead to a microsatellite instability-high (MSI-H)/mismatch repair deficient (MMR-D) phenotype.", indicatorQueryResp.getVariantSummary());
        assertEquals("The tumor type summary is not expected.", "The anti-PD-1 antibodies pembrolizumab or nivolumab, as single-agents, and the anti-CTLA4 antibody ipilimumab in combination with nivolumab are FDA-approved for the treatment of patients with MMR-D or MSI-H metastatic colorectal cancer.", indicatorQueryResp.getTumorTypeSummary());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, null, "MSI-H", null, null, "Cervical Endometrioid Carcinoma", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist is not false, but it should be.", indicatorQueryResp.getGeneExist() == false);
        assertEquals("The oncogenicity is not Oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level is not 1, but it should be.", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The gene summary is not empty, but it should be.", "", indicatorQueryResp.getGeneSummary());
        assertEquals("The variant summary is not expected.", "Genetic or epigenetic alterations resulting in loss of function of mismatch repair (MMR) genes can lead to a microsatellite instability-high (MSI-H)/mismatch repair deficient (MMR-D) phenotype.", indicatorQueryResp.getVariantSummary());
        assertEquals("The tumor type summary is not expected.", "The anti-PD-1 antibody pembrolizumab is an FDA-approved drug for therapy of adult and pediatric patients with unresectable or metastatic MSI-H/MMR-D solid cancers that have progressed following prior treatment.", indicatorQueryResp.getTumorTypeSummary());

        //Other Biomarker tests - TMB-H
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, null, "TMB-H", null, null, "Colorectal Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist is not false, but it should be.", indicatorQueryResp.getGeneExist() == false);
        assertEquals("The oncogenicity is not Oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level is not 1, but it should be.", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The gene summary is not empty, but it should be.", "", indicatorQueryResp.getGeneSummary());
        assertEquals("The variant summary is not expected.", "An association between high Tumor Mutational Burden (TMB), defined as the number of somatic mutations per megabase (mut/Mb) of genome sequenced, and response to immune checkpoint inhibitors has been reported in several solid tumor types.", indicatorQueryResp.getVariantSummary());
        assertEquals("The tumor type summary is not expected.", "The TMB for this sample is ≥10 mut/Mb. The anti-PD-1 antibody pembrolizumab is FDA-approved for the treatment of adult and pediatric patients with unresectable or metastatic solid tumors with a mutation burden of ≥10 mut/Mb.", indicatorQueryResp.getTumorTypeSummary());

        // Alternative allele should not get the diagnostic from curated alterations
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600L", null, null, "hairy cell leukemia", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The diagnostic implication list should be empty, but it's not", 0, indicatorQueryResp.getDiagnosticImplications().size());
        assertTrue("The highest diagnostic level should be empty, but it's not", indicatorQueryResp.getHighestDiagnosticImplicationLevel() == null);

        // Check structural variant fusion
        // a) BRAF is oncogenic gene. No Truncating Mutations is curated.
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF-BRAF", null, "structural_variant", StructuralVariantType.INSERTION, "Ovarian Cancer", "fusion", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The highest sensitive level of BRAF insertion should be Level 3A", LevelOfEvidence.LEVEL_3A, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The oncogenicity of BRAF insertion functional fusion should be Likely Oncogenic", "Likely Oncogenic", indicatorQueryResp.getOncogenic());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF-BRAF", null, "structural_variant", StructuralVariantType.INSERTION, "Ovarian Cancer", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The highest sensitive level of BRAF insertion should be null", null, indicatorQueryResp.getHighestSensitiveLevel());
        assertEquals("The oncogenicity of BRAF insertion non-functional fusion should be unknown", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());

        // b)  KMT2A is tumor suppressor gene, it has both fusions and Truncating Mutations curated. Functional fusion should have fusions mapped, non-functional genes should have Truncating Mutations mapped. And both of them are likely oncogenic.
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KMT2A-MLLT3", null, "structural_variant", StructuralVariantType.TRANSLOCATION, "Acute Myeloid Leukemia", "fusion", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity of KMT2A translocation functional fusion should be likely oncogenic", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KMT2A-MLLT3", null, "structural_variant", StructuralVariantType.TRANSLOCATION, "Acute Myeloid Leukemia", "fusion", null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity of KMT2A translocation non-functional fusion should be likely oncogenic", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());

        // Test for the newly added gene
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KLF5", "P301S", null, null, "Acute Myeloid Leukemia", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not likely oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());

        // Test for the promoter mutation
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TERT", null, null, null, "Acute Myeloid Leukemia", UPSTREAM_GENE, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not likely oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());

//        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TERT", null, null, null, "Acute Myeloid Leukemia", FIVE_UTR, null, null, null);
//        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
//        assertEquals("The oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());

        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TERT", "", null, null, "Acute Myeloid Leukemia", UPSTREAM_GENE, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not likley oncogenic, but it should be.", Oncogenicity.LIKELY.getOncogenic(), indicatorQueryResp.getOncogenic());


        // Check the same protein change in different reference genome
        query = new Query(null, ReferenceGenome.GRCh37, null, "MYD88", "M232T", null, null, "Acute Myeloid Leukemia", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The mutation effect is not oncogenic, but it should be.", MutationEffect.GAIN_OF_FUNCTION.getMutationEffect(), indicatorQueryResp.getMutationEffect().getKnownEffect());
        assertTrue("The mutation is not hotspot, but it should be.", indicatorQueryResp.getHotspot());

        query = new Query(null, ReferenceGenome.GRCh38, null, "MYD88", "M232T", null, null, "Acute Myeloid Leukemia", null, null, null, null);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertEquals("The oncogenicity is not unknown, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The mutation effect is not unknown, but it should be.", MutationEffect.UNKNOWN.getMutationEffect(), indicatorQueryResp.getMutationEffect().getKnownEffect());
        assertTrue("The mutation is not hotspot, but it should be.", !indicatorQueryResp.getHotspot());

        // Test Structural Variants
        // Fusion as alteration type should have same result from structural variant as alteration type and fusion as consequence
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR-RAD51", null, "fusion", null, "Ovarian Cancer", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR-RAD51", null, "structural_variant", StructuralVariantType.INSERTION, "Ovarian Cancer", "fusion", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        pairComparison(resp1, resp2);

        // When the structural variant is functional fusion, the alteration name if is fusion, it should be ignored.
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KIF5B-MET", null, "structural_variant", StructuralVariantType.DELETION, "Lung Adenocarcinoma", "fusion", null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KIF5B-MET", "FUSION", "structural_variant", StructuralVariantType.DELETION, "Lung Adenocarcinoma", "fusion", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        pairComparison(resp1, resp2);

        // When queried gene order is different, but one of the combinations exists, then the variantExist should be set to true
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BCR-ABL1", null, "structural_variant", StructuralVariantType.DELETION, "Lung Adenocarcinoma", "fusion", null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ABL1-BCR", null, "structural_variant", StructuralVariantType.DELETION, "Lung Adenocarcinoma", "fusion", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        pairComparison(resp1, resp2, true);

        // handle mixed input for structural variant deletion
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "KDD", "structural_variant", StructuralVariantType.DUPLICATION, "NSCLC", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "KDD", "structural_variant", StructuralVariantType.DUPLICATION, "NSCLC", "KDD", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        pairComparison(resp1, resp2);

        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "KDD", null, null, "NSCLC", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "EGFR", "KDD", "structural_variant", StructuralVariantType.DUPLICATION, "NSCLC", "fusion", null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        pairComparison(resp1, resp2);

        // Disabled test because RDD and Histiocytosis have different highest sensitive level
        // test RDD, it used to belong to Histiocytic Disorder, now under Histiocytosis
        // query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "MAP2K1", "C121S", null, null, "RDD", null, null, null, null);
        // query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "MAP2K1", "C121S", null, null, "Histiocytosis", null, null, null, null);
        // resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        // resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        // pairComparison(resp1, resp2);

        // Test positional variant but with a missense variant consequence
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TP53", "R248", null, null, "RDD", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "TP53", "R248", null, null, "RDD", MISSENSE_VARIANT, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        pairComparison(resp1, resp2);

        // Test using official hugo symbol and gene alias
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "ABL1", "D276G", null, null, "MEL", null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "c-ABL", "D276G", null, null, "MEL", MISSENSE_VARIANT, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);
        pairComparison(resp1, resp2, true);
        assertEquals("The summary should not be the same, but it is.", resp1.getGeneSummary(), resp2.getGeneSummary());

        // The annotation service should return all treatments without changing the level when tumor type is specified
        query1 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KRAS", "G12C", null, null, null, null, null, null, null);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "KRAS", "G12C", null, null, "NSCLC", MISSENSE_VARIANT, null, null, null);
        resp1 = IndicatorUtils.processQuery(query1, null, true, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, true, null, false);

        assertTrue("The gene exist should be the same.", resp1.getGeneExist().equals(resp2.getGeneExist()));
        assertTrue("The variant exist should be the same.", resp1.getVariantExist().equals(resp2.getVariantExist()));
        assertTrue("The oncogenicity should be the same.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The VUS info should be the same", resp1.getVUS().equals(resp2.getVUS()));
        assertTrue("The gene summary should be the same.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("The variant summary should be the same.", resp1.getVariantSummary().equals(resp2.getVariantSummary()));

        assertSame("The highest sensitive level should be the same.", resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel());
        assertNotSame("The highest resistance level should not be the same. R1 belongs to colorectal", resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel());

        assertNotSame("The annotation without tumor type should have more treatments", resp1.getTreatments().size(), resp2.getTreatments().size());

    }

    @Test
    public void testProcessQueryWithGenomicChange() throws Exception {
        String hgvsg = "7:g.140453136A>T";
        TranscriptSummaryAlterationResult transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.HGVS_G, DEFAULT_REFERENCE_GENOME, hgvsg);
        Query query = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult, hgvsg);
        IndicatorQueryResp indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist is not true, but it should be.", indicatorQueryResp.getGeneExist() == true);
        assertEquals("The oncogenicity is not Oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level is not 1, but it should be.", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());

        hgvsg = ":g.140453136A>T";
        transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.HGVS_G, DEFAULT_REFERENCE_GENOME, hgvsg);
        query = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult, hgvsg);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist is not false, but it should be.", indicatorQueryResp.getGeneExist() == false);
        assertEquals("The oncogenicity is not Unknown, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level is not null, but it should be.", null, indicatorQueryResp.getHighestSensitiveLevel());

        hgvsg = "7:g.140453136A>T";
        transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.HGVS_G, DEFAULT_REFERENCE_GENOME, hgvsg);
        Query query1 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult, hgvsg);
        Query query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "Melanoma", null, null, null, null);

        IndicatorQueryResp resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        IndicatorQueryResp resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);

        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        // different format of chromosome should have the same annotation
        String hgvsg1 = "23:g.100611185G>A";
        String hgvsg2 = "X:g.100611185G>A";
        TranscriptSummaryAlterationResult transcriptSummaryAlterationResult1 = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.HGVS_G, DEFAULT_REFERENCE_GENOME, hgvsg1);
        TranscriptSummaryAlterationResult transcriptSummaryAlterationResult2 = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.HGVS_G, DEFAULT_REFERENCE_GENOME, hgvsg2);
        query1 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult1, hgvsg1);
        query2 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult2, hgvsg2);

        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);

        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));


        // Test indicator endpoint supports genomic location
        String genomicLocation = "7,140453136,140453136,A,T";
        transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.GENOMIC_LOCATION, DEFAULT_REFERENCE_GENOME, genomicLocation);
        query = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult, genomicLocation);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist is not true, but it should be.", indicatorQueryResp.getGeneExist() == true);
        assertEquals("The oncogenicity is not Oncogenic, but it should be.", Oncogenicity.YES.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level is not 1, but it should be.", LevelOfEvidence.LEVEL_1, indicatorQueryResp.getHighestSensitiveLevel());

        hgvsg = ",140453136,140453136,A,T";
        transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.GENOMIC_LOCATION, DEFAULT_REFERENCE_GENOME, hgvsg);
        query = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult, hgvsg);
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist is not false, but it should be.", indicatorQueryResp.getGeneExist() == false);
        assertEquals("The oncogenicity is not Unknown, but it should be.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertEquals("The highest sensitive level is not null, but it should be.", null, indicatorQueryResp.getHighestSensitiveLevel());

        genomicLocation = "7,140453136,140453136,A,T";
        transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.GENOMIC_LOCATION, DEFAULT_REFERENCE_GENOME, genomicLocation);
        query1 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult, genomicLocation);
        query2 = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V600E", null, null, "Melanoma", null, null, null, null);

        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);

        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        // different format of chromosome should have the same annotation
        String genomicLocation1 = "23,100611185,100611185,G,A";
        String genomicLocation2 = "X,100611185,100611185,G,A";
        transcriptSummaryAlterationResult1 = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.GENOMIC_LOCATION, DEFAULT_REFERENCE_GENOME, genomicLocation1);
        transcriptSummaryAlterationResult2 = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.GENOMIC_LOCATION, DEFAULT_REFERENCE_GENOME, genomicLocation2);
        query1 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult1, genomicLocation1);
        query2 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult2, genomicLocation2);

        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);

        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));

        // String query vs List GenomicLocation should have the same response
        genomicLocation = "23,100611185,100611185,G,A";
        transcriptSummaryAlterationResult1 = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.GENOMIC_LOCATION, DEFAULT_REFERENCE_GENOME, genomicLocation);
        List<VariantAnnotation> annotations = GenomeNexusUtils.getGenomicLocationVariantsAnnotation(Collections.singletonList(GenomeNexusUtils.convertGenomicLocation(genomicLocation)), DEFAULT_REFERENCE_GENOME);
        List<TranscriptSummaryAlterationResult> map = AlterationUtils.getAlterationsFromGenomeNexus(annotations, DEFAULT_REFERENCE_GENOME);
        transcriptSummaryAlterationResult2 = map.get(0);
        query1 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult1, genomicLocation1);
        query2 = QueryUtils.getQueryFromAlteration(DEFAULT_REFERENCE_GENOME, "Melanoma", transcriptSummaryAlterationResult2, genomicLocation2);

        resp1 = IndicatorUtils.processQuery(query1, null, false, null, false);
        resp2 = IndicatorUtils.processQuery(query2, null, false, null, false);

        assertTrue("Genes are not the same, but they should.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
        assertTrue("Oncogenicities are not the same, but they should.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("Treatments are not the same, but they should.", resp1.getTreatments().equals(resp2.getTreatments()));
        assertTrue("Highest sensitive levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestSensitiveLevel(), resp2.getHighestSensitiveLevel()));
        assertTrue("Highest resistance levels are not the same, but they should.", LevelUtils.areSameLevels(resp1.getHighestResistanceLevel(), resp2.getHighestResistanceLevel()));
    }

    // Most of the annotation service should not have clinical implication returned when alteration is not available.
    // The only exception is when to do annotation/search where gene query is possible and clinical implications should be returned which we cover in AnnotationSearchUtilsTest.java
    @Test
    public void testProcessQueryWhenAltIsEmpty() {
        Query queryWithOnlyGene = new Query(null, DEFAULT_REFERENCE_GENOME, null, "FGFR3", "", null, null, "", null, null, null, null);
        shouldNotHaveClinicalImplication(IndicatorUtils.processQuery(queryWithOnlyGene, null, true, null, false));
        queryWithOnlyGene = new Query(null, DEFAULT_REFERENCE_GENOME, null, "FGFR3", null, null, null, "", null, null, null, null);
        shouldNotHaveClinicalImplication(IndicatorUtils.processQuery(queryWithOnlyGene, null, true, null, false));

        Query queryWithCC = new Query(null, DEFAULT_REFERENCE_GENOME, null, "FGFR3", "", null, null, "Pancreatic Adenocarcinoma", null, null, null, null);
        shouldNotHaveClinicalImplication(IndicatorUtils.processQuery(queryWithCC, null, true, null, false));
        queryWithCC = new Query(null, DEFAULT_REFERENCE_GENOME, null, "FGFR3", null, null, null, "Pancreatic Adenocarcinoma", null, null, null, null);
        shouldNotHaveClinicalImplication(IndicatorUtils.processQuery(queryWithCC, null, true, null, false));

        Query queryWithConsequence = new Query(null, DEFAULT_REFERENCE_GENOME, null, "FGFR3", "", null, null, "", "3_prime_UTR_variant", null, null, null);
        shouldNotHaveClinicalImplication(IndicatorUtils.processQuery(queryWithConsequence, null, true, null, false));
        queryWithConsequence = new Query(null, DEFAULT_REFERENCE_GENOME, null, "FGFR3", null, null, null, "", "3_prime_UTR_variant", null, null, null);
        shouldNotHaveClinicalImplication(IndicatorUtils.processQuery(queryWithConsequence, null, true, null, false));
    }

    @Test
    public void testProcessQueryGeneOnlyQuery() {
        // when alteration is specified in the query, the geneQueryOnly param doesn't make any difference
        Query queryWithAlt = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "V123M", null, null, null, null, null, null, null);
        IndicatorQueryResp indicatorQueryRespGeneOnly = IndicatorUtils.processQuery(queryWithAlt, null, true, null, true);
        IndicatorQueryResp indicatorQueryResp = IndicatorUtils.processQuery(queryWithAlt, null, true, null, false);
        pairComparison(indicatorQueryRespGeneOnly, indicatorQueryResp);

        // Test when alteration is empty string, different geneOnlyQuery will return different result
        Query query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", "", null, null, null, null, null, null, null);
        /// geneQueryOnly is true, should return clinical implications info under the gene
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, true);
        assertTrue("The geneExist in the response is not true, but it should be.", indicatorQueryResp.getGeneExist() == true);
        assertTrue("The variantSummary is not empty, but it should be.", StringUtils.isEmpty(indicatorQueryResp.getVariantSummary()));
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There isn't treatment in the response, but it should.", indicatorQueryResp.getTreatments().size() > 0);
        assertTrue("There isn't diagnostic implication in the response, but it should.", indicatorQueryResp.getDiagnosticImplications().size() > 0);

        /// geneQueryOnly is false, shouldn't return clinical implications info under the gene
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist in the response is not true, but it should be.", indicatorQueryResp.getGeneExist() == true);
        assertTrue("The variantSummary is not empty, but it should be.", StringUtils.isEmpty(indicatorQueryResp.getVariantSummary()));
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There is treatment in the response, but it should not be.", indicatorQueryResp.getTreatments().size() == 0);
        assertTrue("There is diagnostic in the response, but it should not be.", indicatorQueryResp.getDiagnosticImplications().size() == 0);


        // Test when alteration is null, different geneOnlyQuery will return different result
        query = new Query(null, DEFAULT_REFERENCE_GENOME, null, "BRAF", null, null, null, null, null, null, null, null);
        /// geneQueryOnly is true, should return clinical implications info under the gene
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, true);
        assertTrue("The geneExist in the response is not true, but it should be.", indicatorQueryResp.getGeneExist() == true);
        assertTrue("The variantSummary is not empty, but it should be.", StringUtils.isEmpty(indicatorQueryResp.getVariantSummary()));
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There isn't treatment in the response, but it should.", indicatorQueryResp.getTreatments().size() > 0);
        assertTrue("There isn't diagnostic in the response, but it should.", indicatorQueryResp.getDiagnosticImplications().size() > 0);

        /// geneQueryOnly is false, shouldn't return clinical implications info under the gene
        indicatorQueryResp = IndicatorUtils.processQuery(query, null, true, null, false);
        assertTrue("The geneExist in the response is not true, but it should be.", indicatorQueryResp.getGeneExist() == true);
        assertTrue("The variantSummary is not empty, but it should be.", StringUtils.isEmpty(indicatorQueryResp.getVariantSummary()));
        assertEquals("The oncogenicity is not unknown, but it should.", Oncogenicity.UNKNOWN.getOncogenic(), indicatorQueryResp.getOncogenic());
        assertTrue("There is treatment in the response, but it should not be.", indicatorQueryResp.getTreatments().size() == 0);
        assertTrue("There is diagnostic in the response, but it should not be.", indicatorQueryResp.getDiagnosticImplications().size() == 0);

    }

    @Test
    public void testFilterImplication() {
        TumorType melanoma = new TumorType();
        melanoma.setName("Melanoma");
        melanoma.setCode("MEL");
        MainType mainType = new MainType();
        mainType.setName("Melanoma");
        melanoma.setMainType(mainType);

        TumorType nsclc = new TumorType();
        nsclc.setName("Non-Small Cell Lung Cancer");
        nsclc.setCode("NSCLC");
        mainType = new MainType();
        mainType.setName("Non-Small Cell Lung Cancer");
        nsclc.setMainType(mainType);

        Implication implication1 = new Implication();
        implication1.setDescription("implication1");
        implication1.setTumorType(melanoma);
        implication1.setLevelOfEvidence(LevelOfEvidence.LEVEL_Dx1);

        Implication implication2 = new Implication();
        implication2.setDescription("implication2");
        implication2.setTumorType(melanoma);
        implication2.setLevelOfEvidence(LevelOfEvidence.LEVEL_Dx1);

        Implication implication3 = new Implication();
        implication3.setDescription("implication3");
        implication3.setTumorType(nsclc);
        implication3.setLevelOfEvidence(LevelOfEvidence.LEVEL_Dx1);

        // only keep one if the tumor type and level is the same, and the one has lower index should be picked;
        List<Implication> implications = new ArrayList<>();
        implications.add(implication1);
        implications.add(implication2);
        implications.add(implication3);

        List<Implication> filteredImplications = IndicatorUtils.filterImplication(implications);
        assertEquals(2, filteredImplications.size());
        assertEquals("implication1", filteredImplications.get(0).getDescription());
        assertEquals("implication3", filteredImplications.get(1).getDescription());


        // if the level of evidence is null, filter the result out
        Implication implication4 = new Implication();
        implication4.setDescription("implication3");
        implication4.setTumorType(melanoma);
        implication4.setLevelOfEvidence(null);

        implications = new ArrayList<>();
        implications.add(implication4);
        filteredImplications = IndicatorUtils.filterImplication(implications);
        assertEquals(0, filteredImplications.size());
    }

    private void shouldNotHaveClinicalImplication(IndicatorQueryResp resp) {
        assertEquals("The highest sensitivity level of evidence should be empty, but it's not", resp.getHighestSensitiveLevel(), null);
        assertEquals("The highest resistance level of evidence should be empty, but it's not", resp.getHighestResistanceLevel(), null);
        assertEquals("The highest diagnostic level of evidence should be empty, but it's not", resp.getHighestDiagnosticImplicationLevel(), null);
        assertEquals("The highest prognostic level of evidence should be empty, but it's not", resp.getHighestPrognosticImplicationLevel(), null);
        assertTrue("The treatment list should be empty, but it's not", resp.getTreatments().isEmpty());
        assertTrue("The diagnostic implication list should be empty, but it's not", resp.getDiagnosticImplications().isEmpty());
        assertTrue("The prognostic implication list should be empty, but it's not", resp.getPrognosticImplications().isEmpty());
    }


    private void pairComparison(IndicatorQueryResp resp1, IndicatorQueryResp resp2) {
        pairComparison(resp1, resp2, false);
    }

    private void pairComparison(IndicatorQueryResp resp1, IndicatorQueryResp resp2, boolean skipSummary) {
        assertTrue("The gene exist should be the same.", resp1.getGeneExist().equals(resp2.getGeneExist()));
        assertTrue("The variant exist should be the same.", resp1.getVariantExist().equals(resp2.getVariantExist()));
        assertTrue("The oncogenicity should be the same.", resp1.getOncogenic().equals(resp2.getOncogenic()));
        assertTrue("The VUS info should be the same", resp1.getVUS().equals(resp2.getVUS()));

        if (resp1.getHighestSensitiveLevel() == null || resp2.getHighestSensitiveLevel() == null) {
            assertTrue("The highest sensitive level should be the same.", resp1.getHighestSensitiveLevel() == resp2.getHighestSensitiveLevel());
        } else {
            assertTrue("The highest sensitive level should be the same.", resp1.getHighestSensitiveLevel().equals(resp2.getHighestSensitiveLevel()));
        }

        if (resp1.getHighestResistanceLevel() == null || resp2.getHighestResistanceLevel() == null) {
            assertTrue("The highest sensitive level should be the same.", resp1.getHighestResistanceLevel() == resp2.getHighestResistanceLevel());
        } else {
            assertTrue("The highest resistance level should be the same.", resp1.getHighestResistanceLevel().equals(resp2.getHighestResistanceLevel()));
        }

        if (!skipSummary) {
            assertTrue("The gene summary should be the same.", resp1.getGeneSummary().equals(resp2.getGeneSummary()));
            assertTrue("The variant summary should be the same.", resp1.getVariantSummary().equals(resp2.getVariantSummary()));
            assertTrue("The tumor type summary should be the same.", resp1.getTumorTypeSummary().equals(resp2.getTumorTypeSummary()));
        }
    }

    private Boolean treatmentsContainLevel(List<IndicatorQueryTreatment> treatments, LevelOfEvidence level) {
        if (level == null || treatments == null) {
            return false;
        }

        for (IndicatorQueryTreatment treatment : treatments) {
            if (treatment.getLevel() != null && treatment.getLevel().equals(level)) {
                return true;
            }
        }
        return false;
    }
}
