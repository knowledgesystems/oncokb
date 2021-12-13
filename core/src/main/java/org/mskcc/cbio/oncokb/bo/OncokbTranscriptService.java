package org.mskcc.cbio.oncokb.bo;

import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.apiModels.TranscriptUpdateValidationVM;
import org.mskcc.cbio.oncokb.model.Gene;
import org.mskcc.cbio.oncokb.model.ReferenceGenome;
import org.mskcc.cbio.oncokb.util.CacheUtils;
import org.mskcc.cbio.oncokb.util.GeneUtils;
import org.mskcc.cbio.oncokb.util.PropertiesUtils;
import org.oncokb.oncokb_transcript.ApiClient;
import org.oncokb.oncokb_transcript.ApiException;
import org.oncokb.oncokb_transcript.Configuration;
import org.oncokb.oncokb_transcript.auth.OAuth;
import org.oncokb.oncokb_transcript.client.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by Hongxin Zhang on 2/24/21.
 */
public class OncokbTranscriptService {
    private ApiClient client;
    private final int TIMEOUT = 30000;
    private final String SEQUENCE_TYPE = "PROTEIN";

    public OncokbTranscriptService() {
        this.client = Configuration.getDefaultApiClient();
        this.client.setConnectTimeout(TIMEOUT);
        this.client.setReadTimeout(TIMEOUT);

        // Configure API key authorization: authorization
        OAuth authorization = (OAuth) this.client.getAuthentication("Authorization");
        String oncokbTranscriptToken = PropertiesUtils.getProperties("oncokb_transcript.token");
        authorization.setAccessToken(oncokbTranscriptToken);
    }

    public void updateTranscriptUsage(Gene gene, String grch37EnsemblTranscriptId, String grch38EnsemblTranscriptId) throws ApiException {
        TranscriptControllerApi controllerApi = new TranscriptControllerApi();

        if(StringUtils.isNotEmpty(grch37EnsemblTranscriptId)) {
            controllerApi.addTranscriptUsingPOST(
                gene.getEntrezGeneId(),
                ReferenceGenome.GRCh37.toString(),
                grch37EnsemblTranscriptId,
                true
            );
        }
        if (StringUtils.isNotEmpty(grch38EnsemblTranscriptId)) {
            controllerApi.addTranscriptUsingPOST(
                gene.getEntrezGeneId(),
                ReferenceGenome.GRCh38.toString(),
                grch38EnsemblTranscriptId,
                true
            );
        }
    }

    public TranscriptUpdateValidationVM validateTranscriptUpdate(Gene gene, String grch37EnsemblTranscriptId, String grch38EnsemblTranscriptId) throws ApiException {
        TranscriptUpdateValidationVM transcriptUpdateValidationVM = new TranscriptUpdateValidationVM();
        if (StringUtils.isNotEmpty(grch37EnsemblTranscriptId)) {
            transcriptUpdateValidationVM.setGrch37(this.compareTranscript(TranscriptPairVM.ReferenceGenomeEnum.GRCH37, gene, grch37EnsemblTranscriptId));
        }
        if (StringUtils.isNotEmpty(grch38EnsemblTranscriptId)) {
            transcriptUpdateValidationVM.setGrch38(this.compareTranscript(TranscriptPairVM.ReferenceGenomeEnum.GRCH38, gene, grch38EnsemblTranscriptId));
        }
        return transcriptUpdateValidationVM;
    }

    private TranscriptComparisonResultVM compareTranscript(TranscriptPairVM.ReferenceGenomeEnum referenceGenome, Gene gene, String ensemblTranscriptId)  throws ApiException{
        SequenceControllerApi sequenceControllerApi = new SequenceControllerApi();
        TranscriptControllerApi controllerApi = new TranscriptControllerApi();

        Sequence pickedSequence = null;
        try {
            pickedSequence = sequenceControllerApi.findCanonicalSequenceUsingGET(referenceGenome.toString(), gene.getEntrezGeneId(), SEQUENCE_TYPE);
        } catch (ApiException e) {
            throw e;
        }
        if (pickedSequence == null) {
            return null;
        } else {

            TranscriptComparisonVM vm = new TranscriptComparisonVM();
            vm.setAlign(true);

            // Pair A is the old transcript
            TranscriptPairVM pairA = new TranscriptPairVM();
            pairA.setReferenceGenome(referenceGenome);
            pairA.setTranscript(pickedSequence.getTranscript().getEnsemblTranscriptId());

            // Pair B is the new transcript
            TranscriptPairVM pairB = new TranscriptPairVM();
            pairB.setReferenceGenome(referenceGenome);
            pairB.setTranscript(ensemblTranscriptId);

            vm.setTranscriptA(pairA);
            vm.setTranscriptB(pairB);

            return controllerApi.compareTranscriptUsingPOST(gene.getHugoSymbol(), vm);
        }
    }

    public String getProteinSequence(ReferenceGenome referenceGenome, Gene gene) throws ApiException {
        SequenceControllerApi sequenceResourceApi = new SequenceControllerApi();
        Sequence sequence = sequenceResourceApi.findCanonicalSequenceUsingGET(referenceGenome.name(), gene.getEntrezGeneId(), SEQUENCE_TYPE);
        return sequence == null ? null : sequence.getSequence();
    }

    public List<Sequence> getAllProteinSequences(ReferenceGenome referenceGenome) throws ApiException {
        SequenceControllerApi sequenceResourceApi = new SequenceControllerApi();
        return sequenceResourceApi.findCanonicalSequencesUsingPOST(referenceGenome.name(), CacheUtils.getAllGenes().stream().map(Gene::getEntrezGeneId).collect(Collectors.toList()), SEQUENCE_TYPE);
    }

    public String getAminoAcid(ReferenceGenome referenceGenome, Gene gene, int positionStart, int length) throws ApiException {
        String sequence = getProteinSequence(referenceGenome, gene);

        if (sequence.length() >= (positionStart + length - 1)) {
            return sequence.substring(positionStart - 1, positionStart + length - 1);
        }
        return "";
    }

    public List<Drug> findDrugs(String query) throws ApiException {
        DrugControllerApi drugControllerApi = new DrugControllerApi();
        return drugControllerApi.findDrugsUsingGET(query);
    }

    public Drug findDrugByNcitCode(String code) throws ApiException {
        DrugControllerApi drugControllerApi = new DrugControllerApi();
        return drugControllerApi.findDrugByCodeUsingGET(code);
    }

    public Gene findGeneBySymbol(String symbol) throws ApiException {
        Gene gene = GeneUtils.getGene(symbol);
        if (gene != null) {
            return gene;
        }
        GeneControllerApi geneControllerApi = new GeneControllerApi();
        org.oncokb.oncokb_transcript.client.Gene transcriptGene = geneControllerApi.findGeneBySymbolUsingGET(symbol);
        return transcriptGeneMap(transcriptGene);
    }

    public List<TranscriptDTO> findEnsemblTranscriptsByIds(List<String> ensemblTranscriptIds, ReferenceGenome referenceGenome) throws ApiException {
        TranscriptControllerApi transcriptControllerApi = new TranscriptControllerApi();
        return transcriptControllerApi.findTranscriptsByEnsemblIdsUsingPOST(referenceGenome.name(), ensemblTranscriptIds);
    }

    public List<Gene> findGenesBySymbols(List<String> symbols) throws ApiException {
        List<String> unknownGenes = new ArrayList<>();
        List<Gene> genes = new ArrayList<>();
        for (String symbol : symbols) {
            Gene gene = GeneUtils.getGene(symbol);
            if (gene == null) {
                unknownGenes.add(symbol);
            } else {
                genes.add(gene);
            }
        }
        if (unknownGenes.size() > 0) {
            this.findTranscriptGenesBySymbols(unknownGenes).stream().filter(gene -> gene != null).forEach(gene -> genes.add(transcriptGeneMap(gene)));
        }
        return genes;
    }

    public List<org.oncokb.oncokb_transcript.client.Gene> findTranscriptGenesBySymbols(List<String> symbols) throws ApiException {
        GeneControllerApi geneControllerApi = new GeneControllerApi();
        return geneControllerApi.findGenesBySymbolsUsingPOST(symbols);
    }

    private Gene transcriptGeneMap(org.oncokb.oncokb_transcript.client.Gene transcriptGene) {
        if (transcriptGene != null) {
            Gene gene = new Gene();
            gene.setHugoSymbol(transcriptGene.getHugoSymbol());
            gene.setEntrezGeneId(transcriptGene.getEntrezGeneId());
            gene.setGeneAliases(transcriptGene.getGeneAliases().stream().map(geneAlias -> geneAlias.getName()).collect(Collectors.toSet()));
            return gene;
        }
        return new Gene();
    }
}
