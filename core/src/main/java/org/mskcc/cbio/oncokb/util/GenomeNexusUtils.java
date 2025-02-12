package org.mskcc.cbio.oncokb.util;

import org.apache.commons.lang3.StringUtils;
import org.genome_nexus.ApiClient;
import org.genome_nexus.ApiException;
import org.genome_nexus.client.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mskcc.cbio.oncokb.apiModels.TranscriptMatchResult;
import org.mskcc.cbio.oncokb.apiModels.TranscriptPair;
import org.mskcc.cbio.oncokb.apiModels.ensembl.Sequence;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.Gene;
import org.mskcc.cbio.oncokb.model.ReferenceGenome;
import org.mskcc.cbio.oncokb.model.VariantConsequence;
import org.mskcc.cbio.oncokb.model.genomeNexusPreAnnotations.GenomeNexusAnnotatedVariantInfo;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.util.VariantConsequenceUtils.consequenceResolver;

/**
 * Created by Hongxin on 6/26/17.
 */
public class GenomeNexusUtils {

    private static final String MSK_ISOFORM_OVERRIDE = "mskcc";

    private static final String ENSEMBL_37_API_URL = "https://grch37.rest.ensembl.org";
    private static final String ENSEMBL_38_API_URL = "https://rest.ensembl.org";

    private static final String GN_37_URL = "https://www.genomenexus.org";
    private static final String GN_38_URL = "https://grch38.genomenexus.org";
    private static final int GN_READ_TIMEOUT_OVERRIDE = 30000;

    public static String getEnsemblSequencePOSTUrl(ReferenceGenome referenceGenome) {
        return getEnsemblAPIUrl(referenceGenome) + "/sequence/id";
    }

    public static TranscriptMatchResult matchTranscript(TranscriptPair transcript, ReferenceGenome referenceGenome, String hugoSymbol) throws ApiException {
        // Find whether both transcript length are the same
        Optional<EnsemblTranscript> _ensemblTranscript = getEnsemblTranscript(hugoSymbol, transcript);
        TranscriptMatchResult transcriptMatchResult = new TranscriptMatchResult();

        if (_ensemblTranscript.isPresent()) {
            transcriptMatchResult.setOriginalEnsemblTranscript(_ensemblTranscript.get());
            Optional<Sequence> _sequence = Optional.empty();
            if (StringUtils.isNotEmpty(_ensemblTranscript.get().getProteinId())) {
                _sequence = getProteinSequence(transcript.getReferenceGenome(), _ensemblTranscript.get().getProteinId());
            }
            if (_sequence.isPresent()) {
                List<EnsemblTranscript> targetEnsemblTranscripts = getEnsemblTranscriptList(hugoSymbol, referenceGenome);
                if (targetEnsemblTranscripts.size() == 0) {
                    transcriptMatchResult.setNote("The target reference genome does not have any ensembl transcripts.");
                } else {
                    try {
                        pickEnsemblTranscript(transcriptMatchResult, referenceGenome, targetEnsemblTranscripts, _sequence.get());
                    } catch (Exception exception) {
                        transcriptMatchResult.setNote(exception.getMessage());
                    }
                }
            } else {
                transcriptMatchResult.setNote("No protein sequence available for transcript " + _ensemblTranscript.get().getTranscriptId());
            }
        } else {
            transcriptMatchResult.setNote("The transcript is invalid");
        }
        return transcriptMatchResult;
    }

    private static ApiClient getGNApiClient(String url) {
        ApiClient client = new ApiClient();
        client.setReadTimeout(GN_READ_TIMEOUT_OVERRIDE);
        client.setBasePath(url);
        return client;
    }

    private static EnsemblControllerApi getGNEnsemblControllerApi(String url) {
        return new EnsemblControllerApi(getGNApiClient(url));
    }

    private static AnnotationControllerApi getGNAnnotationControllerApi(String url) {
        return new AnnotationControllerApi(getGNApiClient(url));
    }

    private static String getGenomeNexusUrl(ReferenceGenome referenceGenome) {
        switch (referenceGenome) {
            case GRCh37:
                String grch37Url = PropertiesUtils.getProperties("genome_nexus.grch37.url");
                return StringUtils.isEmpty(grch37Url) ? GN_37_URL : grch37Url;
            case GRCh38:
                String grch38Url = PropertiesUtils.getProperties("genome_nexus.grch38.url");
                return StringUtils.isEmpty(grch38Url) ? GN_38_URL : grch38Url;
            default:
                return null;
        }
    }

    private static EnsemblControllerApi getEnsemblControllerApi(ReferenceGenome referenceGenome) {
        switch (referenceGenome) {
            case GRCh37:
            case GRCh38:
                return getGNEnsemblControllerApi(getGenomeNexusUrl(referenceGenome));
            default:
                return new EnsemblControllerApi();
        }
    }

    private static AnnotationControllerApi getAnnotationControllerApi(ReferenceGenome referenceGenome) {
        switch (referenceGenome) {
            case GRCh37:
            case GRCh38:
                return getGNAnnotationControllerApi(getGenomeNexusUrl(referenceGenome));
            default:
                return new AnnotationControllerApi();
        }
    }

    private static String getEnsemblSequenceGETUrl(ReferenceGenome referenceGenome, String transcript) {
        return getEnsemblAPIUrl(referenceGenome) + "/sequence/id/" + transcript;
    }

    public static String getEnsemblAPIUrl(ReferenceGenome referenceGenome) {
        switch (referenceGenome) {
            case GRCh37:
                return ENSEMBL_37_API_URL;
            case GRCh38:
                return ENSEMBL_38_API_URL;
            default:
                return "";
        }
    }

    public static TranscriptConsequenceSummary getTranscriptConsequence(GNVariantAnnotationType type, String query, ReferenceGenome referenceGenome) throws ApiException {
        List<VariantAnnotation> annotations = new ArrayList<>();
        if (type == GNVariantAnnotationType.GENOMIC_LOCATION) {
            annotations.addAll(getGenomicLocationVariantsAnnotation(Collections.singletonList(GenomeNexusUtils.convertGenomicLocation(query)), referenceGenome));
        } else if (type == GNVariantAnnotationType.HGVS_G) {
            annotations.addAll(getHgvsVariantsAnnotation(Collections.singletonList(query), referenceGenome));
        }
        List<TranscriptConsequenceSummary> map = getTranscriptsConsequence(annotations, referenceGenome);
        if (map.isEmpty()) {
            return null;
        } else {
            return map.get(0);
        }
    }

    public static List<TranscriptConsequenceSummary> getTranscriptsConsequence(List<VariantAnnotation> annotations, ReferenceGenome referenceGenome) throws ApiException {
        List<TranscriptConsequenceSummary> result = new ArrayList<>();
        for (VariantAnnotation annotation : annotations) {
            result.add(getConsequence(annotation, referenceGenome));
        }
        return result;
    }

    public static List<GenomeNexusAnnotatedVariantInfo> getAnnotatedVariantsFromGenomeNexus(GNVariantAnnotationType type, List<VariantAnnotation> variantsAnnotation, ReferenceGenome referenceGenome) throws ApiException {
        List<GenomeNexusAnnotatedVariantInfo> result = new ArrayList<>();

        for (VariantAnnotation annotation : variantsAnnotation) {
            String query = annotation.getOriginalVariantQuery();

            if (StringUtils.isEmpty(query) || StringUtils.isEmpty(query.replace(",", ""))) {
                result.add(null);
            }

            GenomeNexusAnnotatedVariantInfo preAnnotatedVariantInfo = new GenomeNexusAnnotatedVariantInfo();
            preAnnotatedVariantInfo.setOriginalVariantQuery(query);
            preAnnotatedVariantInfo.setReferenceGenome(referenceGenome);

            if (annotation != null) {
                // Use original query for HGVSg/Genomic Location.
                if (annotation.isSuccessfullyAnnotated()) {
                    if (type == GNVariantAnnotationType.HGVS_G) {
                        preAnnotatedVariantInfo.setHgvsg(query);
                    }
                    if (type == GNVariantAnnotationType.GENOMIC_LOCATION) {
                        preAnnotatedVariantInfo.setGenomicLocation(query);
                    }
                }

                // Use Genome Nexus annotation to translate original query to HGVSg or Genomic Location
                if (type == GNVariantAnnotationType.GENOMIC_LOCATION && StringUtils.isNotEmpty(annotation.getHgvsg())) {
                    preAnnotatedVariantInfo.setHgvsg(annotation.getHgvsg());
                }

                if (type == GNVariantAnnotationType.HGVS_G) {
                    if (annotation.getAnnotationSummary() != null && annotation.getAnnotationSummary().getGenomicLocation() != null) {
                        org.genome_nexus.client.GenomicLocation gl = annotation.getAnnotationSummary().getGenomicLocation();
                        if (gl.getChromosome() != null && gl.getStart() != null && gl.getEnd() != null && gl.getReferenceAllele() != null && gl.getVariantAllele() != null) {
                            String glString = gl.getChromosome() + "," + gl.getStart() + "," + gl.getEnd() + "," + gl.getReferenceAllele() + "," + gl.getVariantAllele();
                            preAnnotatedVariantInfo.setGenomicLocation(glString);
                        }
                    }
                }

            }

            TranscriptConsequenceSummary transcriptConsequenceSummary = getConsequence(annotation, referenceGenome);
            if (transcriptConsequenceSummary != null) {
                preAnnotatedVariantInfo.setHugoSymbol(transcriptConsequenceSummary.getHugoGeneSymbol());
                Integer entrezGeneId = StringUtils.isNumeric(transcriptConsequenceSummary.getEntrezGeneId()) ? Integer.parseInt(transcriptConsequenceSummary.getEntrezGeneId()) : null;
                preAnnotatedVariantInfo.setEntrezGeneId(entrezGeneId);
                preAnnotatedVariantInfo.setHgvspShort(transcriptConsequenceSummary.getHgvspShort());
                if (transcriptConsequenceSummary.getProteinPosition() != null) {
                    preAnnotatedVariantInfo.setProteinStart(transcriptConsequenceSummary.getProteinPosition().getStart());
                }
                if (transcriptConsequenceSummary.getProteinPosition() != null) {
                    preAnnotatedVariantInfo.setProteinEnd(transcriptConsequenceSummary.getProteinPosition().getEnd());
                }
                preAnnotatedVariantInfo.setConsequenceTerms(transcriptConsequenceSummary.getConsequenceTerms());
            }

            result.add(preAnnotatedVariantInfo);
        }
        return result;
    }

    private static String getIsoform(Gene gene, ReferenceGenome referenceGenome) {
        if (gene == null) {
            return null;
        }
        switch (referenceGenome) {
            case GRCh37:
                return gene.getGrch37Isoform();
            case GRCh38:
                return gene.getGrch38Isoform();
            default:
                return "";
        }
    }

    public static String chromosomeNormalizer(String chromosome) {
        if (StringUtils.isEmpty(chromosome)) {
            return chromosome;
        }
        return chromosome.trim().replace("chr", "").replace("23", "X").replace("24", "Y");
    }

    public static GenomicLocation convertGenomicLocation(String genomicLocation) {
        if (genomicLocation == null) {
            return null;
        }
        String[] parts = genomicLocation.split(",");
        GenomicLocation location = null;
        if (parts.length >= 5) {
            // trim all parts
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
            }
            location = new GenomicLocation();
            location.setChromosome(chromosomeNormalizer(parts[0]));
            try {
                location.setStart(parts[1].isEmpty() ? null : Integer.parseInt(parts[1]));
            } catch (NumberFormatException e) {
                location.setStart(null);
            }
            try {
                location.setEnd(parts[2].isEmpty() ? null : Integer.parseInt(parts[2]));
            } catch (NumberFormatException e) {
                location.setEnd(null);
            }
            location.setReferenceAllele(parts[3]);
            location.setVariantAllele(parts[4]);
        }
        return location;
    }

    public static String convertGenomicLocation(GenomicLocation genomicLocation) {
        if (genomicLocation == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add(Optional.ofNullable(genomicLocation.getChromosome()).orElse(""));
        parts.add(Optional.ofNullable(genomicLocation.getStart()).map(String::valueOf).orElse(""));
        parts.add(Optional.ofNullable(genomicLocation.getEnd()).map(String::valueOf).orElse(""));
        parts.add(Optional.ofNullable(genomicLocation.getReferenceAllele()).orElse(""));
        parts.add(Optional.ofNullable(genomicLocation.getVariantAllele()).orElse(""));
        return String.join(",", parts);
    }

    public static List<VariantAnnotation> getHgvsVariantsAnnotation(List<String> queries, ReferenceGenome referenceGenome) throws ApiException {
        List<VariantAnnotation> variantsAnnotation = new ArrayList<>();
        if (queries != null) {
            List<String> gnFields = new ArrayList<>();
            gnFields.add("annotation_summary");
            variantsAnnotation = getAnnotationControllerApi(referenceGenome).fetchVariantAnnotationPOST(queries, MSK_ISOFORM_OVERRIDE, null, gnFields);
        }
        return variantsAnnotation;
    }

    public static List<VariantAnnotation> getGenomicLocationVariantsAnnotation(List<GenomicLocation> queries, ReferenceGenome referenceGenome) throws ApiException {
        List<VariantAnnotation> variantsAnnotation = new ArrayList<>();
        if (queries != null) {
            List<String> gnFields = new ArrayList<>();
            gnFields.add("annotation_summary");
            variantsAnnotation = getAnnotationControllerApi(referenceGenome).fetchVariantAnnotationByGenomicLocationPOST(queries, MSK_ISOFORM_OVERRIDE, null, gnFields);
        }
        return variantsAnnotation;
    }

    private static TranscriptConsequenceSummary getConsequence(VariantAnnotation variantAnnotation, ReferenceGenome referenceGenome) {
        List<TranscriptConsequenceSummary> summaries = new ArrayList<>();

        if (variantAnnotation == null || variantAnnotation.getAnnotationSummary() == null || variantAnnotation.getAnnotationSummary().getTranscriptConsequenceSummaries() == null) {
            return null;
        }

        if (variantAnnotation.getAnnotationSummary() != null && variantAnnotation.getAnnotationSummary().getTranscriptConsequenceSummaries() != null) {
            for (TranscriptConsequenceSummary consequenceSummary : variantAnnotation.getAnnotationSummary().getTranscriptConsequenceSummaries()) {
                Integer entrezGeneId = null;
                if (StringUtils.isNotEmpty(consequenceSummary.getEntrezGeneId())) {
                    entrezGeneId = Integer.parseInt(consequenceSummary.getEntrezGeneId());
                }
                String hugoSymbol = null;
                if (StringUtils.isNotEmpty(consequenceSummary.getHugoGeneSymbol())) {
                    hugoSymbol = consequenceSummary.getHugoGeneSymbol();
                }

                if (StringUtils.isNotEmpty(consequenceSummary.getTranscriptId())) {
                    Gene gene = GeneUtils.getGene(entrezGeneId, hugoSymbol);
                    String isoform = getIsoform(gene, referenceGenome);
                    if (gene != null && (StringUtils.isEmpty(isoform) || isoform.equals(consequenceSummary.getTranscriptId()))) {
                        summaries.add(consequenceSummary);
                    }
                }
            }
        }

        TranscriptConsequenceSummary summary;
        // only one transcript marked as canonical
        if (summaries.size() == 1) {
            summary = summaries.iterator().next();
        } else if (summaries.size() > 1) {
            summary = pickTranscriptConsequenceSummary(summaries, variantAnnotation.getAnnotationSummary().getCanonicalTranscriptId(), variantAnnotation.getMostSevereConsequence());
        }
        // no transcript marked as canonical (list.size() == 0), use most sever consequence to decide which one to pick among all available
        else {
            summary = pickTranscriptConsequenceSummary(variantAnnotation.getAnnotationSummary().getTranscriptConsequenceSummaries(), variantAnnotation.getAnnotationSummary().getCanonicalTranscriptId(), variantAnnotation.getMostSevereConsequence());
        }

        // Only return one consequence term
        if (summary != null) {
            VariantConsequence consequence = consequenceResolver(summary.getConsequenceTerms(), summary.getVariantClassification());
            if (consequence == null && StringUtils.isNotEmpty(summary.getVariantClassification())) {
                consequence = VariantConsequenceUtils.findVariantConsequenceByTerm(summary.getVariantClassification());
            }
            summary.setConsequenceTerms(consequence == null ? "" : consequence.getTerm());
        }
        return summary;
    }

    private static TranscriptConsequenceSummary pickTranscriptConsequenceSummary(List<TranscriptConsequenceSummary> summaries, String canonicalTranscript, String mostSevereConsequence) {
        if (summaries == null) {
            return null;
        }
        // Find canonical isoforms first
        List<TranscriptConsequenceSummary> canonicalTranscripts = summaries.stream().filter(summary -> StringUtils.isNotEmpty(summary.getTranscriptId()) && summary.getTranscriptId().equals(canonicalTranscript)).collect(Collectors.toList());

        if (StringUtils.isNotEmpty(mostSevereConsequence)) {
            canonicalTranscripts = canonicalTranscripts.stream().filter(summary -> StringUtils.isNotEmpty(summary.getConsequenceTerms()) && Arrays.asList(summary.getConsequenceTerms().split(",")).contains(mostSevereConsequence)).collect(Collectors.toList());
        }

        return canonicalTranscripts.size() > 0 ? canonicalTranscripts.get(0) : null;
    }

    public static List<EnsemblTranscript> getEnsemblTranscriptList(List<String> ensembelTranscriptIds, ReferenceGenome referenceGenome) throws ApiException {
        EnsemblControllerApi controllerApi = GenomeNexusUtils.getEnsemblControllerApi(referenceGenome);
        EnsemblFilter ensemblFilter = new EnsemblFilter();
        ensemblFilter.setTranscriptIds(ensembelTranscriptIds);
        return controllerApi.fetchEnsemblTranscriptsByEnsemblFilterPOST(ensemblFilter);
    }

    private static List<EnsemblTranscript> getEnsemblTranscriptList(String hugoSymbol, ReferenceGenome referenceGenome) throws ApiException {
        EnsemblControllerApi controllerApi = GenomeNexusUtils.getEnsemblControllerApi(referenceGenome);
        Set<EnsemblTranscript> transcripts = new LinkedHashSet<>();
        EnsemblTranscript canonicalTranscript = getCanonicalEnsemblTranscript(hugoSymbol, referenceGenome);
        if (canonicalTranscript != null) {
            transcripts.add(canonicalTranscript);
        }
        transcripts.addAll(controllerApi.fetchEnsemblTranscriptsGET(null, null, hugoSymbol));
        return new ArrayList<>(transcripts);
    }

    public static EnsemblTranscript getCanonicalEnsemblTranscript(String hugoSymbol, ReferenceGenome referenceGenome) throws ApiException {
        EnsemblControllerApi controllerApi = GenomeNexusUtils.getEnsemblControllerApi(referenceGenome);
        try {
            return controllerApi.fetchCanonicalEnsemblTranscriptByHugoSymbolGET(hugoSymbol, MSK_ISOFORM_OVERRIDE);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.NOT_FOUND.value()) {
                return null;
            } else {
                throw e;
            }
        }
    }

    private static Optional<EnsemblTranscript> getEnsemblTranscript(String hugoSymbol, TranscriptPair transcriptPair) throws ApiException {
        return getEnsemblTranscriptList(hugoSymbol, transcriptPair.getReferenceGenome()).stream().filter(ensemblTranscript -> !StringUtils.isEmpty(ensemblTranscript.getTranscriptId()) && ensemblTranscript.getTranscriptId().equalsIgnoreCase(transcriptPair.getTranscript())).findFirst();
    }

    private static Optional<Sequence> getProteinSequence(ReferenceGenome referenceGenome, String transcript) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(httpHeaders);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Sequence> response = restTemplate.exchange(
            GenomeNexusUtils.getEnsemblSequenceGETUrl(referenceGenome, transcript), HttpMethod.GET, entity, Sequence.class);
        return Optional.of(response.getBody());
    }

    private static List<Sequence> getProteinSequences(ReferenceGenome referenceGenome, List<String> transcripts) {
        if (transcripts.size() == 0) {
            return new ArrayList<>();
        }
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        transcripts.stream().forEach(transcript -> jsonArray.put(transcript));
        try {
            jsonObject.put("ids", jsonArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonObject.toString(), httpHeaders);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Sequence[]> response = restTemplate.postForEntity(
            GenomeNexusUtils.getEnsemblSequencePOSTUrl(referenceGenome), entity, Sequence[].class);
        return Arrays.asList(response.getBody());
    }

    private static TranscriptMatchResult pickEnsemblTranscript(TranscriptMatchResult transcriptMatchResult, ReferenceGenome referenceGenome, List<EnsemblTranscript> availableTranscripts, Sequence sequence) {
        List<EnsemblTranscript> sameLengthList = availableTranscripts.stream().filter(ensemblTranscript -> ensemblTranscript.getProteinLength() != null && ensemblTranscript.getProteinLength().equals(sequence.getSeq().length())).collect(Collectors.toList());

        List<Sequence> sequences = getProteinSequences(referenceGenome, sameLengthList.stream().map(EnsemblTranscript::getProteinId).collect(Collectors.toList())).stream().filter(filteredSequence -> filteredSequence.getSeq().length() == sequence.getSeq().length()).collect(Collectors.toList());
        Optional<Sequence> sequenceSame = sequences.stream().filter(matchedSequence -> matchedSequence.getSeq().equals(sequence.getSeq())).findAny();


        if (sequenceSame.isPresent()) {
            Optional<EnsemblTranscript> ensemblTranscript = getEnsemblTranscriptBySequence(sameLengthList, sequenceSame.get());
            transcriptMatchResult.setTargetEnsemblTranscript(ensemblTranscript.get());
            transcriptMatchResult.setNote("Same sequence");
        } else if (sequences.size() > 0) {
            // We should make some comparison with the original sequence for the same length
            sequences.sort(Comparator.comparingInt(s -> getNumOfMismatchSameLengthSequences(sequence.getSeq(), s.getSeq())));
            Sequence pickedSequence = sequences.iterator().next();

            Optional<EnsemblTranscript> ensemblTranscript = getEnsemblTranscriptBySequence(availableTranscripts, pickedSequence);
            transcriptMatchResult.setTargetEnsemblTranscript(ensemblTranscript.get());
            transcriptMatchResult.setNote("Same length, but mismatch: " + getNumOfMismatchSameLengthSequences(sequence.getSeq(), pickedSequence.getSeq()));
        } else {
            // we want to see whether there is any transcript includes the original sequence
            List<EnsemblTranscript> longerOnes = availableTranscripts.stream().filter(ensemblTranscript -> ensemblTranscript.getProteinLength() != null && ensemblTranscript.getProteinLength() > sequence.getSeq().length()).collect(Collectors.toList());

            List<Sequence> longerSequences = getProteinSequences(referenceGenome, longerOnes.stream().map(EnsemblTranscript::getProteinId).collect(Collectors.toList()));
            List<Sequence> sequencesContains = longerSequences.stream().filter(matchedSequence -> matchedSequence.getSeq().contains(sequence.getSeq())).collect(Collectors.toList());
            sequencesContains.sort((s1, s2) -> s2.getSeq().length() - s1.getSeq().length());

            if (sequencesContains.size() > 0) {
                Sequence pickedSequence = sequencesContains.iterator().next();
                Optional<EnsemblTranscript> ensemblTranscript = getEnsemblTranscriptBySequence(longerOnes, pickedSequence);
                transcriptMatchResult.setTargetEnsemblTranscript(ensemblTranscript.get());
                transcriptMatchResult.setNote("Longer one found, length: " + ensemblTranscript.get().getProteinLength());

            } else {
                transcriptMatchResult.setNote("No matched sequence found");
            }
        }
        return transcriptMatchResult;
    }

    private static Optional<EnsemblTranscript> getEnsemblTranscriptBySequence(List<EnsemblTranscript> availableEnsemblTranscripts, Sequence sequence) {
        return availableEnsemblTranscripts.stream().filter(ensemblTranscript -> {
            if (ensemblTranscript.getProteinId() != null && ensemblTranscript.getProteinId().equals(sequence.getId())) {
                return true;
            } else {
                return false;
            }
        }).findAny();
    }

    private static int getNumOfMismatchSameLengthSequences(String reference, String newSequence) {
        int mismatch = 0;
        for (int i = 0; i < reference.length(); i++) {
            char r = reference.charAt(i);
            char n = newSequence.charAt(i);
            if (r != n) {
                mismatch++;
            }
        }
        return mismatch;
    }
}
