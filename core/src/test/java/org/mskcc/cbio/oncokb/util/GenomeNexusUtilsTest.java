package org.mskcc.cbio.oncokb.util;

import junit.framework.TestCase;
import org.genome_nexus.client.GenomicLocation;
import org.genome_nexus.client.TranscriptConsequenceSummary;
import org.junit.Assert;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.Gene;
import org.mskcc.cbio.oncokb.model.ReferenceGenome;
import org.mskcc.cbio.oncokb.model.genomeNexus.TranscriptSummaryAlterationResult;

import static org.junit.Assert.assertNotEquals;
import static org.mskcc.cbio.oncokb.Constants.FRAMESHIFT_VARIANT;
import static org.mskcc.cbio.oncokb.Constants.IN_FRAME_DELETION;

/**
 * Created by Hongxin Zhang on 7/20/17.
 */
public class GenomeNexusUtilsTest extends TestCase {
    public void testGetTranscriptConsequence() throws Exception {
        final String BRAF_V600E_37 = "7:g.140453136A>T";
        ReferenceGenome mskReferenceGenome = ReferenceGenome.GRCh37;
        TranscriptSummaryAlterationResult transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, BRAF_V600E_37, mskReferenceGenome);
        TranscriptConsequenceSummary consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        Gene gene = GeneUtils.getGeneByHugoSymbol("BRAF");
        assertEquals("Picked transcript gene symbol is not BRAF, but it should.",
            gene.getHugoSymbol(), consequence.getHugoGeneSymbol());
        assertEquals("Picked transcript hgvs p short is not p.V600E, but it should.",
            "p.V600E", consequence.getHgvspShort());
        assertEquals("Picked transcript protein start is not 600, but it should.",
            "600", Integer.toString(consequence.getProteinPosition().getStart()));
        assertEquals("Picked transcript protein end is not 600, but it should.",
            "600", Integer.toString(consequence.getProteinPosition().getEnd()));
        assertEquals("Picked transcript RefSeq is not the same with MSKIMPACT BRAF RefSeq, but it should.",
            gene.getGrch37RefSeq(), consequence.getRefSeq());
        assertEquals("Picked transcript isoform is not the same with MSKIMPACT BRAF isoform, but it should.",
            gene.getGrch37Isoform(), consequence.getTranscriptId());

        // the same BRAF V600E GRCh37 change should not get annotated by GN in GRCh38
        TranscriptSummaryAlterationResult transcriptSummaryAlterationResult38 = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, BRAF_V600E_37, ReferenceGenome.GRCh38);
        TranscriptConsequenceSummary consequence38 = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        assertNotEquals("The consequence should not be the same", consequence, consequence38);


        final String BRAF_V600E_38 = "7:g.140753336A>T";
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, BRAF_V600E_38, ReferenceGenome.GRCh38);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        gene = GeneUtils.getGeneByHugoSymbol("BRAF");
        assertEquals("Picked transcript gene symbol is not BRAF, but it should.",
            gene.getHugoSymbol(), consequence.getHugoGeneSymbol());
        assertEquals("Picked transcript hgvs p short is not p.V600E, but it should.",
            "p.V600E", consequence.getHgvspShort());
        assertEquals("Picked transcript protein start is not 600, but it should.",
            "600", Integer.toString(consequence.getProteinPosition().getStart()));
        assertEquals("Picked transcript protein end is not 600, but it should.",
            "600", Integer.toString(consequence.getProteinPosition().getEnd()));


        // Isoform of TCF3 should be ENST00000344749, uniport default isoform is ENST00000262965
        transcriptSummaryAlterationResult =  GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, "19:g.1615454C>A", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        gene = GeneUtils.getGeneByHugoSymbol("TCF3");
        assertEquals("Picked transcript isoform is not the same with MSKIMPACT TCF3 isoform, but it should.",
            gene.getGrch37Isoform(), consequence.getTranscriptId());
        assertEquals("Picked transcript isoform is not ENST00000344749, but it should.",
            "ENST00000344749", consequence.getTranscriptId());

        // Test frame shift variant
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, "22:g.41574678_41574679insC", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        assertEquals("Picked transcript protein change is not tH2324Pfs*55, but it should.",
            "p.H2324Pfs*55", consequence.getHgvspShort());
        assertEquals("Picked transcript protein change is not tH2324Pfs*55, but it should.",
            VariantConsequenceUtils.findVariantConsequenceByTerm(FRAMESHIFT_VARIANT), VariantConsequenceUtils.findVariantConsequenceByTerm(consequence.getConsequenceTerms()));

        // BRAF V600E genomic location
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.GENOMIC_LOCATION, "7,140453136,140453136,A,T", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        gene = GeneUtils.getGeneByHugoSymbol("BRAF");
        assertEquals("Picked transcript gene symbol is not BRAF, but it should.",
            gene.getHugoSymbol(), consequence.getHugoGeneSymbol());
        assertEquals("Picked transcript hgvs p short is not p.V600E, but it should.",
            "p.V600E", consequence.getHgvspShort());
        assertEquals("Picked transcript protein start is not 600, but it should.",
            "600", Integer.toString(consequence.getProteinPosition().getStart()));
        assertEquals("Picked transcript protein end is not 600, but it should.",
            "600", Integer.toString(consequence.getProteinPosition().getEnd()));
        assertEquals("Picked transcript RefSeq is not the same with MSKIMPACT BRAF RefSeq, but it should.",
            gene.getGrch37RefSeq(), consequence.getRefSeq());
        assertEquals("Picked transcript isoform is not the same with MSKIMPACT BRAF isoform, but it should.",
            gene.getGrch37Isoform(), consequence.getTranscriptId());


        // Potential multiple consequences
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.GENOMIC_LOCATION, "3,178917478,178917478,G,A", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        gene = GeneUtils.getGeneByHugoSymbol("PIK3CA");
        assertEquals("Picked transcript gene symbol is not PIK3CA, but it should.",
            gene.getHugoSymbol(), consequence.getHugoGeneSymbol());
        assertEquals("Picked transcript hgvs p short is not p.V600E, but it should.",
            "p.G118D", consequence.getHgvspShort());
        assertEquals("Picked transcript protein start is not 118, but it should.",
            "118", Integer.toString(consequence.getProteinPosition().getStart()));
        assertEquals("Picked transcript protein end is not 118, but it should.",
            "118", Integer.toString(consequence.getProteinPosition().getEnd()));
        assertEquals("Picked transcript RefSeq is not the same with MSKIMPACT PIK3CA RefSeq, but it should.",
            gene.getGrch37RefSeq(), consequence.getRefSeq());
        assertEquals("Picked transcript isoform is not the same with MSKIMPACT BRAF isoform, but it should.",
            gene.getGrch37Isoform(), consequence.getTranscriptId());
        assertTrue("We no longer return multiple terms, only return the first term", !consequence.getConsequenceTerms().contains(","));

        // No consequence can be found for the following
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.GENOMIC_LOCATION, "9,136519547,136519547,C,C", ReferenceGenome.GRCh38);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        Assert.assertNull("No consequence should return for this genomic location", consequence);

        // the picked transcript consequence terms might not include the most_severe_consequence
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.GENOMIC_LOCATION, "3, 38182641, 38182641,T,C", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        gene = GeneUtils.getGeneByHugoSymbol("MYD88");
        assertEquals("Picked transcript gene symbol is not MYD88, but it should.",
            gene.getHugoSymbol(), consequence.getHugoGeneSymbol());
        assertEquals("Picked transcript hgvs p short is not p.L265P, but it should.",
            "p.L265P", consequence.getHgvspShort());
        assertEquals("Picked transcript protein start is not 265, but it should.",
            "265", Integer.toString(consequence.getProteinPosition().getStart()));
        assertEquals("Picked transcript protein end is not 265, but it should.",
            "265", Integer.toString(consequence.getProteinPosition().getEnd()));
        assertEquals("Picked transcript RefSeq is not the same with MSKIMPACT MYD88 RefSeq, but it should.",
            gene.getGrch37RefSeq(), consequence.getRefSeq());
        assertEquals("Picked transcript isoform is not the same with MSKIMPACT MYD88 isoform, but it should.",
            gene.getGrch37Isoform(), consequence.getTranscriptId());
        assertTrue("We no longer return multiple terms, only return the first term", !consequence.getConsequenceTerms().contains(","));
        assertEquals("The consequence term is not expected", "missense_variant", consequence.getConsequenceTerms());

        // Test few more cases that reported by user which are supposed to have annotation
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, "4:g.55593600_55593606delinsGTGG", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        assertEquals("Picked transcript gene symbol is not expected, but it should.", "3815", consequence.getEntrezGeneId());
        assertEquals("HGVSp short is not expected, but it should.", "p.Q556_K558delinsVE", consequence.getHgvspShort());
        assertEquals("Consequence is not expected, but it should.", IN_FRAME_DELETION, consequence.getConsequenceTerms());
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, "5:g.67589635_67589639delinsTA", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        assertEquals("Picked transcript gene symbol is not expected, but it should.", "5295", consequence.getEntrezGeneId());
        assertEquals("HGVSp short is not expected, but it should.", "p.L466_E468delinsFK", consequence.getHgvspShort());
        assertEquals("Consequence is not expected, but it should.", IN_FRAME_DELETION, consequence.getConsequenceTerms());

        //TERT promoter
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.GENOMIC_LOCATION, "5,1295167,1295168,TC,AATG", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        assertEquals("Picked transcript gene symbol is not expected, but it should.", "TERT", consequence.getHugoGeneSymbol());
        assertEquals("Consequence is not expected, but it should.", "upstream_gene_variant", consequence.getConsequenceTerms());
        // location within TERT gene
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, "5:g.1295167_1295168delinsAATG", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        assertEquals("Picked transcript gene symbol is not expected, but it should.", "TERT", consequence.getHugoGeneSymbol());
        assertEquals("Consequence is not expected, but it should.", "upstream_gene_variant", consequence.getConsequenceTerms());
        // location out of TERT gene on regulatory sequence
        transcriptSummaryAlterationResult = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, "5:g.1295228G>A", mskReferenceGenome);
        consequence = transcriptSummaryAlterationResult.getTranscriptConsequenceSummary();
        assertEquals("Picked transcript gene symbol is not expected, but it should.", "TERT", consequence.getHugoGeneSymbol());
        assertEquals("Consequence is not expected, but it should.", "upstream_gene_variant", consequence.getConsequenceTerms());
    }

    public void testChromosomeNormalizer() {
        assertNull(GenomeNexusUtils.chromosomeNormalizer(null));
        assertEquals("", GenomeNexusUtils.chromosomeNormalizer(""));
        assertEquals("", GenomeNexusUtils.chromosomeNormalizer(" "));
        assertEquals("1", GenomeNexusUtils.chromosomeNormalizer("chr1"));
        assertEquals("X", GenomeNexusUtils.chromosomeNormalizer("23"));
        assertEquals("X", GenomeNexusUtils.chromosomeNormalizer("X"));
        assertEquals("Y", GenomeNexusUtils.chromosomeNormalizer("24"));
        assertEquals("Y", GenomeNexusUtils.chromosomeNormalizer("Y"));
    }

    public void testConvertGenomicLocationToObject() {
        convertGenomicLocationTestSuit("7,140453136,140453136,A,T", "7", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("7 ,140453136,140453136,A,T", "7", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("7 , 140453136,140453136,A,T", "7", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("7 , 140453136, 140453136,A,T", "7", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("7 , 140453136, 140453136, A,T", "7", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("7 , 140453136, 140453136, A, T", "7", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("7 , 140453136, A, A, T", "7", 140453136, null, "A", "T");

        // test some edge cases
        assertNull(GenomeNexusUtils.convertGenomicLocation(""));
        assertNull(GenomeNexusUtils.convertGenomicLocation(" "));
        assertNull(GenomeNexusUtils.convertGenomicLocation("7,"));
        assertNull(GenomeNexusUtils.convertGenomicLocation("7,140453136"));
        assertNull(GenomeNexusUtils.convertGenomicLocation("7,140453136,140453136"));
        assertNull(GenomeNexusUtils.convertGenomicLocation("7,140453136,140453136,A"));

        // 23/24 chr will be converted to X/Y
        convertGenomicLocationTestSuit("23,140453136,140453136,A,T", "X", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("X,140453136,140453136,A,T", "X", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("24,140453136,140453136,A,T", "Y", 140453136, 140453136, "A", "T");
        convertGenomicLocationTestSuit("Y,140453136,140453136,A,T", "Y", 140453136, 140453136, "A", "T");
    }

    private void convertGenomicLocationTestSuit(String genomicLocation, String expectedChr, Integer expectedStart, Integer expectedEnd, String expectedReferenceAllele, String expectedVariantAllele) {
        GenomicLocation gl = GenomeNexusUtils.convertGenomicLocation(genomicLocation);
        assertNotNull(gl);
        assertEquals(gl.getChromosome(), expectedChr);
        assertEquals(gl.getStart(), expectedStart);
        assertEquals(gl.getEnd(), expectedEnd);
        assertEquals(gl.getReferenceAllele(), expectedReferenceAllele);
        assertEquals(gl.getVariantAllele(), expectedVariantAllele);
    }

    public void testConvertGenomicLocationToString() {
        GenomicLocation gl = new GenomicLocation();
        gl.setChromosome("1");
        gl.setStart(140453136);
        gl.setEnd(140453136);
        gl.setReferenceAllele("A");
        gl.setVariantAllele("T");
        assertEquals("1,140453136,140453136,A,T", GenomeNexusUtils.convertGenomicLocation(gl));

        gl.setChromosome("1");
        gl.setStart(null);
        gl.setEnd(140453136);
        gl.setReferenceAllele("A");
        gl.setVariantAllele("T");
        assertEquals("1,,140453136,A,T", GenomeNexusUtils.convertGenomicLocation(gl));

        gl.setChromosome("1");
        gl.setStart(null);
        gl.setEnd(140453136);
        gl.setReferenceAllele("A");
        gl.setVariantAllele(null);
        assertEquals("1,,140453136,A,", GenomeNexusUtils.convertGenomicLocation(gl));

        gl = null;
        assertEquals("", GenomeNexusUtils.convertGenomicLocation(gl));
    }

}
