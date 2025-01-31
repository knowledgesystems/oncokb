package org.mskcc.cbio.oncokb.api.pub.v1;

import io.swagger.annotations.ApiParam;
import org.mskcc.cbio.oncokb.apiModels.ActionableGene;
import org.mskcc.cbio.oncokb.apiModels.AnnotatedVariant;
import org.mskcc.cbio.oncokb.apiModels.CuratedGene;
import org.mskcc.cbio.oncokb.apiModels.VariantOfUnknownSignificance;
import org.mskcc.cbio.oncokb.apiModels.download.FileName;
import org.mskcc.cbio.oncokb.apiModels.download.FileExtension;
import org.mskcc.cbio.oncokb.cache.CacheFetcher;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.BiologicalVariant;
import org.mskcc.cbio.oncokb.util.*;
import org.oncokb.oncokb_transcript.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.api.pub.v1.Constants.INCLUDE_EVIDENCE;
import static org.mskcc.cbio.oncokb.api.pub.v1.Constants.VERSION;
import static org.mskcc.cbio.oncokb.util.HttpUtils.getDataDownloadResponseEntity;

/**
 * Created by Hongxin on 10/28/16.
 */
@Controller
public class UtilsApiController implements UtilsApi {
    @Autowired
    CacheFetcher cacheFetcher;

    @Override
    public ResponseEntity<List<AnnotatedVariant>> utilsAllAnnotatedVariantsGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
    ) {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.ALL_ANNOTATED_VARIANTS, FileExtension.JSON);
        }
        return new ResponseEntity<>(getAllAnnotatedVariants(false), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> utilsAllAnnotatedVariantsTxtGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
    ) {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.ALL_ANNOTATED_VARIANTS, FileExtension.TEXT);
        }
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
        header.add("Reference Genome");
        header.add("Alteration");
        header.add("Protein Change");
        header.add("Oncogenicity");
        header.add("Mutation Effect");
        header.add("PMIDs");
        header.add("Abstracts");
        header.add("Description");
        sb.append(MainUtils.listToString(header, separator));
        sb.append(newLine);

        for (AnnotatedVariant annotatedVariant : getAllAnnotatedVariants(false)) {
            List<String> row = new ArrayList<>();
            row.add(annotatedVariant.getGrch37Isoform());
            row.add(annotatedVariant.getGrch37RefSeq());
            row.add(annotatedVariant.getGrch38Isoform());
            row.add(annotatedVariant.getGrch38RefSeq());
            row.add(String.valueOf(annotatedVariant.getEntrezGeneId()));
            row.add(annotatedVariant.getGene());
            row.add(annotatedVariant.getReferenceGenome());
            row.add(annotatedVariant.getVariant());
            row.add(annotatedVariant.getProteinChange());
            row.add(annotatedVariant.getOncogenicity());
            row.add(annotatedVariant.getMutationEffect());
            row.add(annotatedVariant.getMutationEffectPmids());
            row.add(annotatedVariant.getMutationEffectAbstracts());
            row.add(annotatedVariant.getDescription());
            sb.append(MainUtils.listToString(row, separator));
            sb.append(newLine);
        }
        return new ResponseEntity<>(sb.toString(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<VariantOfUnknownSignificance>> utilsAllVariantsOfUnknownSignificanceGet() {
        return new ResponseEntity<>(getAllVus(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> utilsAllVariantsOfUnknownSignificanceTxtGet() {
        String separator = "\t";
        String newLine = "\n";

        StringBuilder sb = new StringBuilder();
        List<String> header = new ArrayList<>();
        header.add("Entrez Gene ID");
        header.add("Hugo Symbol");
        header.add("Alteration");
        sb.append(MainUtils.listToString(header, separator));
        sb.append(newLine);

        for (VariantOfUnknownSignificance vus : getAllVus()) {
            List<String> row = new ArrayList<>();
            row.add(String.valueOf(vus.getEntrezGeneId()));
            row.add(vus.getGene());
            row.add(vus.getVariant());
            sb.append(MainUtils.listToString(row, separator));
            sb.append(newLine);
        }
        return new ResponseEntity<>(sb.toString(), HttpStatus.OK);
    }

    private List<AnnotatedVariant> getAllAnnotatedVariants(boolean germline) {
        List<AnnotatedVariant> annotatedVariantList = new ArrayList<>();
        Set<Gene> genes = CacheUtils.getAllGenes();
        Map<Gene, Set<BiologicalVariant>> map = new HashMap<>();

        for (Gene gene : genes) {
            map.put(gene, MainUtils.getBiologicalVariants(gene, germline));
        }

        Set<AnnotatedVariant> annotatedVariants = new HashSet<>();
        for (Map.Entry<Gene, Set<BiologicalVariant>> entry : map.entrySet()) {
            Gene gene = entry.getKey();
            for (BiologicalVariant biologicalVariant : entry.getValue()) {
                Set<ArticleAbstract> articleAbstracts = biologicalVariant.getMutationEffectAbstracts();
                List<String> abstracts = new ArrayList<>();
                for (ArticleAbstract articleAbstract : articleAbstracts) {
                    abstracts.add(articleAbstract.getAbstractContent() + " " + articleAbstract.getLink());
                }
                annotatedVariants.add(new AnnotatedVariant(
                    gene.getGrch37Isoform(),
                    gene.getGrch37RefSeq(),
                    gene.getGrch38Isoform(),
                    gene.getGrch38RefSeq(),
                    gene.getEntrezGeneId(),
                    gene.getHugoSymbol(),
                    biologicalVariant.getVariant().getReferenceGenomes().stream().map(referenceGenome -> referenceGenome.name()).collect(Collectors.joining(", ")),
                    biologicalVariant.getVariant().getName(),
                    biologicalVariant.getVariant().getAlteration(),
                    biologicalVariant.getOncogenic(),
                    biologicalVariant.getMutationEffect(),
                    MainUtils.listToString(new ArrayList<>(biologicalVariant.getMutationEffectPmids()), ", ", true),
                    MainUtils.listToString(abstracts, "; ", true),
                    CplUtils.annotate(
                        biologicalVariant.getMutationEffectDescription(),
                        gene.getHugoSymbol(),
                        biologicalVariant.getVariant().getName(),
                        null,
                        null,
                        gene,
                        null
                    )
                ));
            }
        }

        annotatedVariantList.addAll(annotatedVariants);
        MainUtils.sortAnnotatedVariants(annotatedVariantList);
        return annotatedVariantList;
    }

    private List<VariantOfUnknownSignificance> getAllVus() {
        List<VariantOfUnknownSignificance> allVus = new ArrayList<>();
        Set<Gene> genes = CacheUtils.getAllGenes();
        Map<Gene, Set<Alteration>> map = new HashMap<>();

        for (Gene gene : genes) {
            map.put(gene, CacheUtils.getVUS(gene.getEntrezGeneId()));
        }

        for (Map.Entry<Gene, Set<Alteration>> entry : map.entrySet()) {
            Gene gene = entry.getKey();
            for (Alteration alteration : entry.getValue()) {
                allVus.add(new VariantOfUnknownSignificance(
                        gene.getEntrezGeneId(),
                        gene.getHugoSymbol(),
                        alteration.getAlteration()
                ));
            }
        }
        MainUtils.sortVusVariants(allVus);
        return allVus;
    }

    @Override
    public ResponseEntity<List<ActionableGene>> utilsAllActionableVariantsGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
    ) {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.ALL_ACTIONABLE_VARIANTS, FileExtension.JSON);
        }
        return new ResponseEntity<>(getAllActionableVariants(), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> utilsAllActionableVariantsTxtGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
    ) {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.ALL_ACTIONABLE_VARIANTS, FileExtension.TEXT);
        }
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
        header.add("Reference Genome");
        header.add("Alteration");
        header.add("Protein Change");
        header.add("Cancer Type");
        header.add("Level");
        header.add("Drugs(s)");
        header.add("PMIDs");
        header.add("Abstracts");
        header.add("Description");
        sb.append(MainUtils.listToString(header, separator));
        sb.append(newLine);

        for (ActionableGene actionableGene : getAllActionableVariants()) {
            List<String> row = new ArrayList<>();
            row.add(actionableGene.getGrch37Isoform());
            row.add(actionableGene.getGrch37RefSeq());
            row.add(actionableGene.getGrch38Isoform());
            row.add(actionableGene.getGrch38RefSeq());
            row.add(String.valueOf(actionableGene.getEntrezGeneId()));
            row.add(actionableGene.getGene());
            row.add(actionableGene.getReferenceGenome());
            row.add(actionableGene.getVariant());
            row.add(actionableGene.getProteinChange());
            row.add(actionableGene.getCancerType());
            row.add(actionableGene.getLevel());
            row.add(actionableGene.getDrugs());
            row.add(actionableGene.getPmids());
            row.add(actionableGene.getAbstracts());
            row.add(actionableGene.getDescription());
            sb.append(MainUtils.listToString(row, separator));
            sb.append(newLine);
        }
        return new ResponseEntity<>(sb.toString(), HttpStatus.OK);
    }

    private List<ActionableGene> getAllActionableVariants() {
        List<ActionableGene> actionableGeneList = new ArrayList<>();
        Set<Gene> genes = CacheUtils.getAllGenes();
        Map<Gene, Set<ClinicalVariant>> map = new HashMap<>();

        for (Gene gene : genes) {
            map.put(gene, MainUtils.getClinicalVariants(gene, false));
        }

        Set<ActionableGene> actionableGenes = new HashSet<>();
        for (Map.Entry<Gene, Set<ClinicalVariant>> entry : map.entrySet()) {
            Gene gene = entry.getKey();
            for (ClinicalVariant clinicalVariant : entry.getValue()) {
                Set<ArticleAbstract> articleAbstracts = clinicalVariant.getDrugAbstracts();
                List<String> abstracts = new ArrayList<>();
                for (ArticleAbstract articleAbstract : articleAbstracts) {
                    abstracts.add(articleAbstract.getAbstractContent() + " " + articleAbstract.getLink());
                }

                if (clinicalVariant.getExcludedCancerTypes().size() > 0) {
                    String cancerTypeName = TumorTypeUtils.getTumorTypesNameWithExclusion(clinicalVariant.getCancerTypes(), clinicalVariant.getExcludedCancerTypes());
                    // for any clinical variant that has cancer type excluded, we no longer list the cancer types separately
                    actionableGenes.add(new ActionableGene(
                        gene.getGrch37Isoform(), gene.getGrch37RefSeq(),
                        gene.getGrch38Isoform(), gene.getGrch38RefSeq(),
                        gene.getEntrezGeneId(),
                        gene.getHugoSymbol(),
                        clinicalVariant.getVariant().getReferenceGenomes().stream().map(referenceGenome -> referenceGenome.name()).collect(Collectors.joining(", ")),
                        clinicalVariant.getVariant().getName(),
                        clinicalVariant.getVariant().getAlteration(),
                        cancerTypeName,
                        clinicalVariant.getLevel(),
                        MainUtils.listToString(new ArrayList<>(clinicalVariant.getDrug()), ", ", true),
                        MainUtils.listToString(new ArrayList<>(clinicalVariant.getDrugPmids()), ", ", true),
                        MainUtils.listToString(abstracts, "; ", true),
                        CplUtils.annotate(
                            clinicalVariant.getDrugDescription(),
                            gene.getHugoSymbol(),
                            clinicalVariant.getVariant().getName(),
                            cancerTypeName,
                            null,
                            gene,
                            null
                        )
                    ));
                } else {
                    for (TumorType tumorType : clinicalVariant.getCancerTypes()) {
                        actionableGenes.add(new ActionableGene(
                            gene.getGrch37Isoform(), gene.getGrch37RefSeq(),
                            gene.getGrch38Isoform(), gene.getGrch38RefSeq(),
                            gene.getEntrezGeneId(),
                            gene.getHugoSymbol(),
                            clinicalVariant.getVariant().getReferenceGenomes().stream().map(referenceGenome -> referenceGenome.name()).collect(Collectors.joining(", ")),
                            clinicalVariant.getVariant().getName(),
                            clinicalVariant.getVariant().getAlteration(),
                            TumorTypeUtils.getTumorTypeName(tumorType),
                            clinicalVariant.getLevel(),
                            MainUtils.listToString(new ArrayList<>(clinicalVariant.getDrug()), ", ", true),
                            MainUtils.listToString(new ArrayList<>(clinicalVariant.getDrugPmids()), ", ", true),
                            MainUtils.listToString(abstracts, "; ", true),
                            CplUtils.annotate(
                                clinicalVariant.getDrugDescription(),
                                gene.getHugoSymbol(),
                                clinicalVariant.getVariant().getName(),
                                TumorTypeUtils.getTumorTypeName(tumorType),
                                null,
                                gene,
                                tumorType
                            )
                        ));
                    }
                }
            }
        }

        actionableGeneList.addAll(actionableGenes);
        MainUtils.sortActionableVariants(actionableGeneList);
        return actionableGeneList;
    }

    @Override
    public ResponseEntity<List<CancerGene>> utilsCancerGeneListGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
    ) throws ApiException, IOException {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.CANCER_GENE_LIST, FileExtension.JSON);
        }
        List<CancerGene> result = this.cacheFetcher.getCancerGenes();
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> utilsCancerGeneListTxtGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
    ) throws ApiException, IOException {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.CANCER_GENE_LIST, FileExtension.TEXT);
        }
        return new ResponseEntity<>(this.cacheFetcher.getCancerGenesTxt(), HttpStatus.OK);
    }


    @Override
    public ResponseEntity<List<CuratedGene>> utilsAllCuratedGenesGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
        , @ApiParam(value = INCLUDE_EVIDENCE, defaultValue = "TRUE") @RequestParam(value = "includeEvidence", required = false, defaultValue = "TRUE") Boolean includeEvidence
    ) {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.ALL_CURATED_GENES, FileExtension.JSON);
        }
        return new ResponseEntity<>(this.cacheFetcher.getCuratedGenes(includeEvidence), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> utilsAllCuratedGenesTxtGet(
        @ApiParam(value = VERSION) @RequestParam(value = "version", required = false) String version
        , @ApiParam(value = INCLUDE_EVIDENCE, defaultValue = "TRUE") @RequestParam(value = "includeEvidence", required = false, defaultValue = "TRUE") Boolean includeEvidence
    ) {
        if (version != null) {
            return getDataDownloadResponseEntity(version, FileName.ALL_CURATED_GENES, FileExtension.TEXT);
        }
        return new ResponseEntity<>(this.cacheFetcher.getCuratedGenesTxt(includeEvidence), HttpStatus.OK);
    }

}
