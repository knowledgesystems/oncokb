/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mskcc.cbio.oncokb.controller;

import io.swagger.annotations.ApiParam;
import org.mskcc.cbio.oncokb.bo.GeneBo;
import org.mskcc.cbio.oncokb.model.Gene;
import org.mskcc.cbio.oncokb.model.Geneset;
import org.mskcc.cbio.oncokb.model.GenomicIndicator;
import org.mskcc.cbio.oncokb.service.JsonResultFactory;
import org.mskcc.cbio.oncokb.util.*;
import org.mskcc.cbio.oncokb.model.Evidence;
import org.mskcc.cbio.oncokb.model.EvidenceType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

/**
 * @author jgao
 */
@Controller
public class GeneController {

    @RequestMapping(value = "/legacy-api/gene.json")
    public @ResponseBody
    List<Gene> getGene(
        @RequestParam(value = "entrezGeneId", required = false) List<Integer> entrezGeneIds
        , @RequestParam(value = "hugoSymbol", required = false) List<String> hugoSymbols
        , @RequestParam(value = "fields", required = false) String fields
    ) {
        if (entrezGeneIds == null && hugoSymbols == null) {
            return new ArrayList<>(CacheUtils.getAllGenes());
        }

        Set<Gene> genes = new HashSet<>();
        if (entrezGeneIds != null) {
            for (Integer entrez : entrezGeneIds) {
                Gene gene = GeneUtils.getGeneByEntrezId(entrez);
                if (gene != null) {
                    genes.add(gene);
                }
            }
        } else if (hugoSymbols != null) {
            for (String hugoSymbol : hugoSymbols) {
                Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
                if (gene != null) {
                    genes.add(gene);
                }
            }
        }

        return JsonResultFactory.getGene(new ArrayList<>(genes), fields);
    }

    @RequestMapping(value = "/legacy-api/genomic-indicators")
    public @ResponseBody
    List<GenomicIndicator> getGenomicIndicators(
        @RequestParam(value = "entrezGeneId", required = false) List<Integer> entrezGeneIds
        , @RequestParam(value = "hugoSymbol", required = false) List<String> hugoSymbols
    ) {
        if (entrezGeneIds == null && hugoSymbols == null) {
            return new ArrayList<>();
        }

        Set<Gene> genes = new HashSet<>();
        if (entrezGeneIds != null) {
            for (Integer entrez : entrezGeneIds) {
                Gene gene = GeneUtils.getGeneByEntrezId(entrez);
                if (gene != null) {
                    genes.add(gene);
                }
            }
        }
        if (hugoSymbols != null) {
            for (String hugoSymbol : hugoSymbols) {
                Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
                if (gene != null) {
                    genes.add(gene);
                }
            }
        }
        Map<Gene, Set<Evidence>> map = EvidenceUtils.getEvidenceByGenes(genes);
        List<GenomicIndicator> indicators = new ArrayList<>();

        for (Map.Entry<Gene, Set<Evidence>> entry : map.entrySet()) {
            for (Evidence evidence : entry.getValue()) {
                if (evidence.getEvidenceType() == EvidenceType.GENOMIC_INDICATOR) {
                    GenomicIndicator indicator = new GenomicIndicator();
                    indicator.setId(evidence.getId());
                    indicator.setGene(evidence.getGene());
                    indicator.setName(evidence.getName());
                    indicator.setDescription(evidence.getDescription());
                    indicators.add(indicator);
                }
            }
        }

        return indicators;
    }

    @RequestMapping(value = "/legacy-api/genes/update/{hugoSymbol}", method = RequestMethod.POST)
    public @ResponseBody
    String updateGene(@ApiParam(value = "hugoSymbol", required = true) @PathVariable("hugoSymbol") String hugoSymbol,
                      @RequestBody(required = true) Gene queryGene) {
        if (!hugoSymbol.equalsIgnoreCase(queryGene.getHugoSymbol())) {
            return "error";
        }
        Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
        if (gene != null) {
            GeneBo geneBo = ApplicationContextSingleton.getGeneBo();
            gene.setTSG(queryGene.getTSG());
            gene.setOncogene(queryGene.getOncogene());
            geneBo.update(gene);
        }
        return "success";
    }

    @RequestMapping(value = "/legacy-api/genes/remove/{hugoSymbol}", method = RequestMethod.POST)
    public @ResponseBody
    String updateGene(@ApiParam(value = "hugoSymbol", required = true) @PathVariable("hugoSymbol") String hugoSymbol) throws IOException {
        if (hugoSymbol == null) {
            return "error";
        }
        Gene gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
        if (gene != null) {
            ApplicationContextSingleton.getEvidenceBo().deleteAll(new ArrayList<>(CacheUtils.getEvidences(gene)));
            ApplicationContextSingleton.getAlterationBo().deleteAll(new ArrayList<>(AlterationUtils.getAllAlterations(null, gene)));
            ApplicationContextSingleton.getGeneBo().delete(gene);
            CacheUtils.updateGene(Collections.singleton(gene.getEntrezGeneId()), true);
        }
        return "success";
    }

}
