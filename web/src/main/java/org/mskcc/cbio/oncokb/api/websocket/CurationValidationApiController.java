package org.mskcc.cbio.oncokb.api.websocket;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mskcc.cbio.oncokb.bo.OncokbTranscriptService;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.util.*;
import org.oncokb.oncokb_transcript.ApiException;
import org.oncokb.oncokb_transcript.client.Sequence;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.Constants.DEFAULT_REFERENCE_GENOME;
import static org.mskcc.cbio.oncokb.api.websocket.ValidationCategory.*;
import static org.mskcc.cbio.oncokb.model.StructuralAlteration.TRUNCATING_MUTATIONS;

/**
 * Created by Hongxin on 12/12/16.
 */

@ServerEndpoint(value = "/api/websocket/curation/validation")
public class CurationValidationApiController {

    private Session session;

    @OnOpen
    public void onOpen(Session session) throws IOException {
        // Get session and WebSocket connection
        this.session = session;

        validateHugoSymbols();

        validateGeneInfo();

        validateEmptyClinicalVariants();

        validateEmptyBiologicalVariants();

        validateEvidenceDescriptionInfo();

        validateEvidenceDescriptionHasOutdatedInfo();

        validateAlterationName();

        validateDuplicatedAlteration();

        validateTruncatingMutationsUnderTSG();

        validateMismatchedRefAA();

        validateVariantActionabilityAndOncogenicity();

        compareActionableGenes();

        try {
            this.session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // Handle new messages
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        session.close();
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        // Do error handling here
    }

    private void sendText(String text) {
        try {
            this.session.getBasicRemote().sendText(text);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                this.session.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void validateEmptyClinicalVariants() {
        sendText(generateInfo(MISSING_TREATMENT_INFO, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = ValidationUtils.getMissingTreatmentInfoData();
        if (data.length() == 0) {
            sendText(generateInfo(MISSING_TREATMENT_INFO, ValidationStatus.IS_COMPLETE, new JSONArray()));
        } else {
            sendText(generateInfo(MISSING_TREATMENT_INFO, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateEmptyBiologicalVariants() {
        sendText(generateInfo(MISSING_BIOLOGICAL_ALTERATION_INFO, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = ValidationUtils.getEmptyBiologicalVariants();
        if (data.length() == 0) {
            sendText(generateInfo(MISSING_BIOLOGICAL_ALTERATION_INFO, ValidationStatus.IS_COMPLETE, new JSONArray()));
        } else {
            sendText(generateInfo(MISSING_BIOLOGICAL_ALTERATION_INFO, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateGeneInfo() {
        sendText(generateInfo(MISSING_GENE_INFO, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = ValidationUtils.checkGeneSummaryBackground();
        if (data.length() == 0) {
            sendText(generateInfo(MISSING_GENE_INFO, ValidationStatus.IS_COMPLETE, new JSONArray()));
        } else {
            sendText(generateInfo(MISSING_GENE_INFO, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateEvidenceDescriptionInfo() {
        sendText(generateInfo(INCORRECT_EVIDENCE_DESCRIPTION_FORMAT, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = ValidationUtils.checkEvidenceDescriptionReferenceFormat();
        if (data.length() == 0) {
            sendText(generateInfo(INCORRECT_EVIDENCE_DESCRIPTION_FORMAT, ValidationStatus.IS_COMPLETE, new JSONArray()));
        } else {
            sendText(generateInfo(INCORRECT_EVIDENCE_DESCRIPTION_FORMAT, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateEvidenceDescriptionHasOutdatedInfo() {
        sendText(generateInfo(OUTDATED_INFO_EVIDENCE_DESCRIPTION, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = ValidationUtils.checkEvidenceDescriptionHasOutdatedInfo();
        if (data.length() == 0) {
            sendText(generateInfo(OUTDATED_INFO_EVIDENCE_DESCRIPTION, ValidationStatus.IS_COMPLETE, new JSONArray()));
        } else {
            sendText(generateInfo(OUTDATED_INFO_EVIDENCE_DESCRIPTION, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateAlterationName() {
        sendText(generateInfo(INCORRECT_ALTERATION_NAME_FORMAT, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = ValidationUtils.checkAlterationNameFormat();
        if (data.length() == 0) {
            sendText(generateInfo(INCORRECT_ALTERATION_NAME_FORMAT, ValidationStatus.IS_COMPLETE, new JSONArray()));
        } else {
            sendText(generateInfo(INCORRECT_ALTERATION_NAME_FORMAT, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateMismatchedRefAA() {
        sendText(generateInfo(MISMATCH_REF_AA, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = null;
        try {
            data = getMismatchRefAAData();
            if (data.length() == 0) {
                sendText(generateInfo(MISMATCH_REF_AA, ValidationStatus.IS_COMPLETE, new JSONArray()));
            } else {
                sendText(generateInfo(MISMATCH_REF_AA, ValidationStatus.IS_ERROR, data));
            }
        } catch (ApiException e) {
            data = new JSONArray();
            data.put(ValidationUtils.getErrorMessage("API ERROR", e.getMessage()));
            sendText(generateInfo(MISMATCH_REF_AA, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateDuplicatedAlteration() {
        sendText(generateInfo(DUP_ALTERATION, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = null;
        try {
            data = getDuplicatedAlterations();
            if (data.length() == 0) {
                sendText(generateInfo(DUP_ALTERATION, ValidationStatus.IS_COMPLETE, new JSONArray()));
            } else {
                sendText(generateInfo(DUP_ALTERATION, ValidationStatus.IS_ERROR, data));
            }
        } catch (ApiException e) {
            data = new JSONArray();
            data.put(ValidationUtils.getErrorMessage("API ERROR", e.getMessage()));
            sendText(generateInfo(DUP_ALTERATION, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateTruncatingMutationsUnderTSG() {
        sendText(generateInfo(TRUNCATING_MUTATIONS_NOT_UNDER_TSG, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = null;
        try {
            data = getTruncatingMutationsNotUnderTSG();
            if (data.length() == 0) {
                sendText(generateInfo(TRUNCATING_MUTATIONS_NOT_UNDER_TSG, ValidationStatus.IS_COMPLETE, new JSONArray()));
            } else {
                sendText(generateInfo(TRUNCATING_MUTATIONS_NOT_UNDER_TSG, ValidationStatus.IS_ERROR, data));
            }
        } catch (ApiException e) {
            data = new JSONArray();
            data.put(ValidationUtils.getErrorMessage("API ERROR", e.getMessage()));
            sendText(generateInfo(TRUNCATING_MUTATIONS_NOT_UNDER_TSG, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateHugoSymbols() throws IOException {
        sendText(generateInfo(OUTDATED_HUGO_SYMBOLS, ValidationStatus.IS_PENDING, new JSONArray()));

        Set<Gene> curatedGenesToCheck = CacheUtils.getAllGenes().stream().filter(gene -> gene.getEntrezGeneId() > 0).collect(Collectors.toSet());
        Set<CancerGene> cancerGenesToCheck = CacheUtils.getCancerGeneList().stream().filter(gene -> gene.getEntrezGeneId() > 0).collect(Collectors.toSet());
        Set<Integer> genesToSearch = new HashSet<>();
        genesToSearch.addAll(curatedGenesToCheck.stream().filter(gene -> gene.getEntrezGeneId() > 0).map(Gene::getEntrezGeneId).collect(Collectors.toSet()));
        genesToSearch.addAll(cancerGenesToCheck.stream().map(CancerGene::getEntrezGeneId).collect(Collectors.toSet()));
        OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();

        JSONArray data = null;
        try {
            data = ValidationUtils.validateHugoSymbols(
                curatedGenesToCheck,
                cancerGenesToCheck,
                oncokbTranscriptService.findGenesBySymbols(genesToSearch.stream().map(gene -> gene.toString()).collect(Collectors.toList()))
            );
            if (data.length() == 0) {
                sendText(generateInfo(OUTDATED_HUGO_SYMBOLS, ValidationStatus.IS_COMPLETE, new JSONArray()));
            } else {
                sendText(generateInfo(OUTDATED_HUGO_SYMBOLS, ValidationStatus.IS_ERROR, data));
            }
        } catch (ApiException e) {
            data = new JSONArray();
            data.put(ValidationUtils.getErrorMessage("API ERROR", e.getMessage()));
            sendText(generateInfo(OUTDATED_HUGO_SYMBOLS, ValidationStatus.IS_ERROR, data));
        }
    }

    private void validateVariantActionabilityAndOncogenicity() throws IOException {
        sendText(generateInfo(VARIANT_ACTIONABILITY_AND_ONCOGENICITY, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = null;
        try {
            data = getActionableVariantsNotOncogenic();
            if (data.length() == 0) {
                sendText(generateInfo(VARIANT_ACTIONABILITY_AND_ONCOGENICITY, ValidationStatus.IS_COMPLETE, new JSONArray()));
            } else {
                sendText(generateInfo(VARIANT_ACTIONABILITY_AND_ONCOGENICITY, ValidationStatus.IS_ERROR, data));
            }
        } catch (ApiException e) {
            data = new JSONArray();
            data.put(ValidationUtils.getErrorMessage("API ERROR", e.getMessage()));
            sendText(generateInfo(VARIANT_ACTIONABILITY_AND_ONCOGENICITY, ValidationStatus.IS_ERROR, data));
        }
    }

    private void compareActionableGenes() {
        sendText(generateInfo(ACTIONABLE_INFO, ValidationStatus.IS_PENDING, new JSONArray()));

        JSONArray data = new JSONArray();
        try {
            data = ValidationUtils.compareActionableGene();
            if (data.length() == 0) {
                sendText(generateInfo(ACTIONABLE_INFO, ValidationStatus.IS_COMPLETE, new JSONArray()));
            } else {
                sendText(generateInfo(ACTIONABLE_INFO, ValidationStatus.IS_ERROR, data));
            }
        } catch (IOException e) {
            sendText(generateInfo(ACTIONABLE_INFO, ValidationStatus.IS_ERROR, data));
        }
    }

    private static final String KEY = "key";
    private static final String TYPE = "type";
    private static final String STATUS_KEY = "status";
    private static final String DATA_KEY = "data";

    private static String generateInfo(ValidationCategory test, ValidationStatus status, JSONArray data) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(TYPE, test.getType());
        jsonObject.put(KEY, test.getName());
        jsonObject.put(STATUS_KEY, status);
        jsonObject.put(DATA_KEY, data);
        return jsonObject.toString();
    }

    public JSONArray getMismatchRefAAData() throws ApiException {
        JSONArray data = new JSONArray();
        OncokbTranscriptService oncokbTranscriptService = new OncokbTranscriptService();

        List<org.oncokb.oncokb_transcript.client.Sequence> allGrch37Sequences = oncokbTranscriptService.getAllProteinSequences(ReferenceGenome.GRCh37);
        List<org.oncokb.oncokb_transcript.client.Sequence> allGrch38Sequences = oncokbTranscriptService.getAllProteinSequences(ReferenceGenome.GRCh38);

        for (Alteration alteration : AlterationUtils.getAllAlterations()) {
            if (alteration.getGene().getEntrezGeneId() > 0 && alteration.getProteinStart() >= 0 && alteration.getReferenceGenomes() != null && alteration.getRefResidues() != null) {
                String sequence = "";
                ReferenceGenome referenceGenome = null;
                for (ReferenceGenome ref : alteration.getReferenceGenomes()) {
                    if (ref.equals(ReferenceGenome.GRCh37)) {
                        sequence = getGeneSequenceFromPool(allGrch37Sequences, alteration.getGene().getGrch37Isoform());
                    } else if (ref.equals(ReferenceGenome.GRCh38)) {
                        sequence = getGeneSequenceFromPool(allGrch38Sequences, alteration.getGene().getGrch38Isoform());
                    }
                    if (!StringUtils.isEmpty(sequence)) {
                        referenceGenome = ref;
                        break;
                    }
                }
                String altTargetName = alteration.getName() + " / " + (MainUtils.isVUS(alteration) ? "VUS" : "CURATED");
                if (StringUtils.isEmpty(sequence)) {
                    data.put(ValidationUtils.getErrorMessage(ValidationUtils.getTarget(alteration.getGene().getHugoSymbol(), altTargetName), "No sequence available for " + alteration.getGene().getHugoSymbol()));
                } else if (referenceGenome != null) {
                    if (sequence.length() < alteration.getProteinStart()) {
                        data.put(ValidationUtils.getErrorMessage(ValidationUtils.getTarget(alteration.getGene().getHugoSymbol(), altTargetName), "The gene only has " + sequence.length() + " AAs. But the variant protein start is " + alteration.getProteinStart()));
                    } else if (sequence.length() < alteration.getProteinEnd()) {
                        data.put(ValidationUtils.getErrorMessage(ValidationUtils.getTarget(alteration.getGene().getHugoSymbol(), altTargetName), "The gene only has " + sequence.length() + " AAs. But the variant protein end is " + alteration.getProteinEnd()));
                    } else {
                        String referenceAA = sequence.substring(alteration.getProteinStart() - 1, alteration.getProteinStart() + alteration.getRefResidues().length() - 1);
                        if (!referenceAA.equals(alteration.getRefResidues())) {
                            data.put(ValidationUtils.getErrorMessage(ValidationUtils.getTarget(alteration.getGene().getHugoSymbol(), altTargetName), "The reference amino acid does not match with the curated variant. The expected AA is " + referenceAA));
                        }
                    }
                }
            }
        }
        return data;
    }

    public JSONArray getDuplicatedAlterations() throws ApiException {
        JSONArray data = new JSONArray();

        for (Alteration alteration : AlterationUtils.getAllAlterations()) {
            List<Evidence> evidences = EvidenceUtils.getAlterationEvidences(Collections.singletonList(alteration));
            List<Evidence> evidencesWithoutVus = evidences.stream().filter(evidence -> !EvidenceType.VUS.equals(evidence.getEvidenceType())).collect(Collectors.toList());
            if (evidences.size() != evidencesWithoutVus.size() && evidencesWithoutVus.size() > 0) {
                data.put(ValidationUtils.getErrorMessage(ValidationUtils.getTarget(alteration.getGene().getHugoSymbol(), alteration.getAlteration()), "The alteration is in both mutation and VUS lists."));
            }
        }

        return data;
    }

    public JSONArray getTruncatingMutationsNotUnderTSG() throws ApiException {
        JSONArray data = new JSONArray();

        Set<Gene> genes = new HashSet<>();

        for (Alteration alteration : AlterationUtils.getAllAlterations().stream().filter(alt -> alt.getName().equals(TRUNCATING_MUTATIONS.getVariant())).collect(Collectors.toList())) {
            genes.add(alteration.getGene());
        }

        for (Gene gene : genes) {
            if (!gene.getTSG()) {
                data.put(ValidationUtils.getErrorMessage(ValidationUtils.getTarget(gene.getHugoSymbol()), "The gene " + gene.getHugoSymbol() + " is not tumor suppressor gene but has Truncating Mutations curated."));
            }
        }

        return data;
    }

    public JSONArray getActionableVariantsNotOncogenic() throws ApiException {
        JSONArray data = new JSONArray();
        List<Alteration> alterations = AlterationUtils.getAllAlterations();
        List<String> allowedOncogenicities = new ArrayList<>(Arrays.asList(Oncogenicity.YES.getOncogenic(), Oncogenicity.LIKELY.getOncogenic(), Oncogenicity.RESISTANCE.getOncogenic()));
        for (Alteration alteration : alterations) {
            Query query = new Query(
                null,
                DEFAULT_REFERENCE_GENOME,
                alteration.getGene().getEntrezGeneId(),
                alteration.getGene().getHugoSymbol(),
                alteration.getAlteration(),
                null,
                null,
                null,
                null,
                alteration.getProteinStart(),
                alteration.getProteinEnd(),
                null,
                false,
                "",
                null);
            IndicatorQueryResp response = IndicatorUtils.processQuery(query, null, false,null, false);
            if (!allowedOncogenicities.contains(response.getOncogenic())) {
                if (response.getHighestSensitiveLevel() != null || response.getHighestResistanceLevel() != null) {
                    String hugoSymbol = alteration.getGene().getHugoSymbol();
                    List<IndicatorQueryTreatment> treatments = response.getTreatments();
                    for (IndicatorQueryTreatment treatment: treatments) {
                        for (String altString: treatment.getAlterations()) {
                            TumorType tumorTypeModel = new TumorType(treatment.getLevelAssociatedCancerType());
                            Set<TumorType> excludedTumorTypeModels =  treatment.getLevelExcludedCancerTypes().stream().map(excludedTT -> {
                                return new TumorType(excludedTT);
                            }).collect(Collectors.toSet());
                            String tumorName = TumorTypeUtils.getTumorTypesNameWithExclusion(Collections.singleton(tumorTypeModel), excludedTumorTypeModels);

                            StringBuilder errorMessage = new StringBuilder();
                            errorMessage.append("Is ");
                            errorMessage.append(response.getOncogenic());
                            errorMessage.append(", but has ");
                            errorMessage.append(treatment.getLevel().toString());
                            errorMessage.append(" treatment: ");
                            List<String> drugList = treatment.getDrugs().stream().map(drug -> {
                                return drug.getDrugName();
                            }).collect(Collectors.toList());
                            errorMessage.append(StringUtils.join(drugList, " + "));

                            data.put(ValidationUtils.getErrorMessage(ValidationUtils.getTarget(hugoSymbol, altString, tumorName), errorMessage.toString()));
                        }
                    }
                }
            }
        }
        return data;
    }

    private String getGeneSequenceFromPool(List<Sequence> allSequences, String geneIsoform) {
        if (StringUtils.isEmpty(geneIsoform)) {
            return null;
        }
        Sequence matchedSeq = allSequences.stream().filter(sequence -> sequence.getTranscript().getEnsemblTranscriptId().equals(geneIsoform)).findAny().orElse(null);
        return matchedSeq == null ? null : matchedSeq.getSequence();
    }
}
