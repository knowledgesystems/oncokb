package org.mskcc.cbio.oncokb.util;

import com.mysql.jdbc.StringUtils;
import org.mskcc.cbio.oncokb.model.*;

import java.util.LinkedHashSet;

import static org.mskcc.cbio.oncokb.Constants.UPSTREAM_GENE;

/**
 * Created by Hongxin Zhang on 8/23/17.
 */
public class QueryUtils {
    public static String getAlterationName(Query query) {
        String name = "";
        if (query != null) {
            if (StringUtils.isNullOrEmpty(query.getAlteration()) || query.getAlteration().trim().matches("(?i)^fusion$")) {
                AlterationType alterationType = AlterationType.getByName(query.getAlterationType());
                if (alterationType != null) {
                    if (alterationType.equals(AlterationType.FUSION) ||
                        (alterationType.equals(AlterationType.STRUCTURAL_VARIANT) &&
                            !StringUtils.isNullOrEmpty(query.getConsequence()) &&
                            query.getConsequence().equalsIgnoreCase("fusion"))) {
                        if (query.getEntrezGeneId() != null) {
                            // For structural variant, if the entrezGeneId is specified which means this is probably a intragenic event. In this case, the hugoSymbol should be ignore.
                            Gene entrezGeneIdGene = GeneUtils.getGeneByEntrezId(query.getEntrezGeneId());
                            name = entrezGeneIdGene.getHugoSymbol();
                        } else {
                            LinkedHashSet<String> genes = StringUtils.isNullOrEmpty(query.getHugoSymbol()) ? new LinkedHashSet<>() : new LinkedHashSet<>(FusionUtils.getGenesStrs(query.getHugoSymbol()));
                            if (genes.size() > 1) {
                                name = org.apache.commons.lang3.StringUtils.join(genes, "-") + " Fusion";
                            } else if (genes.size() == 1) {
                                name = "Fusions";
                            }
                        }
                    }
                }
            }
        }

        if (StringUtils.isNullOrEmpty(name)) {
            name = query.getAlteration().trim();
        }
        return name;
    }

    public static Query getQueryFromAlteration(ReferenceGenome referenceGenome, String tumorType, Alteration alteration, String hgvs) {
        Query query = new Query();
        query.setReferenceGenome(referenceGenome);
        query.setHgvs(hgvs);
        query.setTumorType(tumorType);
        if (alteration != null) {
            if (alteration.getGene() != null) {
                query.setHugoSymbol(alteration.getGene().getHugoSymbol());
                query.setEntrezGeneId(alteration.getGene().getEntrezGeneId());
            }
            query.setAlteration(alteration.getAlteration());
            query.setProteinStart(alteration.getProteinStart());
            query.setProteinEnd(alteration.getProteinEnd());
            if (alteration.getConsequence() != null) {
                query.setConsequence(alteration.getConsequence().getTerm());
                if (alteration.getConsequence().getTerm().equals(UPSTREAM_GENE) && StringUtils.isNullOrEmpty(query.getAlteration())) {
                    query.setAlteration(SpecialVariant.PROMOTER.getVariant());
                }
            }
        }
        return query;
    }
}
