package org.mskcc.cbio.oncokb.api.pvt;

import com.mysql.jdbc.StringUtils;
import io.swagger.annotations.ApiParam;
import org.genome_nexus.client.GenomicLocation;
import org.genome_nexus.client.TranscriptConsequenceSummary;
import org.mskcc.cbio.oncokb.apiModels.*;
import org.mskcc.cbio.oncokb.apiModels.annotation.AnnotateMutationByGenomicChangeQuery;
import org.mskcc.cbio.oncokb.apiModels.annotation.AnnotateMutationByHGVSQuery;
import org.mskcc.cbio.oncokb.apiModels.download.DownloadAvailability;
import org.mskcc.cbio.oncokb.apiModels.download.FileExtension;
import org.mskcc.cbio.oncokb.apiModels.download.FileName;
import org.mskcc.cbio.oncokb.apiModels.ensembl.EnsemblGene;
import org.mskcc.cbio.oncokb.bo.AlterationBo;
import org.mskcc.cbio.oncokb.bo.PortalAlterationBo;
import org.mskcc.cbio.oncokb.cache.CacheFetcher;
import org.mskcc.cbio.oncokb.controller.advice.ApiHttpErrorException;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.TumorType;
import org.mskcc.cbio.oncokb.bo.OncokbTranscriptService;
import org.mskcc.cbio.oncokb.util.*;
import org.oncokb.oncokb_transcript.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.util.GitHubUtils.getOncoKBSqlDumpFileName;
import static org.mskcc.cbio.oncokb.util.GitHubUtils.getOncoKBTranscriptSqlDumpFileName;
import static org.mskcc.cbio.oncokb.util.HttpUtils.getDataDownloadResponseEntity;

/**
 * Created by Hongxin on 10/28/16.
 */
@Controller
public class PrivateUtilsApiController implements PrivateUtilsApi {
    @Autowired
    CacheFetcher cacheFetcher;

    @Override
    public ResponseEntity<List<String>> utilsSuggestedVariantsGet() {
        HttpStatus status = HttpStatus.OK;

        List<String> variants = new ArrayList<>(AlterationUtils.getGeneralVariants());

        return new ResponseEntity<>(variants, status);
    }

    @Override
    public ResponseEntity<Boolean> utilsHotspotMutationGet(
        @ApiParam(value = "Gene hugo symbol") @RequestParam(value = "hugoSymbol") String hugoSymbol
        , @ApiParam(value = "Variant name") @RequestParam(value = "variant") String variant
    ) {
        HttpStatus status = HttpStatus.OK;

        Boolean isHotspot = false;

        Alteration alteration = AlterationUtils.getAlteration(hugoSymbol, variant, null, null, null, null, null);

        if (alteration != null) {
            isHotspot = HotspotUtils.isHotspot(alteration);
        }

        return new ResponseEntity<>(isHotspot, status);
    }

    @Override
    public ResponseEntity<GeneNumber> utilsNumbersGeneGet(
        @ApiParam(value = "The gene symbol used in Human Genome Organisation.", required = true) @PathVariable("hugoSymbol") String hugoSymbol
    ) {
        HttpStatus status = HttpStatus.OK;
        Set<GeneNumber> geneNumbers = NumberUtils.getGeneNumberListWithLevels(Collections.singleton(GeneUtils.getGeneByHugoSymbol(hugoSymbol)), LevelUtils.getPublicLevels());
        GeneNumber geneNumber = null;

        if (geneNumbers.size() == 1) {
            geneNumber = geneNumbers.iterator().next();
        } else {
            status = HttpStatus.NO_CONTENT;
        }

        return new ResponseEntity<>(geneNumber, status);
    }

    @Override
    public ResponseEntity<Set<GeneNumber>> utilsNumbersGenesGet() {

        Set<GeneNumber> genes = new HashSet<>();

        if (CacheUtils.getNumbers("genes") == null) {
            genes = NumberUtils.getAllGeneNumberListByLevels(LevelUtils.getPublicLevels());
            CacheUtils.setNumbers("genes", genes);
        } else {
            genes = (Set<GeneNumber>) CacheUtils.getNumbers("genes");
        }
        return new ResponseEntity<>(genes, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<MainNumber> utilsNumbersMainGet() {
        MainNumber mainNumber = new MainNumber();


        if (CacheUtils.getNumbers("main") == null) {
            Set<Gene> allGenes = CacheUtils.getAllGenes();
            Integer numRealGenes = 0;
            for (Gene gene : allGenes) {
                if (gene.getEntrezGeneId() > 0)
                    numRealGenes++;
            }
            mainNumber.setGene(numRealGenes);

            List<Alteration> alterations = new ArrayList<>(AlterationUtils.getAllAlterations());
            alterations = AlterationUtils.excludeVUS(alterations);
            alterations = AlterationUtils.excludeInferredAlterations(alterations);

            mainNumber.setAlteration(alterations.size());
            Set<Evidence> evidences = CacheUtils.getAllEvidences();
            Set<TumorType> treatmentTumorTypes = new HashSet<>();
            for (Evidence evidence : evidences) {
                if (evidence.getLevelOfEvidence() != null && !evidence.getCancerTypes().isEmpty()) {
                    treatmentTumorTypes.addAll(evidence.getCancerTypes());
                }
            }
            mainNumber.setTumorType(treatmentTumorTypes.size());
            mainNumber.setDrug(NumberUtils.getDrugsCountByLevels(LevelUtils.getPublicLevels()));
            CacheUtils.setNumbers("main", mainNumber);
        } else {
            mainNumber = (MainNumber) CacheUtils.getNumbers("main");
        }

        return new ResponseEntity<>(mainNumber, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Set<LevelNumber>> utilsNumbersLevelsGet() {
        Set<LevelNumber> genes = new HashSet<>();


        if (CacheUtils.getNumbers("levels") == null) {
            genes = NumberUtils.getLevelNumberListByLevels(LevelUtils.getPublicLevels());
            CacheUtils.setNumbers("levels", genes);
        } else {
            genes = (Set<LevelNumber>) CacheUtils.getNumbers("levels");
        }

        return new ResponseEntity<>(genes, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Map<String, Boolean>> validateTrials(@ApiParam(value = "NCTID list") @RequestParam(value = "nctIds") List<String> nctIds) throws ParserConfigurationException, SAXException, IOException {
        return new ResponseEntity<>(MainUtils.validateTrials(nctIds), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<MatchVariantResult>> validateVariantExamplePost(@ApiParam(value = "List of queries. Please see swagger.json for request body format.", required = true) @RequestBody(required = true) MatchVariantRequest body) {
        List<MatchVariantResult> results = new ArrayList<>();

        for (Query query : body.getQueries()) {
            MatchVariantResult matchVariantResult = new MatchVariantResult();
            matchVariantResult.setQuery(query);
            Set<MatchVariant> match = new HashSet<>();

            for (MatchVariant matchVariantRequestVariant : body.getOncokbVariants()) {
                if (query.getHugoSymbol().equals(matchVariantRequestVariant.getHugoSymbol())) {
                    boolean isMatch = matchVariant(query.getHugoSymbol(), query.getReferenceGenome(), matchVariantRequestVariant.getAlteration(), query.getAlteration());
                    if (isMatch) {
                        match.add(matchVariantRequestVariant);
                    }
                }
            }
            matchVariantResult.setResult(match);
            results.add(matchVariantResult);
        }
        return new ResponseEntity<>(results, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<TumorType>> utilsTumorTypesGet() {
        return new ResponseEntity<>(ApplicationContextSingleton.getTumorTypeBo().getAllTumorTypes(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Map<String, Boolean>> validateVariantExampleGet(String hugoSymbol, String referenceGenome, String variant, String examples) throws ParserConfigurationException, SAXException, IOException {
        Map<String, Boolean> validation = new HashMap<>();
        ReferenceGenome matchedRG = null;
        if (!org.apache.commons.lang3.StringUtils.isEmpty(referenceGenome)) {
            matchedRG = MainUtils.searchEnum(ReferenceGenome.class, referenceGenome);
            if (matchedRG == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
        for (String example : examples.split(",")) {
            validation.put(example, matchVariant(hugoSymbol, matchedRG, variant, example));
        }
        return new ResponseEntity<>(validation, HttpStatus.OK);
    }

    private boolean matchVariant(String hugoSymbol, ReferenceGenome referenceGenome, String variant, String example) {
        Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
        boolean isMatched = false;
        if (gene != null) {
            AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
            boolean isGeneralAlteration = AlterationUtils.isGeneralAlterations(variant);

            Alteration oncokbVariant = new Alteration();
            oncokbVariant.setGene(gene);
            oncokbVariant.setAlteration(variant);
            oncokbVariant.setName(variant);
            // Annotate OncoKB variant
            AlterationUtils.annotateAlteration(oncokbVariant, variant);

            example = example.trim();
            Alteration exampleVariant = new Alteration();
            exampleVariant.setGene(gene);
            exampleVariant.setAlteration(example);
            exampleVariant.setName(example);
            AlterationUtils.annotateAlteration(exampleVariant, example);

            LinkedHashSet<Alteration> relevantAlterations = new LinkedHashSet<>();
            if (isGeneralAlteration) {
                List<Alteration> allAlterations = AlterationUtils.getAllAlterations(referenceGenome, gene);

                // If the general alteration is not annotated system, at least we need to add
                // it into the list for mapping.
                Alteration exactMatch = AlterationUtils.findAlteration(gene, referenceGenome, variant);
                if (exactMatch == null) {
                    allAlterations.add(oncokbVariant);
                }
                relevantAlterations = alterationBo.findRelevantAlterations(referenceGenome, exampleVariant, allAlterations, true);
                for (Alteration alteration : relevantAlterations) {
                    if (alteration.getAlteration().toLowerCase().equals(variant.toLowerCase())) {
                        isMatched = true;
                        break;
                    }
                }
            } else {
                relevantAlterations = alterationBo.findRelevantAlterations(referenceGenome, exampleVariant, Collections.singletonList(oncokbVariant), false);


                // We should not do alternative allele rule in here
                List<Alteration> alternativeAlleles = AlterationUtils.getAlleleAlterations(referenceGenome, exampleVariant, Collections.singletonList(oncokbVariant));
                relevantAlterations.removeAll(alternativeAlleles);

                isMatched = relevantAlterations.size() > 0;
            }
        }
        return isMatched;
    }

    @Override
    public ResponseEntity<Map<LevelOfEvidence, Set<Evidence>>> utilsEvidencesByLevelsGet() {
        return new ResponseEntity<>(getEvidencesByLevels(), HttpStatus.OK);
    }

    private Map<LevelOfEvidence, Set<Evidence>> getEvidencesByLevels() {
        Map<Gene, Set<Evidence>> evidences = EvidenceUtils.getAllGeneBasedEvidences();

        Map<LevelOfEvidence, Set<Evidence>> result = new HashMap<>();

        for (Map.Entry<Gene, Set<Evidence>> entry : evidences.entrySet()) {
            for (Evidence evidence : entry.getValue()) {
                LevelOfEvidence level = evidence.getLevelOfEvidence();
                if (level != null && LevelUtils.getPublicLevels().contains(level)) {
                    if (!result.containsKey(level)) {
                        result.put(level, new HashSet<Evidence>());
                    }
                    if (evidence.getGene().getHugoSymbol().equals("ESR1")) {
                        evidence = MainUtils.convertSpecialESR1Evidence(evidence);
                    }
                    Evidence updatedEvidence = new Evidence(evidence, null);
                    if (updatedEvidence.getRelevantCancerTypes() == null || updatedEvidence.getRelevantCancerTypes().size() == 0) {
                        updatedEvidence.setRelevantCancerTypes(TumorTypeUtils.findEvidenceRelevantCancerTypes(updatedEvidence));
                    }
                    result.get(level).add(updatedEvidence);
                }
            }
        }
        return result;
    }

    @Override
    public ResponseEntity<List<TumorType>> utilRelevantTumorTypesGet(
        @ApiParam(value = "OncoTree tumor type name/main type/code") @RequestParam(value = "tumorType") String tumorType
    ) {
        return new ResponseEntity<>(TumorTypeUtils.findRelevantTumorTypes(tumorType), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<TumorType>> utilRelevantCancerTypesPost(
        @ApiParam(value = "Level of Evidence") @RequestParam(value = "levelOfEvidence", required = false) LevelOfEvidence levelOfEvidence,
        @ApiParam(value = "List of queries.", required = true) @RequestBody List<RelevantCancerTypeQuery> body
    ) {
        boolean isLevelBased = levelOfEvidence != null;
        RelevantTumorTypeDirection direction = levelOfEvidence != null && levelOfEvidence.equals(LevelOfEvidence.LEVEL_Dx1) ? RelevantTumorTypeDirection.UPWARD : RelevantTumorTypeDirection.DOWNWARD;
        Set<TumorType> tumorTypes = body.stream()
            .map(relevantCancerTypeQuery -> {
                if (StringUtils.isNullOrEmpty(relevantCancerTypeQuery.getCode())) {
                    if (isLevelBased) {
                        List<TumorType> queries = ApplicationContextSingleton.getTumorTypeBo().getAllTumorTypes();
                        if (direction.equals(RelevantTumorTypeDirection.UPWARD)) {
                            queries = TumorTypeUtils.findRelevantTumorTypes(relevantCancerTypeQuery.getMainType(), true, direction);
                        } else {
                            queries = queries.stream().filter(tumorType -> !StringUtils.isNullOrEmpty(tumorType.getMainType()) && tumorType.getMainType().equals(relevantCancerTypeQuery.getMainType())).collect(Collectors.toList());
                        }
                        return queries.stream().filter(tumorType -> tumorType.getLevel() >= 0).collect(Collectors.toSet());
                    } else {
                        return TumorTypeUtils.findRelevantTumorTypes(relevantCancerTypeQuery.getMainType(), true, direction);
                    }
                } else {
                    Set<TumorType> relevantTumorTypes = TumorTypeUtils.findRelevantTumorTypes(relevantCancerTypeQuery.getCode(), false, direction).stream().filter(tumorType -> tumorType.getLevel() > 0).collect(Collectors.toSet());
                    if (isLevelBased) {
                        return relevantTumorTypes.stream().filter(tumorType -> tumorType.getLevel() > 0).collect(Collectors.toSet());
                    } else {
                        return relevantTumorTypes;
                    }
                }
            })
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        return new ResponseEntity<>(
            new ArrayList<>(tumorTypes)
            , HttpStatus.OK
        );
    }

    @Override
    public ResponseEntity<List<Alteration>> utilRelevantAlterationsGet(
        @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "alteration") @RequestParam(value = "entrezGeneId") Integer entrezGeneId
        , @ApiParam(value = "alteration") @RequestParam(value = "alteration") String alteration
    ) {
        ReferenceGenome matchedRG = null;
        if (!org.apache.commons.lang3.StringUtils.isEmpty(referenceGenome)) {
            matchedRG = MainUtils.searchEnum(ReferenceGenome.class, referenceGenome);
            if (matchedRG == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
        Gene gene = GeneUtils.getGeneByEntrezId(entrezGeneId);
        if (gene == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }
        Alteration alt = AlterationUtils.getAlteration(gene.getHugoSymbol(), alteration, null, null, null, null, matchedRG);
        Set<Alteration> relevantAlts = new LinkedHashSet<>();
        if (AlterationUtils.isCategoricalAlteration(alt.getAlteration())) {
            relevantAlts = ApplicationContextSingleton.getAlterationBo().findRelevantAlterationsForCategoricalAlt(matchedRG, alt, AlterationUtils.getAllAlterations(matchedRG, alt.getGene()));
        } else {
            relevantAlts = ApplicationContextSingleton.getAlterationBo().findRelevantAlterations(matchedRG, alt, false);
        }
        return ResponseEntity.ok(new ArrayList<>(relevantAlts));
    }

    @Override
    public ResponseEntity<VariantAnnotation> utilVariantAnnotationGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "entrezGeneId") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "Alteration") @RequestParam(value = "alteration", required = false) String alteration
        , @ApiParam(value = "HGVS genomic format. Example: 7:g.140453136A>T") @RequestParam(value = "hgvsg", required = false) String hgvsg
        , @ApiParam(value = "Genomic change format. Example: 7,140453136,140453136,A,T") @RequestParam(value = "genomicChange", required = false) String genomicChange
        , @ApiParam(value = "OncoTree tumor type name/main type/code") @RequestParam(value = "tumorType", required = false) String tumorType) throws ApiException, org.genome_nexus.ApiException {


        List<TumorType> relevantTumorTypes = TumorTypeUtils.findRelevantTumorTypes(tumorType);

        Query query;
        Gene gene;
        ReferenceGenome matchedRG = null;
        if (!org.apache.commons.lang3.StringUtils.isEmpty(referenceGenome)) {
            matchedRG = MainUtils.searchEnum(ReferenceGenome.class, referenceGenome);
            if (matchedRG == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
        if (!StringUtils.isNullOrEmpty(hgvsg) || !StringUtils.isNullOrEmpty(genomicChange)) {
            Alteration alterationModel = null;
            if (!StringUtils.isNullOrEmpty(hgvsg)) {
                if (this.cacheFetcher.hgvsgShouldBeAnnotated(hgvsg, matchedRG)) {
                    alterationModel = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.HGVS_G, matchedRG, hgvsg);
                }
                if (alterationModel == null) alterationModel = new Alteration();
            } else {
                if (this.cacheFetcher.genomicLocationShouldBeAnnotated(GenomeNexusUtils.convertGenomicLocation(genomicChange), matchedRG)) {
                    alterationModel = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.GENOMIC_LOCATION, matchedRG, genomicChange);
                }
                if (alterationModel == null) alterationModel = new Alteration();
            }
            query = QueryUtils.getQueryFromAlteration(matchedRG, tumorType, alterationModel, hgvsg);
            gene = GeneUtils.getGeneByEntrezId(query.getEntrezGeneId());
        } else {
            gene = GeneUtils.getGene(entrezGeneId, hugoSymbol);
            Alteration alterationModel = AlterationUtils.findAlteration(gene, matchedRG, alteration);
            if (alterationModel == null) {
                if (gene == null) {
                    alterationModel = new Alteration();
                    gene = new Gene();
                    gene.setHugoSymbol(hugoSymbol);
                    gene.setEntrezGeneId(entrezGeneId);
                    alterationModel.setGene(gene);
                    alterationModel.setAlteration(alteration);
                } else {
                    alterationModel = AlterationUtils.getAlteration(gene.getHugoSymbol(), alteration, null, null, null, null, matchedRG);
                }
            }
            query = new Query(alterationModel, matchedRG);
        }
        query.setTumorType(tumorType);
        List<EvidenceQueryRes> responses = EvidenceUtils.processRequest(Collections.singletonList(query), new HashSet<>(EvidenceTypeUtils.getAllEvidenceTypes()), LevelUtils.getPublicLevels(), false, false);
        IndicatorQueryResp indicatorQueryResp = IndicatorUtils.processQuery(query, null, false, null, false);

        EvidenceQueryRes response = responses.iterator().next();

        VariantAnnotation annotation = new VariantAnnotation(indicatorQueryResp);

        // for any hgvsg variant, we need to check whether it is VUE
        if(!StringUtils.isNullOrEmpty(hgvsg)) {
            TranscriptConsequenceSummary transcriptConsequenceSummary = GenomeNexusUtils.getTranscriptConsequence(GNVariantAnnotationType.HGVS_G, hgvsg, matchedRG);
            if (transcriptConsequenceSummary != null && transcriptConsequenceSummary.isIsVue() != null && transcriptConsequenceSummary.isIsVue()) {
                annotation.setVUE(true);
            }
        }

        Set<Evidence> background = EvidenceUtils.getEvidenceByGeneAndEvidenceTypes(gene, Collections.singleton(EvidenceType.GENE_BACKGROUND));
        if (background.size() > 0) {
            annotation.setBackground(CplUtils.annotateGene(background.iterator().next().getDescription(), gene.getHugoSymbol()));
        }

        for (TumorType uniqueTumorType : response.getEvidences().stream().filter(evidence -> !evidence.getCancerTypes().isEmpty()).map(evidence -> evidence.getCancerTypes()).flatMap(Collection::stream).collect(Collectors.toSet())) {
            VariantAnnotationTumorType variantAnnotationTumorType = new VariantAnnotationTumorType();
            variantAnnotationTumorType.setRelevantTumorType(relevantTumorTypes.contains(uniqueTumorType));
            variantAnnotationTumorType.setTumorType(uniqueTumorType);
            variantAnnotationTumorType.setEvidences(response.getEvidences().stream().filter(evidence -> !evidence.getCancerTypes().isEmpty() && evidence.getCancerTypes().contains(uniqueTumorType)).map(evidence -> {
                Evidence updatedEvidence = new Evidence(evidence, evidence.getId());
                if (updatedEvidence.getGene().getHugoSymbol().equals("ESR1")) {
                    updatedEvidence = MainUtils.convertSpecialESR1Evidence(updatedEvidence);
                }
                if (updatedEvidence.getRelevantCancerTypes() == null || updatedEvidence.getRelevantCancerTypes().size() == 0) {
                    updatedEvidence.setRelevantCancerTypes(TumorTypeUtils.findEvidenceRelevantCancerTypes(evidence));
                }
                return updatedEvidence;
            }).collect(Collectors.toList()));
            annotation.getTumorTypes().add(variantAnnotationTumorType);
        }
        return new ResponseEntity<>(annotation, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<CancerTypeCount>> utilPortalAlterationSampleCountGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
    ) {
        PortalAlterationBo portalAlterationBo = ApplicationContextSingleton.getPortalAlterationBo();
        List<CancerTypeCount> counts = new ArrayList<>();
        if (hugoSymbol == null) {
            counts.addAll(portalAlterationBo.findPortalAlterationCount());
        } else {
            Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
            counts.addAll(portalAlterationBo.findPortalAlterationCountByGene(gene));

        }
        return new ResponseEntity<>(counts, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<PortalAlteration>> utilMutationMapperDataGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
    ) {
        PortalAlterationBo portalAlterationBo = ApplicationContextSingleton.getPortalAlterationBo();
        List<PortalAlteration> portalAlterations = new ArrayList<>();
        Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
        portalAlterations.addAll(portalAlterationBo.findMutationMapperData(gene));
        return new ResponseEntity<>(portalAlterations, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<EnsemblGene>> utilsEnsemblGenesGet(
        @ApiParam(value = "Gene entrez id", required = true) @RequestParam(value = "entrezGeneId") Integer entrezGeneId
    ) {
        List<EnsemblGene> genes = new ArrayList<>();
        if (entrezGeneId != null && entrezGeneId > 0) {
            try {
                Optional<org.oncokb.oncokb_transcript.client.Gene> geneOptional = cacheFetcher.getAllTranscriptGenes().stream().filter(gene -> gene.getEntrezGeneId().equals(entrezGeneId)).findFirst();
                if (geneOptional.isPresent()) {
                    genes = geneOptional.get().getEnsemblGenes().stream().map(org.mskcc.cbio.oncokb.apiModels.ensembl.EnsemblGene::new).collect(Collectors.toList());
                }
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
        return new ResponseEntity<>(genes, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Void> utilUpdateTranscriptGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(required = false) String hugoSymbol
        , @ApiParam(value = "entrezGeneId") @RequestParam(required = false) Integer entrezGeneId
        , @ApiParam(value = "grch37Isoform") @RequestParam(required = false) String grch37Isoform
        , @ApiParam(value = "grch37RefSeq") @RequestParam(required = false) String grch37RefSeq
        , @ApiParam(value = "grch38Isoform") @RequestParam(required = false) String grch38Isoform
        , @ApiParam(value = "grch38RefSeq") @RequestParam(required = false) String grch38RefSeq
    ) throws ApiException, IOException {
        // this is an util to upgrade oncokb transcript which operates on the grch37
        Gene gene = GeneUtils.getGene(entrezGeneId, hugoSymbol);

        if (gene == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();
        oncokbTranscriptService.updateTranscriptUsage(
            gene,
            grch37Isoform,
            grch38Isoform
        );

        gene.setGrch37Isoform(grch37Isoform);
        gene.setGrch37RefSeq(grch37RefSeq);
        gene.setGrch38Isoform(grch38Isoform);
        gene.setGrch38RefSeq(grch38RefSeq);

        ApplicationContextSingleton.getGeneBo().update(gene);
        CacheUtils.updateGene(Collections.singleton(gene.getEntrezGeneId()), true);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> utilValidateTranscriptUpdateGet(
        @ApiParam(value = "hugoSymbol") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "entrezGeneId") @RequestParam(required = false) Integer entrezGeneId
        , @ApiParam(value = "grch37Isoform") @RequestParam(required = false) String grch37Isoform
        , @ApiParam(value = "grch38Isoform") @RequestParam(required = false) String grch38Isoform
    ) throws ApiException {
        // this is an util to upgrade oncokb transcript which operates on the grch37
        Gene gene = GeneUtils.getGene(entrezGeneId, hugoSymbol);

        if (gene == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();

        StringBuilder sb = new StringBuilder();
        TranscriptUpdateValidationVM vm = oncokbTranscriptService.validateTranscriptUpdate(gene, grch37Isoform, grch38Isoform);
        if (vm.getGrch37() != null) {
            if (vm.getGrch37().getMatch()) {
                sb.append("GRCh37 sequences are the same.\n");
            } else {
                sb.append("GRCh37 sequences do not match.\n");
                sb.append("GRCh37 old: " + vm.getGrch37().getSequenceA() + "\n");
                sb.append("GRCh37 new: " + vm.getGrch37().getSequenceB() + "\n");
            }
        }
        if (vm.getGrch38() != null) {
            sb.append("\n");
            if (vm.getGrch38().getMatch()) {
                sb.append("GRCh38 sequences are the same.\n");
            } else {
                sb.append("GRCh38 sequences do not match.\n");
                sb.append("GRCh38 old: " + vm.getGrch38().getSequenceA() + "\n");
                sb.append("GRCh38 new: " + vm.getGrch38().getSequenceB() + "\n");
            }
        }
        return new ResponseEntity<>(sb.toString(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<DownloadAvailability>> utilDataAvailabilityGet() {
        return new ResponseEntity<>(CacheUtils.getDownloadAvailabilities(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> utilDataReadmeGet(
        @ApiParam(value = "version") @RequestParam(value = "version", required = false) String version
    ) {
        return getDataDownloadResponseEntity(version, FileName.README, FileExtension.MARK_DOWN);
    }

    @Override
    public ResponseEntity<byte[]> utilDataSqlDumpGet(
        @ApiParam(value = "version") @RequestParam(value = "version", required = false) String version
    ) {
        return getDataDownloadResponseEntity(version, getOncoKBSqlDumpFileName(version), FileExtension.GZ);
    }

    @Override
    public ResponseEntity<byte[]> utilDataTranscriptSqlDump(
        @ApiParam(value = "version") @RequestParam(value = "version", required = false) String version
    ) {
        return getDataDownloadResponseEntity(version, getOncoKBTranscriptSqlDumpFileName(version), FileExtension.GZ);
    }

    @Override
    public ResponseEntity<List<TranscriptCoverageFilterResult>> utilFilterHgvsgBasedOnCoveragePost(
        @ApiParam(value = "List of queries.", required = true) @RequestBody List<AnnotateMutationByHGVSQuery> body
    ) throws ApiException, ApiHttpErrorException {
        List<TranscriptCoverageFilterResult> result = new ArrayList<>();

        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        } else {
            for (AnnotateMutationByHGVSQuery query : body) {
                boolean shouldAnnotate = this.cacheFetcher.hgvsgShouldBeAnnotated(query.getHgvs(), query.getReferenceGenome());
                result.add(new TranscriptCoverageFilterResult(query.getHgvs(), shouldAnnotate));
            }
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<TranscriptCoverageFilterResult>> utilFilterGenomicChangeBasedOnCoveragePost(
        @ApiParam(value = "List of queries.", required = true) @RequestBody List<AnnotateMutationByGenomicChangeQuery> body
    ) throws ApiException, ApiHttpErrorException {
        List<TranscriptCoverageFilterResult> result = new ArrayList<>();

        if (body == null) {
            throw new ApiHttpErrorException("The request body is missing.", HttpStatus.BAD_REQUEST);
        } else {
            for (AnnotateMutationByGenomicChangeQuery query : body) {
                boolean shouldAnnotate = this.cacheFetcher.genomicLocationShouldBeAnnotated(GenomeNexusUtils.convertGenomicLocation(query.getGenomicLocation()), query.getReferenceGenome());
                result.add(new TranscriptCoverageFilterResult(query.getGenomicLocation(), shouldAnnotate));
            }
        }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

}
