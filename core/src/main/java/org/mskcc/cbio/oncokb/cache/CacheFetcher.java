package org.mskcc.cbio.oncokb.cache;

import org.apache.commons.lang3.StringUtils;
import org.cbioportal.genome_nexus.component.annotation.NotationConverter;
import org.cbioportal.genome_nexus.model.GenomicLocation;
import org.cbioportal.genome_nexus.util.exception.InvalidHgvsException;
import org.cbioportal.genome_nexus.util.exception.TypeNotSupportedException;
import org.mskcc.cbio.oncokb.apiModels.CuratedGene;
import org.mskcc.cbio.oncokb.bo.OncokbTranscriptService;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.genomeNexusPreAnnotations.GenomeNexusAnnotatedVariantInfo;
import org.mskcc.cbio.oncokb.util.*;
import org.oncokb.oncokb_transcript.ApiException;
import org.oncokb.oncokb_transcript.client.EnsemblGene;
import org.oncokb.oncokb_transcript.client.TranscriptDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.Constants.DEFAULT_REFERENCE_GENOME;
import static org.mskcc.cbio.oncokb.util.MainUtils.rangesIntersect;
import static org.mskcc.cbio.oncokb.cache.Constants.REDIS_KEY_SEPARATOR;

@Component
public class CacheFetcher {
    private static Map<ReferenceGenome, Map<String, Set<EnsemblGene>>> canonicalEnsemblGenesByChromosomeByReferenceGenome = new HashMap<ReferenceGenome, Map<String, Set<EnsemblGene>>>() {{
        put(ReferenceGenome.GRCh37, new HashMap<>());
        put(ReferenceGenome.GRCh38, new HashMap<>());
    }};

    OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();
    NotationConverter notationConverter = new NotationConverter();

    @Autowired(required = false)
    CacheManager cacheManager;

    @Cacheable(cacheResolver = "generalCacheResolver", key = "'all'")
    public OncoKBInfo getOncoKBInfo() {
        return new OncoKBInfo();
    }

    @Cacheable(cacheResolver = "generalCacheResolver", key = "'all'")
    public List<CancerGene> getCancerGenes() throws ApiException, IOException {
        return getCancerGeneList();
    }

    @Cacheable(cacheResolver = "generalCacheResolver", key = "'all'")
    public String getCancerGenesTxt() throws ApiException, IOException {

        String separator = "\t";
        String newLine = "\n";
        StringBuilder sb = new StringBuilder();
        List<String> header = new ArrayList<>();
        header.add("Hugo Symbol");
        header.add("Entrez Gene ID");
        header.add("GRCh37 Isoform");
        header.add("GRCh37 RefSeq");
        header.add("GRCh38 Isoform");
        header.add("GRCh38 RefSeq");
        header.add("Is Oncogene");
        header.add("Is Tumor Suppressor Gene");
        header.add("# of occurrence within resources (Column J-P)");
        header.add("OncoKB Annotated");
        header.add("MSK-IMPACT");
        header.add("MSK-HEME");
        header.add("FOUNDATION ONE");
        header.add("FOUNDATION ONE HEME");
        header.add("Vogelstein");
        header.add("COSMIC CGC (v99)");
        header.add("Gene Aliases");
        sb.append(MainUtils.listToString(header, separator));
        sb.append(newLine);

        for (CancerGene cancerGene : getCancerGeneList()) {
            List<String> row = new ArrayList<>();
            row.add(cancerGene.getHugoSymbol());
            row.add(cancerGene.getEntrezGeneId().toString());
            row.add(cancerGene.getGrch37Isoform());
            row.add(cancerGene.getGrch37RefSeq());
            row.add(cancerGene.getGrch38Isoform());
            row.add(cancerGene.getGrch38RefSeq());
            row.add(getStringByBoolean(cancerGene.getOncogene()));
            row.add(getStringByBoolean(cancerGene.getTSG()));
            row.add(String.valueOf(cancerGene.getOccurrenceCount()));
            row.add(getStringByBoolean(cancerGene.getOncokbAnnotated()));
            row.add(getStringByBoolean(cancerGene.getmSKImpact()));
            row.add(getStringByBoolean(cancerGene.getmSKHeme()));
            row.add(getStringByBoolean(cancerGene.getFoundation()));
            row.add(getStringByBoolean(cancerGene.getFoundationHeme()));
            row.add(getStringByBoolean(cancerGene.getVogelstein()));
            row.add(getStringByBoolean(cancerGene.getSangerCGC()));
            row.add(cancerGene.getGeneAliases().stream().sorted().collect(Collectors.joining(", ")));
            sb.append(MainUtils.listToString(row, separator));
            sb.append(newLine);
        }
        return sb.toString();
    }

    @Cacheable(cacheResolver = "generalCacheResolver", key = "'all'")
    public Set<org.oncokb.oncokb_transcript.client.Gene> getAllTranscriptGenes() throws ApiException {
        return oncokbTranscriptService.findTranscriptGenesBySymbols(CacheUtils.getAllGenes().stream().filter(gene -> gene.getEntrezGeneId() > 0).map(gene -> gene.getEntrezGeneId().toString()).collect(Collectors.toList()));
    }

    public Map<String, Set<EnsemblGene>> getCanonicalEnsemblGenesByChromosome(ReferenceGenome referenceGenome) throws ApiException {
        // Use cached data if available
        Map<String, Set<EnsemblGene>> chromosomeEnsemblGenesMap = canonicalEnsemblGenesByChromosomeByReferenceGenome.get(referenceGenome);
        if(!chromosomeEnsemblGenesMap.isEmpty()) {
            return chromosomeEnsemblGenesMap;
        }
        // Reach out to transcript service
        Set<org.oncokb.oncokb_transcript.client.Gene> allTranscriptGenes = getAllTranscriptGenes();
        for (org.oncokb.oncokb_transcript.client.Gene gene : allTranscriptGenes) {
            for (EnsemblGene ensemblGene : gene.getEnsemblGenes()) {
                if (!ensemblGene.getCanonical() || !ensemblGene.getReferenceGenome().equals(referenceGenome.name())) {
                    continue;
                }
                String chromosome = ensemblGene.getChromosome();
                chromosomeEnsemblGenesMap.putIfAbsent(chromosome, new HashSet<>());
                chromosomeEnsemblGenesMap.get(chromosome).add(ensemblGene);
            }
        }
        canonicalEnsemblGenesByChromosomeByReferenceGenome.put(referenceGenome, chromosomeEnsemblGenesMap);
        return chromosomeEnsemblGenesMap;
    }

    private List<CancerGene> getCancerGeneList() throws ApiException, IOException {
        List<CancerGene> cancerGenes = CancerGeneUtils.getCancerGeneList();
        List<String> hugos = cancerGenes.stream().map(CancerGene::getHugoSymbol).collect(Collectors.toList());
        List<Gene> genes = new ArrayList<>();
        genes = oncokbTranscriptService.findGenesBySymbols(hugos);
        for (CancerGene cancerGene : cancerGenes) {
            if (cancerGene.getGeneAliases().size() == 0) {
                List<Gene> matched = genes.stream().filter(gene -> gene.getEntrezGeneId().equals(cancerGene.getEntrezGeneId())).collect(Collectors.toList());
                if (matched.size() > 0) {
                    Set<String> geneAlias = new HashSet<>();
                    Gene gene = matched.iterator().next();
                    geneAlias.addAll(gene.getGeneAliases());
                    geneAlias.add(gene.getHugoSymbol());
                    geneAlias.remove(cancerGene.getHugoSymbol());
                    cancerGene.setGeneAliases(geneAlias);
                }
            }
        }
        return cancerGenes;
    }

    @Cacheable(cacheResolver = "generalCacheResolver")
    public List<CuratedGene> getCuratedGenes(boolean includeEvidence) {
        List<CuratedGene> genes = new ArrayList<>();
        for (Gene gene : CacheUtils.getAllGenes()) {
            // Skip all genes without entrez gene id
            if (gene.getEntrezGeneId() == null) {
                continue;
            }

            String highestSensitiveLevel = "";
            String highestResistanceLevel = "";
            Set<Evidence> therapeuticEvidences = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, EvidenceTypeUtils.getTreatmentEvidenceTypes());
            Set<Evidence> highestSensitiveLevelEvidences = EvidenceUtils.getOnlyHighestLevelEvidences(EvidenceUtils.getSensitiveEvidences(therapeuticEvidences), null, null);
            Set<Evidence> highestResistanceLevelEvidences = EvidenceUtils.getOnlyHighestLevelEvidences(EvidenceUtils.getResistanceEvidences(therapeuticEvidences), null, null);
            if (!highestSensitiveLevelEvidences.isEmpty()) {
                highestSensitiveLevel = highestSensitiveLevelEvidences.iterator().next().getLevelOfEvidence().getLevel();
            }
            if (!highestResistanceLevelEvidences.isEmpty()) {
                highestResistanceLevel = highestResistanceLevelEvidences.iterator().next().getLevelOfEvidence().getLevel();
            }

            genes.add(
                new CuratedGene(
                    gene.getGrch37Isoform(), gene.getGrch37RefSeq(),
                    gene.getGrch38Isoform(), gene.getGrch38RefSeq(),
                    gene.getEntrezGeneId(), gene.getHugoSymbol(),
                    gene.getTSG(), gene.getOncogene(),
                    highestSensitiveLevel, highestResistanceLevel,
                    includeEvidence ? SummaryUtils.geneSummary(gene, gene.getHugoSymbol(), false) : "",
                    includeEvidence ? SummaryUtils.geneBackground(gene, gene.getHugoSymbol()) : ""
                )
            );
        }
        MainUtils.sortCuratedGenes(genes);
        return genes;
    }

    @Cacheable(cacheResolver = "generalCacheResolver")
    public String getCuratedGenesTxt(boolean includeEvidence) {
        String separator = "\t";
        String newLine = "\n";
        StringBuilder sb = new StringBuilder();
        List<String> header = new ArrayList<>();
        header.add("GRCh37 Isoform");
        header.add("GRCh37 RefSeq");
        header.add("GRCh38 Isoform");
        header.add("GRCh38 RefSeq");
        header.add("Entrez Gene ID");
        header.add("Hugo Symbol");
        header.add("Is Oncogene");
        header.add("Is Tumor Suppressor Gene");
        header.add("Highest Level of Evidence(sensitivity)");
        header.add("Highest Level of Evidence(resistance)");
        if (includeEvidence == Boolean.TRUE) {
            header.add("Summary");
            header.add("Background");
        }
        sb.append(MainUtils.listToString(header, separator));
        sb.append(newLine);

        List<CuratedGene> genes = this.getCuratedGenes(includeEvidence == Boolean.TRUE);
        for (CuratedGene gene : genes) {
            List<String> row = new ArrayList<>();
            row.add(gene.getGrch37Isoform());
            row.add(gene.getGrch37RefSeq());
            row.add(gene.getGrch38Isoform());
            row.add(gene.getGrch38RefSeq());
            row.add(String.valueOf(gene.getEntrezGeneId()));
            row.add(gene.getHugoSymbol());
            row.add(getStringByBoolean(gene.getOncogene()));
            row.add(getStringByBoolean(gene.getTSG()));
            row.add(gene.getHighestSensitiveLevel());
            row.add(gene.getHighestResistancLevel());
            if (includeEvidence == Boolean.TRUE) {
                row.add(gene.getSummary());
                row.add(gene.getBackground());
            }
            sb.append(MainUtils.listToString(row, separator));
            sb.append(newLine);
        }
        return sb.toString();
    }

    private String getStringByBoolean(Boolean val) {
        return val ? "Yes" : "No";
    }

    @Cacheable(cacheResolver = "generalCacheResolver")
    public Gene findGeneBySymbol(String symbol) throws ApiException {
        return this.oncokbTranscriptService.findGeneBySymbol(symbol);
    }

    @Cacheable(
        cacheResolver = "generalCacheResolver",
        keyGenerator = "concatKeyGenerator"
    )
    public IndicatorQueryResp processQuery(ReferenceGenome referenceGenome,
                                           Integer entrezGeneId,
                                           String hugoSymbol,
                                           String alteration,
                                           String alterationType,
                                           String tumorType,
                                           String consequence,
                                           Integer proteinStart,
                                           Integer proteinEnd,
                                           StructuralVariantType svType,
                                           String hgvs,
                                           Boolean isGermline,
                                           String alleleState,
                                           Pathogenicity pathogenicity,
                                           Set<LevelOfEvidence> levels,
                                           Boolean highestLevelOnly,
                                           Set<EvidenceType> evidenceTypes,
                                           Boolean geneQueryOnly) {
        if (referenceGenome == null) {
            referenceGenome = DEFAULT_REFERENCE_GENOME;
        }
        Query query = new Query(null, referenceGenome, entrezGeneId, hugoSymbol, alteration, alterationType, svType, tumorType, consequence, proteinStart, proteinEnd, hgvs, isGermline, alleleState, pathogenicity);
        return IndicatorUtils.processQuery(
            query, levels, highestLevelOnly,
            evidenceTypes, geneQueryOnly
        );
    }

    @Cacheable(cacheResolver = "generalCacheResolver",
        keyGenerator = "concatKeyGenerator")
    public Alteration getAlterationFromGenomeNexus(GNVariantAnnotationType gnVariantAnnotationType, ReferenceGenome referenceGenome, String genomicLocation) throws org.genome_nexus.ApiException {
        return AlterationUtils.getAlterationFromGenomeNexus(gnVariantAnnotationType, referenceGenome, genomicLocation);
    }

    public void cacheAlterationFromGenomeNexus(GenomeNexusAnnotatedVariantInfo gnAnnotatedVariantInfo) throws IllegalStateException {
        if (cacheManager == null) {
            throw new IllegalStateException("Cannot cache pre-annotated GN variants. Change property redis.enable to True.");
        }

        // Build the Alteration object from the pre-annotated GN variant object.
        Alteration alteration = new Alteration();

        // Sometimes the VEP does not return the entrezGeneId and only returns the hugoSymbol.
        if (StringUtils.isNotEmpty(gnAnnotatedVariantInfo.getHugoSymbol()) || gnAnnotatedVariantInfo.getEntrezGeneId() != null) {
            Gene gene = GeneUtils.getGene(gnAnnotatedVariantInfo.getEntrezGeneId(), gnAnnotatedVariantInfo.getHugoSymbol());
            if (gene == null) {
                gene = new Gene();
                gene.setHugoSymbol(gnAnnotatedVariantInfo.getHugoSymbol());
                gene.setEntrezGeneId(gnAnnotatedVariantInfo.getEntrezGeneId());
            }
            alteration.setGene(gene);
        }

        alteration.setAlteration(gnAnnotatedVariantInfo.getHgvspShort());
        alteration.setProteinStart(gnAnnotatedVariantInfo.getProteinStart());
        alteration.setProteinEnd(gnAnnotatedVariantInfo.getProteinEnd());
        alteration.setConsequence(VariantConsequenceUtils.findVariantConsequenceByTerm(gnAnnotatedVariantInfo.getConsequenceTerms()));

        String hgvsg = gnAnnotatedVariantInfo.getHgvsg();
        String genomicLocation = gnAnnotatedVariantInfo.getGenomicLocation();
        ReferenceGenome referenceGenome = gnAnnotatedVariantInfo.getReferenceGenome();

        // Store pre-annotated alteration into Redis cache
        Cache cache = cacheManager.getCache(CacheCategory.GENERAL.getKey() + REDIS_KEY_SEPARATOR + "getAlterationFromGenomeNexus");
        if (StringUtils.isNotEmpty(hgvsg)) {
            String cacheKey = String.join(REDIS_KEY_SEPARATOR, new String[]{GNVariantAnnotationType.HGVS_G.name(), referenceGenome.name(), hgvsg});
            cache.put(cacheKey, alteration);
        }

        if (StringUtils.isNotEmpty(genomicLocation)) {
            String cacheKey = String.join(REDIS_KEY_SEPARATOR, new String[]{GNVariantAnnotationType.GENOMIC_LOCATION.name(), referenceGenome.name(), genomicLocation});
            cache.put(cacheKey, alteration);
        }

    }

    @Cacheable(cacheResolver = "generalCacheResolver",
        keyGenerator = "concatKeyGenerator")
    public List<TranscriptDTO> getAllGeneEnsemblTranscript(ReferenceGenome referenceGenome) throws ApiException {
        List<String> ids = CacheUtils.getAllGenes().stream().map(gene -> Optional.ofNullable(referenceGenome.equals(ReferenceGenome.GRCh37.name()) ? gene.getGrch37Isoform() : gene.getGrch38Isoform()).orElse("")).filter(id -> StringUtils.isNotEmpty(id)).collect(Collectors.toList());
        return oncokbTranscriptService.findEnsemblTranscriptsByIds(ids, referenceGenome);
    }

    public boolean genomicLocationShouldBeAnnotated(GNVariantAnnotationType gnVariantAnnotationType, String query, ReferenceGenome referenceGenome) throws ApiException {
        if (StringUtils.isEmpty(query)) {
            return false;
        }else{
            query = query.trim();
        }
        if (GNVariantAnnotationType.HGVS_G.equals(gnVariantAnnotationType)) {
            if (!AlterationUtils.isValidHgvsg(query)) {
                return false;
            }
        }
        Map<String, Set<EnsemblGene>> chromosomeCanonicalEnsemblGeneMap = getCanonicalEnsemblGenesByChromosome(referenceGenome);
        // when the transcript info is not available, we should always annotate the genomic location
        if (chromosomeCanonicalEnsemblGeneMap == null || chromosomeCanonicalEnsemblGeneMap.isEmpty()) {
            return true;
        }
        GenomicLocation gl = null;
        try {
            if (gnVariantAnnotationType.equals(GNVariantAnnotationType.GENOMIC_LOCATION)) {
                gl = notationConverter.parseGenomicLocation(query);
            } else if (gnVariantAnnotationType.equals(GNVariantAnnotationType.HGVS_G)) {
                query = notationConverter.hgvsNormalizer(query);
                // We are only doing a partial HGVSg -> Genomic Location conversion.
                // The withinBuffer method only requires the chromosome and range, so we do
                // not need to parse the ref/var residues. This is slightly more performanant than using regex.
                gl = MainUtils.parseChromosomeAndRangeFromHGVSg(query);
            }
            if (gl == null) {
                return false;
            }
        } catch (InvalidHgvsException | TypeNotSupportedException e) {
            // If GN throws InvalidHgvsException, we still need to check whether it's a duplication. The GN does not support dup in HGVSg format but it can still be annotated by VEP.
            if (query.endsWith("dup")) {
                return true;
            } else {
                return false;
            }
        }
        GenomicLocation finalGl = gl;

        if (chromosomeCanonicalEnsemblGeneMap.containsKey(finalGl.getChromosome())){
            return chromosomeCanonicalEnsemblGeneMap.get(finalGl.getChromosome()).stream().anyMatch(ensemblGene -> withinBuffer(ensemblGene, finalGl));
        }

        return false;
    }

    private Boolean withinBuffer(EnsemblGene ensemblGene, GenomicLocation genomicLocation) {
        int bpBuffer = 10000; // add some buffer on determine which genomic change should be annotated. We use the gene range from oncokb-transcript but that does not include gene regulatory sequence. Before having proper range, we use a buffer range instead.
        return rangesIntersect(ensemblGene.getStart() > bpBuffer ? (ensemblGene.getStart() - bpBuffer) : 0, ensemblGene.getEnd() + bpBuffer, genomicLocation.getStart(), genomicLocation.getEnd());
    }
}
