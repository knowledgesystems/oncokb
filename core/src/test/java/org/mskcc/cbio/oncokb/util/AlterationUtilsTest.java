package org.mskcc.cbio.oncokb.util;

import static org.mskcc.cbio.oncokb.Constants.DEFAULT_REFERENCE_GENOME;

import java.util.ArrayList;
import java.util.List;

import org.mskcc.cbio.oncokb.model.Alteration;
import org.mskcc.cbio.oncokb.model.FrameshiftVariant;
import org.mskcc.cbio.oncokb.model.Gene;

import junit.framework.TestCase;

/**
 * Created by Hongxin Zhang on 6/20/18.
 */
public class AlterationUtilsTest extends TestCase {
    public void testGetRevertFusions() throws Exception {
        Alteration alteration = createBRAFAlteration("BRAF-MKRN1 fusion");

        // Check when alteration is not available
        List<Alteration> fullAlterations = new ArrayList<>();
        Alteration result = AlterationUtils.getRevertFusions(DEFAULT_REFERENCE_GENOME, alteration, fullAlterations);
        assertEquals("The result should be null", null, result);

        fullAlterations = new ArrayList<>();
        fullAlterations.add(createBRAFAlteration("BRAF-MKRN1 fusion"));
        result = AlterationUtils.getRevertFusions(DEFAULT_REFERENCE_GENOME, alteration, fullAlterations);
        assertEquals("The result should be null", null, result);

        fullAlterations = new ArrayList<>();
        fullAlterations.add(createBRAFAlteration("MKRN1-BRAF fusion"));
        result = AlterationUtils.getRevertFusions(DEFAULT_REFERENCE_GENOME, alteration, fullAlterations);
        assertTrue("The result should not be null", result != null);

    }

    private Alteration createBRAFAlteration(String alterationName) {
        Gene gene = GeneUtils.getGeneByHugoSymbol("BRAF");
        Alteration alteration = new Alteration();
        alteration.setGene(gene);
        alteration.setAlteration(alterationName);
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());
        return alteration;
    }

    public void testGetPositionedAlterations() throws Exception {
        Alteration alteration = new Alteration();
        alteration.setGene(GeneUtils.getGeneByHugoSymbol("BRAF"));
        alteration.setAlteration("V600E");
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());

        List<Alteration> positionedAlterations = AlterationUtils.getPositionedAlterations(DEFAULT_REFERENCE_GENOME, alteration);
        List<String> alterations = new ArrayList<>();
        for (Alteration alt : positionedAlterations) {
            alterations.add(alt.getAlteration());
        }
        assertEquals("V600,V600 {excluding V600E; V600K}", MainUtils.listToString(alterations, ",", true));

        // non missense should not be annotated
        alteration = new Alteration();
        alteration.setGene(GeneUtils.getGeneByHugoSymbol("BRAF"));
        alteration.setAlteration("V600del");
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());

        positionedAlterations = AlterationUtils.getPositionedAlterations(DEFAULT_REFERENCE_GENOME, alteration);
        alterations = new ArrayList<>();
        for (Alteration alt : positionedAlterations) {
            alterations.add(alt.getAlteration());
        }
        assertEquals("", MainUtils.listToString(alterations, ","));
    }

    public void testGetRelevantAlterations() throws Exception {
        // Check when the consequence is not exactly matched to the alteration in the DB even the alteration is the same
        Gene gene = GeneUtils.getGeneByHugoSymbol("PIK3R1");
        Alteration alteration = new Alteration();
        alteration.setAlteration("W583del");
        alteration.setGene(gene);
        alteration.setConsequence(VariantConsequenceUtils.findVariantConsequenceByTerm("splice_region_variant"));
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());

        List<Alteration> alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, alteration);
        String relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "Truncating Mutations", relevantAltsName);


        // if the consequence matches with the alteration in DB
        alteration = new Alteration();
        alteration.setAlteration("W583del");
        alteration.setGene(gene);
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());

        alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, alteration);
        relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "W583del", relevantAltsName);
    }

    public void testGetRelevantAlterationsForExclusion() throws Exception {
        // Test alteration when relevant alt has exclusion
        Gene gene = GeneUtils.getGeneByHugoSymbol("BRAF");
        Alteration v600e = generateAlteration(gene, "V600E");
        Alteration v600k = generateAlteration(gene, "V600K");
        Alteration v600 = generateAlteration(gene, "V600");
        Alteration v600eExcluded = generateAlteration(gene, "V600 {excluding V600E}");
        Alteration rangeMissense = generateAlteration(gene, "V600_V601delinsEB");
        Alteration rangeMissenseExcluded = generateAlteration(gene, "V600_V601delinsEB {excluding V600E}");
        Alteration rangeMissenseExcludesPosition = generateAlteration(gene, "V600_V601delinsEB {excluding V600}");

        List<Alteration> fullAlteration = new ArrayList<>();
        fullAlteration.add(v600e);
        fullAlteration.add(v600k);
        fullAlteration.add(v600);
        fullAlteration.add(v600eExcluded);
        fullAlteration.add(rangeMissense);
        fullAlteration.add(rangeMissenseExcluded);
        fullAlteration.add(rangeMissenseExcludesPosition);

        List<Alteration> alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, v600e, fullAlteration);
        String relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "V600E, V600K, V600_V601delinsEB, V600", relevantAltsName);

        alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, v600k, fullAlteration);
        relevantAltsName = AlterationUtils.toString(alterations, true);
        assertEquals("The relevant alterations do not match", "V600, V600 {excluding V600E}, V600E, V600K, V600_V601delinsEB, V600_V601delinsEB {excluding V600E}", relevantAltsName);

        // Test alteration when relevant alt has exclusion
        Alteration fusionA = generateAlteration(gene, "AKAP9-BRAF Fusion");
        Alteration fusionB = generateAlteration(gene, "AGAP3-BRAF Fusion");
        Alteration fusionC = generateAlteration(gene, "FAM131B-BRAF Fusion");
        Alteration fusions = generateAlteration(gene, "Fusions");
        Alteration oncogenicMutationsExcludesFusion = generateAlteration(gene, "Fusions {excluding AKAP9-BRAF Fusion; FAM131B-BRAF Fusion}");

        fullAlteration = new ArrayList<>();
        fullAlteration.add(fusionA);
        fullAlteration.add(fusionB);
        fullAlteration.add(fusionC);
        fullAlteration.add(fusions);
        fullAlteration.add(oncogenicMutationsExcludesFusion);

        alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, fusionA, fullAlteration);
        relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "AKAP9-BRAF Fusion, Fusions", relevantAltsName);

        alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, fusionB, fullAlteration);
        relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "AGAP3-BRAF Fusion, Fusions {excluding AKAP9-BRAF Fusion; FAM131B-BRAF Fusion}, Fusions", relevantAltsName);

        alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, fusionC, fullAlteration);
        relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "FAM131B-BRAF Fusion, Fusions", relevantAltsName);
    }

    public void testGetRelevantAlterationsWhenAltHasExclusion() throws Exception {
        Gene gene = GeneUtils.getGeneByHugoSymbol("BRAF");
        Alteration fusion = generateAlteration(gene, "AKAP9-BRAF Fusion");
        Alteration fusionsWithExcluding = generateAlteration(gene, "Fusions {excluding AKAP9-BRAF Fusion}");

        List<Alteration> fullAlteration = new ArrayList<>();
        fullAlteration.add(fusion);
        fullAlteration.add(fusionsWithExcluding);

        List<Alteration> alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, fusion, fullAlteration);
        String relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "AKAP9-BRAF Fusion", relevantAltsName);

        alterations = AlterationUtils.getRelevantAlterations(DEFAULT_REFERENCE_GENOME, fusionsWithExcluding, fullAlteration);
        relevantAltsName = AlterationUtils.toString(alterations);
        assertEquals("The relevant alterations do not match", "Fusions {excluding AKAP9-BRAF Fusion}", relevantAltsName);
    }

    private Alteration generateAlteration(Gene gene, String proteinChange) {
        Alteration alteration = new Alteration();
        alteration.setAlteration(proteinChange);
        alteration.setGene(gene);
        AlterationUtils.annotateAlteration(alteration, alteration.getAlteration());
        return alteration;
    }

    public void testSortAlterationsByTheRange() throws Exception {
        int start = 8;
        int end = 8;
        Integer[] starts = {0, 8, 8, null, 8};
        Integer[] ends = {10, 9, 8, 8, null};
        List<Alteration> alterationList = new ArrayList<>();
        for (int i = 0; i < starts.length; i++) {
            Alteration alt = new Alteration();
            alt.setProteinStart(starts[i]);
            alt.setProteinEnd(ends[i]);
            alt.setName(Integer.toString(i));
            alterationList.add(alt);
        }
        AlterationUtils.sortAlterationsByTheRange(alterationList, start, end);
        assertEquals(5, alterationList.size());
        assertEquals(alterationList.get(0).getProteinStart().intValue(), 8);
        assertEquals(alterationList.get(0).getProteinEnd().intValue(), 8);
        assertEquals(alterationList.get(1).getProteinStart().intValue(), 8);
        assertEquals(alterationList.get(1).getProteinEnd().intValue(), 9);
        assertEquals(alterationList.get(2).getProteinStart().intValue(), 0);
        assertEquals(alterationList.get(2).getProteinEnd().intValue(), 10);
        assertEquals(alterationList.get(3).getProteinStart(), null);
        assertEquals(alterationList.get(3).getProteinEnd().intValue(), 8);
        assertEquals(alterationList.get(4).getProteinStart().intValue(), 8);
        assertEquals(alterationList.get(4).getProteinEnd(), null);
    }

    public void testIsPositionVariant() throws Exception {
        Alteration alteration = AlterationUtils.getAlteration("AKT1", "E17", null, "NA", null, null, null);
        assertTrue("This variant should be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17", null, null, null, null, null);
        assertTrue("This variant should be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17*", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "EE17*", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "EE17", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "17", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17A", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "E17AA", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("AKT1", "EE17AA", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

        alteration = AlterationUtils.getAlteration("EGFR", "L747Rfs*13", null, null, null, null, null);
        assertFalse("This variant should NOT be position variant.", AlterationUtils.isPositionedAlteration(alteration));

    }

    public void testRemoveAlternativeAllele() {
        // Test regular missense variant
        Alteration v600e = createBRAFAlteration("V600E");

        // Check when alteration is not available
        List<Alteration> relevantAlterations = new ArrayList<>();
        Alteration alteration = createBRAFAlteration("V600G");
        relevantAlterations.add(alteration);
        alteration = createBRAFAlteration("Oncogenic Mutations");
        relevantAlterations.add(alteration);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, v600e, relevantAlterations);

        assertEquals(1, relevantAlterations.size());


        // Check when alteration is present in list
        relevantAlterations = new ArrayList<>();
        alteration = createBRAFAlteration("V600G");
        relevantAlterations.add(alteration);
        relevantAlterations.add(v600e);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, v600e, relevantAlterations);
        assertEquals(1, relevantAlterations.size());

        // Check when it's a mix
        relevantAlterations = new ArrayList<>();

        alteration = createBRAFAlteration("V600G");
        relevantAlterations.add(alteration);

        alteration = createBRAFAlteration("Oncogenic Mutations");
        relevantAlterations.add(alteration);

        relevantAlterations.add(v600e);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, v600e, relevantAlterations);
        assertEquals(2, relevantAlterations.size());

        // Check delins match missense
        relevantAlterations = new ArrayList<>();

        alteration = createBRAFAlteration("V600_V601delinsEK");
        relevantAlterations.add(alteration);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, v600e, relevantAlterations);
        assertEquals(1, relevantAlterations.size());

        // Check delins match missense - match
        relevantAlterations = new ArrayList<>();

        alteration = createBRAFAlteration("V599_V600delinsKE");
        relevantAlterations.add(alteration);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, v600e, relevantAlterations);
        assertEquals(1, relevantAlterations.size());

        // Check delins match missense - does not match
        relevantAlterations = new ArrayList<>();
        relevantAlterations.add(createBRAFAlteration("V599_V600delinsKK"));

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, v600e, relevantAlterations);
        assertEquals(0, relevantAlterations.size());

        // Check multi-residues match missense - match
        relevantAlterations = new ArrayList<>();

        alteration = createBRAFAlteration("VK600EI");
        relevantAlterations.add(alteration);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, v600e, relevantAlterations);
        assertEquals(1, relevantAlterations.size());

        // Check multi-residues match missense - does not match
        relevantAlterations = new ArrayList<>();

        alteration = createBRAFAlteration("VK600EI");
        relevantAlterations.add(alteration);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, createBRAFAlteration("V600K"), relevantAlterations);
        assertEquals(0, relevantAlterations.size());

        // Check missense match delins
        Alteration delins = createBRAFAlteration("V599_V600delinsKE");

        relevantAlterations = new ArrayList<>();
        relevantAlterations.add(v600e);

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, delins, relevantAlterations);
        assertEquals(1, relevantAlterations.size());

        // Check missense match delins plus mix
        relevantAlterations = new ArrayList<>();
        relevantAlterations.add(v600e);
        relevantAlterations.add(createBRAFAlteration("V600R"));
        relevantAlterations.add(createBRAFAlteration("Oncogenic Mutations"));

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, delins, relevantAlterations);
        assertEquals(2, relevantAlterations.size());

        // Check missense match delins plus positional
        relevantAlterations = new ArrayList<>();
        relevantAlterations.add(v600e);
        relevantAlterations.add(createBRAFAlteration("V600"));

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, delins, relevantAlterations);
        assertEquals(2, relevantAlterations.size());


        // Check missense match delins plus positional plus multi-residues missense
        relevantAlterations = new ArrayList<>();
        relevantAlterations.add(v600e);
        relevantAlterations.add(createBRAFAlteration("V600"));
        relevantAlterations.add(createBRAFAlteration("VK600EI"));

        AlterationUtils.removeAlternativeAllele(DEFAULT_REFERENCE_GENOME, delins, relevantAlterations);
        assertEquals(3, relevantAlterations.size());
    }

    public void testGetMissenseProteinChangesFromComplexProteinChange() {
        String test = "S768_V769delinsIL";
        List<Alteration> alterations = AlterationUtils.getMissenseProteinChangesFromComplexProteinChange(test);
        String alt = AlterationUtils.toString(alterations);
        assertEquals("768I, 769L", alt);

        test = "SV768IL";
        alterations = AlterationUtils.getMissenseProteinChangesFromComplexProteinChange(test);
        alt = AlterationUtils.toString(alterations);
        assertEquals("S768I, V769L", alt);

        // inframe insertion should not get any match
        test = "S768_V769delinsILA";
        alterations = AlterationUtils.getMissenseProteinChangesFromComplexProteinChange(test);
        assertEquals(alterations.size(), 0);
        test = "SV768ILA";
        alterations = AlterationUtils.getMissenseProteinChangesFromComplexProteinChange(test);
        alt = AlterationUtils.toString(alterations);
        assertEquals(alterations.size(), 0);
        // inframe deletion should not get any match
        test = "S768_V769delinsI";
        alterations = AlterationUtils.getMissenseProteinChangesFromComplexProteinChange(test);
        assertEquals(alterations.size(), 0);
        test = "SV768I";
        alterations = AlterationUtils.getMissenseProteinChangesFromComplexProteinChange(test);
        alt = AlterationUtils.toString(alterations);
        assertEquals(alterations.size(), 0);
    }

    public void testHgvsgFomat() {
        String hgvsg = "7:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = " 7:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = " 7:g.140453136A>T ";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));

        hgvsg = "X:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "x:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "y:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));

        // we should allow the chr prefix
        hgvsg = "chr7:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "CHR7:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "Chr7:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = " chr7:g.140453136A>T";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "chr7:g.140453136A>T ";
        assertTrue(AlterationUtils.isValidHgvsg(hgvsg));

        hgvsg = "";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = " ";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "test";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));

        hgvsg = ":g.140453136A>T";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "7g.140453136A>T";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "7:.140453136A>T";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "7:g140453136A>T";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "7:g.A>T";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = "a:g.140453136A>T";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
        hgvsg = " 7:c.140453136A>T ";
        assertFalse(AlterationUtils.isValidHgvsg(hgvsg));
    }

    public void testHgvscFormat() {
        String hgvsc = "ENST00000377346.4:c.241G>A";
        assertTrue(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "ENST00000396946.4:c.2104G>A";
        assertTrue(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "ENST00000321945:c.205C>G";
        assertTrue(AlterationUtils.isValidHgvsc(hgvsc));

        hgvsc = "";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = " ";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "test";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));

        hgvsc = "chr7:g.140453136A>T";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "g.140453136A>T";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "ENS00000396946:1234:c.205C>G";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "ENST00000396946.4:c.G>A";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "ENST00000321945c.205C>G";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
        hgvsc = "ENST00000377346.4:.241G>A";
        assertFalse(AlterationUtils.isValidHgvsc(hgvsc));
    }

    public void testTrimComment() {
        assertEquals("", AlterationUtils.trimComment(""));
        assertEquals("", AlterationUtils.trimComment(" "));
        assertEquals("", AlterationUtils.trimComment(null));

        assertEquals("A1B", AlterationUtils.trimComment("A1B"));
        assertEquals("A1B", AlterationUtils.trimComment("A1B "));
        assertEquals("A1B", AlterationUtils.trimComment(" A1B"));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B,B1C"));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B,B1C "));
        assertEquals("A1B,B1C", AlterationUtils.trimComment(" A1B,B1C"));

        assertEquals("A1B", AlterationUtils.trimComment("A1B(test1)"));
        assertEquals("A1B", AlterationUtils.trimComment("A1B (test1)"));
        assertEquals("A1B", AlterationUtils.trimComment("A1B(test1) "));
        assertEquals("A1B", AlterationUtils.trimComment("A1B(test1,test2)"));
        assertEquals("A1B", AlterationUtils.trimComment("A1B(test1, test2)"));
        assertEquals("A1B", AlterationUtils.trimComment("A1B(test1 ,test2)"));
        assertEquals("A1B", AlterationUtils.trimComment("A1B (test1,test2)"));
        assertEquals("A1B", AlterationUtils.trimComment("A1B(test1,test2) "));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B(test1,test2),B1C"));
        assertEquals("A1B, B1C", AlterationUtils.trimComment("A1B(test1,test2), B1C"));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B(test1,test2),B1C "));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B(test1,test2),B1C(test1,test2)"));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B(test1,test2),B1C (test1,test2)"));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B(test1,test2),B1C(test1,test2) "));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B(test1,test2),B1C(test1, test2)"));
        assertEquals("A1B,B1C", AlterationUtils.trimComment("A1B(test1,test2),B1C (test1 ,test2)"));

        assertEquals("A1B,B1C[test1,test2]", AlterationUtils.trimComment("A1B(test1,test2),B1C[test1,test2]"));
        assertEquals("A1B,B1C[test1,test2],C1D", AlterationUtils.trimComment("A1B(test1,test2),B1C[test1,test2],C1D(test1,test2)"));
    }

    public void testHasExclusionCriteria() {
        assertFalse(AlterationUtils.hasExclusionCriteria(null));
        assertFalse(AlterationUtils.hasExclusionCriteria(""));
        assertFalse(AlterationUtils.hasExclusionCriteria("BRAF"));
        assertFalse(AlterationUtils.hasExclusionCriteria("V600E"));

        assertTrue(AlterationUtils.hasExclusionCriteria("V600 {excluding V600E}"));
        assertTrue(AlterationUtils.hasExclusionCriteria("V600 {exclude V600E}"));
        assertTrue(AlterationUtils.hasExclusionCriteria("V600 (excluding V600E)"));
        assertTrue(AlterationUtils.hasExclusionCriteria("V600 (exclude V600E)"));

        assertTrue(AlterationUtils.hasExclusionCriteria("V600{excluding V600E}"));
        assertTrue(AlterationUtils.hasExclusionCriteria("V600{excluding V600E} "));
        assertTrue(AlterationUtils.hasExclusionCriteria("V600{excluding  V600E}"));
    }

    public void testParseFrameshiftVariant() {
        assertNull(AlterationUtils.parseFrameshiftVariant(""));
        assertNull(AlterationUtils.parseFrameshiftVariant(null));
        assertNull(AlterationUtils.parseFrameshiftVariant("600"));
        assertNull(AlterationUtils.parseFrameshiftVariant("V600E"));
        assertNull(AlterationUtils.parseFrameshiftVariant("E17*"));
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), "N", 105, 105, "E", "4");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*"), "N", 105, 105, "E", "");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*?"), "N", 105, 105, "E", "?");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("105fs*4"), "", 105, 105, "", "4");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105fs*4"), "N", 105, 105, "", "4");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("105Efs*4"), "", 105, 105, "E", "4");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs"), "N", 105, 105, "E", "");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*?"), "N", 105, 105, "E", "?");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("EED153fs"), "EED", 153, 153, "", "");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("*1069Ffs*5"), "*", 1069, 1069, "F", "5");
        suiteNotNullFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("*1069Ffs*?"), "*", 1069, 1069, "F", "?");
    }

    private void suiteNotNullFrameshiftVariant(FrameshiftVariant variant, String expectedRef, Integer expectedStart, Integer expectedEnd, String expectedVar, String expectedExtension) {
        assertEquals(expectedRef, variant.getRefResidues());
        assertEquals(expectedStart, variant.getProteinStart());
        assertEquals(expectedEnd, variant.getProteinEnd());
        assertEquals(expectedVar, variant.getVariantResidues());
        assertEquals(expectedExtension, variant.getExtension());
    }

    public void testIsSameFrameshiftVariant() {
        // not equal when variant alleles are different
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), AlterationUtils.parseFrameshiftVariant("N105Tfs*4")));
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Tfs*4"), AlterationUtils.parseFrameshiftVariant("N105Efs*4")));

        // not equal when new frame does not have a stop codon
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), AlterationUtils.parseFrameshiftVariant("N105fs*?")));
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105fs*?"), AlterationUtils.parseFrameshiftVariant("N105Efs*4")));

        // not equal when extension are different
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), AlterationUtils.parseFrameshiftVariant("N105Efs*5")));
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*5"), AlterationUtils.parseFrameshiftVariant("N105Efs*4")));

        // not equal when reference alleles are different
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), AlterationUtils.parseFrameshiftVariant("A105Efs*4")));
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("A105Efs*4"), AlterationUtils.parseFrameshiftVariant("N105Efs*4")));

        // not equal when protein start are different
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N305Efs*4"), AlterationUtils.parseFrameshiftVariant("N306Efs*4")));
        assertFalse(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N306Efs*4"), AlterationUtils.parseFrameshiftVariant("N305Efs*4")));

        // equal when protein start are the same() -128~127 are cached Integer. We require to use .equal to compare. https://stackoverflow.com/questions/30840071/why-comparison-of-two-integer-using-sometimes-works-and-sometimes-not
        assertTrue(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N305Efs*4"), AlterationUtils.parseFrameshiftVariant("N305Efs*4")));

        // equal when one of reference alleles is missing
        assertTrue(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), AlterationUtils.parseFrameshiftVariant("105Efs*4")));
        assertTrue(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("105Efs*4"), AlterationUtils.parseFrameshiftVariant("N105Efs*4")));

        // equal when one of reference and variant alleles are missing
        assertTrue(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), AlterationUtils.parseFrameshiftVariant("105fs*4")));
        assertTrue(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("105Efs*4"), AlterationUtils.parseFrameshiftVariant("N105fs*4")));

        // equal when one of the variant alleles is missing
        assertTrue(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105Efs*4"), AlterationUtils.parseFrameshiftVariant("N105fs*4")));
        assertTrue(AlterationUtils.isSameFrameshiftVariant(AlterationUtils.parseFrameshiftVariant("N105fs*4"), AlterationUtils.parseFrameshiftVariant("N105Efs*4")));

    }

    public void testComplexDelinsClassifiedAsTruncating() {
        Gene gene = GeneUtils.getGeneByHugoSymbol("NF1");
        Alteration alteration = generateAlteration(gene, "L1340_Q1341delinsF*");
        assertEquals(alteration.getConsequence(), VariantConsequenceUtils.findVariantConsequenceByTerm("stop_gained"));
    }
}
