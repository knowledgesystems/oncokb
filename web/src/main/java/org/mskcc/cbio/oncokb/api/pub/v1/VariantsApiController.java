package org.mskcc.cbio.oncokb.api.pub.v1;

import io.swagger.annotations.ApiParam;
import java.util.Collections;
import org.apache.commons.lang3.StringUtils;
import org.genome_nexus.ApiException;
import org.mskcc.cbio.oncokb.bo.AlterationBo;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.genomeNexus.TranscriptSummaryAlterationResult;
import org.mskcc.cbio.oncokb.service.JsonResultFactory;
import org.mskcc.cbio.oncokb.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.SpringCodegen", date = "2016-10-19T19:28:21.941Z")

@Controller
public class VariantsApiController implements VariantsApi {

    public ResponseEntity<List<Alteration>> variantsGet(
        @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) {
        List<Alteration> alterations = new ArrayList(AlterationUtils.getAllAlterations());

        return ResponseEntity.ok().body(JsonResultFactory.getAlteration(alterations, fields));
    }

    public ResponseEntity<List<Alteration>> variantsLookupGet(
        @ApiParam(value = "The entrez gene ID. entrezGeneId is prioritize than hugoSymbol if both parameters have been defined") @RequestParam(value = "entrezGeneId", required = false) Integer entrezGeneId
        , @ApiParam(value = "The gene symbol used in Human Genome Organisation.") @RequestParam(value = "hugoSymbol", required = false) String hugoSymbol
        , @ApiParam(value = "variant name.") @RequestParam(value = "variant", required = false) String variant
        , @ApiParam(value = "Reference genome, either GRCh37 or GRCh38. The default is GRCh37", required = false, defaultValue = "GRCh37") @RequestParam(value = "referenceGenome", required = false, defaultValue = "GRCh37") String referenceGenome
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) throws ApiException {
        ReferenceGenome matchedRG = null;
        if (!StringUtils.isEmpty(referenceGenome)) {
            matchedRG = MainUtils.searchEnum(ReferenceGenome.class, referenceGenome);
            if (matchedRG == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
        List<Alteration> result = new ArrayList<>();
        Gene gene = GeneUtils.getGene(entrezGeneId, hugoSymbol);
        if (gene != null) {
            if (StringUtils.isEmpty(variant)) {
                result = new ArrayList<>(AlterationUtils.getAllAlterations(matchedRG, gene));
            } else {
                Alteration alteration = new Alteration();
                alteration.setGene(gene);
                alteration.setAlteration(variant);
                Alteration matchedAlteration = AlterationUtils.findExactlyMatchedAlteration(matchedRG, alteration, AlterationUtils.getAllAlterations(matchedRG, gene));
                if (matchedAlteration != null) {
                    result.add(matchedAlteration);
                }
            }
        }
        return ResponseEntity.ok().body(JsonResultFactory.getAlteration(result, fields));
    }

    @Override
    public ResponseEntity<List<List<Alteration>>> variantsLookupPost(
        @ApiParam(value = "List of queries.", required = true) @RequestBody(required = true) List<VariantSearchQuery> body
        , @ApiParam(value = "The fields to be returned.") @RequestParam(value = "fields", required = false) String fields
    ) throws ApiException {
        List<List<Alteration>> result = new ArrayList<>();
        if (body != null) {
            for (VariantSearchQuery query : body) {
                result.add(getVariants(query));
            }
        }
        return ResponseEntity.ok().body(JsonResultFactory.getAlteration2D(result, fields));
    }

    private List<Alteration> getVariants(VariantSearchQuery query) throws ApiException {
        LinkedHashSet<Alteration> alterationSet = new LinkedHashSet<>();
        List<Alteration> alterationList = new ArrayList<>();
        if (query != null) {
            if (query.getHgvs() != null && !query.getHgvs().isEmpty()) {
                TranscriptSummaryAlterationResult transcriptSummaryAlterationResult = AlterationUtils.getAlterationFromGenomeNexus(GNVariantAnnotationType.HGVS_G, query.getReferenceGenome(), query.getHgvs());
                Alteration alteration = transcriptSummaryAlterationResult.getAlteration()
;                if (alteration != null && alteration.getGene() != null) {
                    List<Alteration> allAlterations = AlterationUtils.getAllAlterations(query.getReferenceGenome(), alteration.getGene());
                    alterationList.addAll(ApplicationContextSingleton.getAlterationBo().findRelevantAlterations(query.getReferenceGenome(), alteration, allAlterations, true));
                }
            } else if (query.getHugoSymbol() != null || query.getEntrezGeneId() != null) {
                Gene gene = GeneUtils.getGene(query.getEntrezGeneId(), query.getHugoSymbol());
                if (gene != null) {
                    if (AlterationUtils.isInferredAlterations(query.getVariant())) {
                        // If inferred alteration has been manually curated, it should be returned in the list
                        Alteration alteration = AlterationUtils.findAlteration(gene, query.getReferenceGenome(), query.getVariant());
                        if (alteration != null) {
                            alterationSet.add(alteration);
                        }
                        alterationSet.addAll(AlterationUtils.getAlterationsByKnownEffectInGene(gene, AlterationUtils.getInferredAlterationsKnownEffect(query.getVariant()), true));
                    } else if (AlterationUtils.isLikelyInferredAlterations(query.getVariant())) {
                        alterationSet.addAll(AlterationUtils.getAlterationsByKnownEffectInGene(gene, AlterationUtils.getInferredAlterationsKnownEffect(query.getVariant()), false));
                    } else {
                        List<Alteration> allAlterations = AlterationUtils.getAllAlterations(query.getReferenceGenome(), gene);
                        if (query.getVariant() == null && query.getProteinStart() == null && query.getProteinEnd() == null) {
                            alterationSet.addAll(allAlterations);
                        } else {
                            AlterationBo alterationBo = new ApplicationContextSingleton().getAlterationBo();
                            List<Alteration> alterations = AlterationUtils.lookupVariant(query.getVariant(), true, false, allAlterations);

                            // If this variant is not annotated
                            if (alterations == null || alterations.isEmpty()) {
                                Alteration alteration = AlterationUtils.getAlteration(gene.getHugoSymbol(), query.getVariant(), AlterationType.getByName(query.getVariantType()), query.getConsequence(), query.getProteinStart(), query.getProteinEnd(), query.getReferenceGenome());
                                if (alteration != null) {
                                    alterations.add(alteration);
                                }
                            }
                            for (Alteration alteration : alterations) {
                                alterationSet.addAll(alterationBo.findRelevantAlterations(query.getReferenceGenome(), alteration, allAlterations, true));
                            }
                        }
                    }
                }
            } else if (query.getVariant() != null) {
                if (AlterationUtils.isInferredAlterations(query.getVariant())) {
                    for (Gene gene : CacheUtils.getAllGenes()) {
                        // If inferred alteration has been manually curated, it should be returned in the list
                        Alteration alteration = AlterationUtils.findAlteration(gene, query.getReferenceGenome(), query.getVariant());
                        if (alteration != null) {
                            alterationSet.add(alteration);
                        }

                        alterationSet.addAll(AlterationUtils.getAlterationsByKnownEffectInGene(gene, AlterationUtils.getInferredAlterationsKnownEffect(query.getVariant()), true));
                    }
                } else if (AlterationUtils.isLikelyInferredAlterations(query.getVariant())) {
                    for (Gene gene : CacheUtils.getAllGenes()) {
                        alterationSet.addAll(AlterationUtils.getAlterationsByKnownEffectInGene(gene, AlterationUtils.getInferredAlterationsKnownEffect(query.getVariant()), false));
                    }
                } else {
                    alterationList = AlterationUtils.lookupVariant(query.getVariant(), false, false, AlterationUtils.getAllAlterations());
                }
            }
        }

        alterationList.addAll(alterationSet);
        return alterationList;
    }

}
