package org.mskcc.cbio.oncokb.util;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.genome_nexus.ApiException;
import org.genome_nexus.client.TranscriptConsequenceSummary;
import org.genome_nexus.client.VariantAnnotation;
import org.mskcc.cbio.oncokb.apiModels.annotation.AnnotateMutationByGenomicChangeQuery;
import org.mskcc.cbio.oncokb.apiModels.annotation.AnnotateMutationByHGVSQuery;
import org.mskcc.cbio.oncokb.bo.AlterationBo;
import org.mskcc.cbio.oncokb.bo.EvidenceBo;
import org.mskcc.cbio.oncokb.genomenexus.GNVariantAnnotationType;
import org.mskcc.cbio.oncokb.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.Constants.*;
import static org.mskcc.cbio.oncokb.model.StructuralAlteration.TRUNCATING_MUTATIONS;
import static org.mskcc.cbio.oncokb.util.MainUtils.isOncogenic;
import static org.mskcc.cbio.oncokb.util.VariantConsequenceUtils.TOO_BROAD_CONSEQUENCES;

/**
 * @author jgao, Hongxin Zhang
 */
public final class AlterationUtils {
    private static List<String> oncogenicList = Arrays.asList(new String[]{
        "", Oncogenicity.INCONCLUSIVE.getOncogenic(), Oncogenicity.LIKELY_NEUTRAL.getOncogenic(),
        Oncogenicity.LIKELY.getOncogenic(), Oncogenicity.YES.getOncogenic()});

    private static AlterationBo alterationBo = ApplicationContextSingleton.getAlterationBo();
    private static Pattern COMPLEX_MISSENSE_ONE = Pattern.compile("([A-Z])([0-9]+)_([A-Z])([0-9]+)delins([A-Z]+)");
    private static Pattern COMPLEX_MISSENSE_TWO = Pattern.compile("([A-Z]+)([0-9]+)([A-Z]+)");

    // We do not intend to do comprehensive checking, but only eliminate some basic errors.
    // GenomeNexus will evaluate it further
    public static Pattern HGVSG_FORMAT = Pattern.compile("((grch37|grch38):)?((chr)?[\\dxy]+:g\\.\\d+.*)", Pattern.CASE_INSENSITIVE);
    public static Pattern HGVSC_FORMAT = Pattern.compile("((grch37|grch38):)?(ENST\\d+(\\.\\d+)?:c\\.\\d+.*)", Pattern.CASE_INSENSITIVE);
    public static Pattern GENOMIC_CHANGE_FORMAT = Pattern.compile("((grch37|grch38):)?([\\dxy]+,\\d+,\\d+,.*)", Pattern.CASE_INSENSITIVE);

    private static String EXCLUSION_SEPERATOR_REGEX = "\\s*;\\s*";


    private AlterationUtils() {
        throw new AssertionError();
    }

    public static boolean consequenceRelated(VariantConsequence consequence, VariantConsequence compareTo) {
        if (consequence == null || compareTo == null) {
            return consequence == compareTo;
        }
        if (SPLICE_SITE_VARIANTS.contains(consequence)) {
            return SPLICE_SITE_VARIANTS.contains(compareTo);
        } else {
            return consequence.equals(compareTo);
        }
    }

    public static boolean isValidHgvsg(String hgvsg) {
        hgvsg = hgvsg == null ? null : hgvsg.trim();
        if (StringUtils.isEmpty(hgvsg)) {
            return false;
        }
        return HGVSG_FORMAT.matcher(hgvsg).matches();
    }

    public static boolean isValidHgvsc(String hgvsc) {
        hgvsc = hgvsc == null ? null : hgvsc.trim();
        if (StringUtils.isEmpty(hgvsc)) {
            return false;
        }
        return HGVSC_FORMAT.matcher(hgvsc).matches();
    }

    public static boolean isValidHgvs(String hgvs) {
        return isValidHgvsg(hgvs) || isValidHgvsc(hgvs);
    }

    public static boolean isValidGenomicChange(String query) {
        query = query == null ? null : query.trim();
        if (StringUtils.isEmpty(query)) {
            return false;
        }
        return GENOMIC_CHANGE_FORMAT.matcher(query).matches();
    }

    public static boolean isRangeInframeAlteration(Alteration alteration) {
        boolean isInframeAlteration = isInframeAlteration(alteration);
        if (!isInframeAlteration) {
            return false;
        }

        Pattern p = Pattern.compile("([0-9]+)_([0-9]+)(ins|del)(.*)");
        Matcher m = p.matcher(alteration.getAlteration());
        return m.matches();
    }

    public static boolean isInframeAlteration(Alteration alteration) {
        if (alteration == null || alteration.getConsequence() == null) {
            return false;
        }
        return alteration.getConsequence().equals(VariantConsequenceUtils.findVariantConsequenceByTerm(IN_FRAME_INSERTION)) || alteration.getConsequence().equals(VariantConsequenceUtils.findVariantConsequenceByTerm(IN_FRAME_DELETION));
    }

    public static Set<Alteration> findOverlapAlteration(List<Alteration> alterations, Gene gene, ReferenceGenome referenceGenome, VariantConsequence consequence, int start, int end, String proteinChange) {
        Set<Alteration> overlaps = new HashSet<>();
        VariantConsequence inframeDeletionConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(IN_FRAME_DELETION);
        for (int i = 0; i < alterations.size(); i++) {
            Alteration alteration = alterations.get(i);
            if (alteration.getGene().equals(gene) && alteration.getConsequence() != null && consequenceRelated(consequence, alteration.getConsequence()) && (referenceGenome == null || alteration.getReferenceGenomes().contains(referenceGenome))) {
                //For alteration without specific position, do not do intersection
                if (start <= AlterationPositionBoundary.START.getValue() || end >= AlterationPositionBoundary.END.getValue()) {
                    if (start >= alteration.getProteinStart()
                        && end <= alteration.getProteinEnd()) {
                        overlaps.add(alteration);
                    }
                } else if (end >= alteration.getProteinStart()
                    && start <= alteration.getProteinEnd()) {
                    //For variant, as long as they are overlapped to each, return the alteration
                    overlaps.add(alteration);
                }
            }
        }
        return overlaps;
    }

    private static Matcher getExclusionCriteriaMatcher(String proteinChange) {
        if (proteinChange == null) {
            proteinChange = "";
        }
        Pattern exclusionPatter = Pattern.compile("(.*)[\\{\\(]\\s*(exclude|excluding)(.*)[\\}\\)](.*)?", Pattern.CASE_INSENSITIVE);
        Matcher exclusionMatch = exclusionPatter.matcher(proteinChange);
        return exclusionMatch;
    }

    public static String removeExclusionCriteria(String proteinChange) {
        Matcher exclusionMatch = getExclusionCriteriaMatcher(proteinChange);
        if (exclusionMatch.matches()) {
            proteinChange = exclusionMatch.group(1).trim();
        }
        return proteinChange;
    }

    public static Set<Alteration> getExclusionAlterations(String proteinChange) {
        Set<Alteration> exclusionAlterations = new HashSet<>();
        Matcher exclusionMatcher = getExclusionCriteriaMatcher(proteinChange);
        if (exclusionMatcher.matches()) {
            String excludedStr = exclusionMatcher.group(3).trim();
            exclusionAlterations.addAll(parseMutationString(excludedStr, ";"));
        }
        return exclusionAlterations;
    }

    public static boolean hasExclusionCriteria(String proteinChange) {
        Matcher exclusionMatch = getExclusionCriteriaMatcher(proteinChange);
        return exclusionMatch.matches();
    }

    public static String trimComment(String mutationStr) {
        if (StringUtils.isEmpty(mutationStr)) {
            return "";
        }
        return mutationStr.replaceAll("\\([\\s\\S]*?\\)", "").trim();
    }

    public static List<Alteration> parseMutationString(String mutationStr, String mutationSeparator) {
        List<Alteration> ret = new ArrayList<>();

        mutationStr = trimComment(mutationStr);

        String[] parts = mutationStr.split(mutationSeparator);

        Pattern p = Pattern.compile("([A-Z][0-9]+)([^0-9/]+/.+)", Pattern.CASE_INSENSITIVE);
        Pattern rgp = Pattern.compile("(((grch37)|(grch38)):\\s*).*", Pattern.CASE_INSENSITIVE);
        for (String part : parts) {
            String proteinChange, displayName;
            part = part.trim();

            Matcher rgm = rgp.matcher(part);
            Set<ReferenceGenome> referenceGenomes = new HashSet<>();
            if (rgm.find()) {
                String referenceGenome = rgm.group(2);
                ReferenceGenome matchedReferenceGenome = MainUtils.searchEnum(ReferenceGenome.class, referenceGenome);
                if (matchedReferenceGenome != null) {
                    referenceGenomes.add(matchedReferenceGenome);
                }
                part = part.replace(rgm.group(1), "");
            } else {
                referenceGenomes.add(ReferenceGenome.GRCh37);
                referenceGenomes.add(ReferenceGenome.GRCh38);
            }

            if (part.contains("[")) {
                int l = part.indexOf("[");
                int r = part.indexOf("]");
                proteinChange = part.substring(0, l).trim();
                displayName = part.substring(l + 1, r).trim();
            } else {
                proteinChange = part;
                displayName = part;
                if (displayName.contains("{")) {
                    int left = displayName.indexOf("{");
                    int right = displayName.indexOf("}");
                    if (left > 0 && right > 0) {
                        String exclusion = displayName.substring(left + 1, right);
                        exclusion = MainUtils.replaceLast(exclusion, EXCLUSION_SEPERATOR_REGEX, " and ");
                        exclusion = exclusion.replaceAll(EXCLUSION_SEPERATOR_REGEX, ", ");

                        displayName = displayName.substring(0, left) + "(" + exclusion + ")" + displayName.substring(right + 1);
                    }
                }
            }
            proteinChange = trimComment(proteinChange);
            proteinChange = proteinChange.replaceAll(EXCLUSION_SEPERATOR_REGEX, "; ");

            Matcher m = p.matcher(proteinChange);
            if (m.find()) {
                String ref = m.group(1);
                for (String var : m.group(2).split("/")) {
                    Alteration alteration = new Alteration();
                    alteration.setAlteration(ref + var);
                    alteration.setName(ref + var);
                    alteration.setReferenceGenomes(referenceGenomes);
                    ret.add(alteration);
                }
            } else {
                Alteration alteration = new Alteration();
                alteration.setAlteration(proteinChange);
                alteration.setName(displayName);
                alteration.setReferenceGenomes(referenceGenomes);
                ret.add(alteration);
            }
        }
        return ret.stream().map(alteration -> {
            annotateAlteration(alteration, alteration.getAlteration());
            return alteration;
        }).collect(Collectors.toList());
    }

    public static FrameshiftVariant parseFrameshiftVariant(String proteinChange) {
        if (StringUtils.isEmpty(proteinChange)) {
            return null;
        }
        Pattern p = Pattern.compile("([A-Z\\*]+)?([0-9]+)([A-Z])?fs(\\*([0-9]+|\\?)?)?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(proteinChange);
        if (m.matches()) {
            FrameshiftVariant variant = new FrameshiftVariant();
            variant.setRefResidues(m.group(1) == null ? "" : m.group(1).toUpperCase());
            variant.setProteinStart(Integer.valueOf(m.group(2)));
            variant.setProteinEnd(variant.getProteinStart());
            variant.setVariantResidues(m.group(3) == null ? "" : m.group(3).toUpperCase());
            variant.setExtension(m.group(5) == null ? "" : m.group(5).toUpperCase());
            return variant;
        } else {
            return null;
        }
    }

    public static boolean isSameFrameshiftVariant(FrameshiftVariant variant1, FrameshiftVariant variant2) {
        if (variant1 == null || variant2 == null) {
            return false;
        }
        if (variant1.getProteinStart() != null && variant2.getProteinStart() != null && !variant1.getProteinStart().equals(variant2.getProteinStart())) {
            return false;
        }
        if (variant1.getProteinEnd() != null && variant2.getProteinEnd() != null && !variant1.getProteinEnd().equals(variant2.getProteinEnd())) {
            return false;
        }
        if (StringUtils.isNotEmpty(variant1.getRefResidues()) && StringUtils.isNotEmpty(variant2.getRefResidues()) && !variant1.getRefResidues().equals(variant2.getRefResidues())) {
            return false;
        }
        if (StringUtils.isNotEmpty(variant1.getVariantResidues()) && StringUtils.isNotEmpty(variant2.getVariantResidues()) && !variant1.getVariantResidues().equals(variant2.getVariantResidues())) {
            return false;
        }
        if (StringUtils.isEmpty(variant1.getExtension()) || StringUtils.isEmpty(variant2.getExtension())) {
            if (StringUtils.isEmpty(variant1.getExtension()) & StringUtils.isEmpty(variant2.getExtension())) {
                return true;
            }
            return false;
        } else {
            return variant1.getExtension().equals(variant2.getExtension());
        }
    }

    public static void annotateAlteration(Alteration alteration, String proteinChange) {
        String consequence = "NA";
        String ref = null;
        String var = null;
        Integer start = AlterationPositionBoundary.START.getValue();
        Integer end = AlterationPositionBoundary.END.getValue();

        if (alteration == null) {
            return;
        }

        if (proteinChange == null) {
            proteinChange = "";
        }

        if (proteinChange.startsWith("p.")) {
            proteinChange = proteinChange.substring(2);
        }

        if (proteinChange.indexOf("[") != -1) {
            proteinChange = proteinChange.substring(0, proteinChange.indexOf("["));
        }

        // we need to deal with the exclusion format so the protein change can properly be interpreted.
        String excludedStr = "";
        Matcher exclusionMatch = getExclusionCriteriaMatcher(proteinChange);
        if (exclusionMatch.matches()) {
            proteinChange = exclusionMatch.group(1);
            excludedStr = exclusionMatch.group(3).trim();
        }

        proteinChange = proteinChange.trim();

        Pattern p = Pattern.compile("([A-Z]?)([0-9]+)(_[A-Z]?([0-9]+))?(delins|ins|del)([A-Z0-9\\*]*)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(proteinChange);
            if (m.matches()) {
                if (m.group(1) != null && m.group(3) == null) {
                    // we only want to specify reference when it's one position ins/del
                    ref = m.group(1).toUpperCase();
                }
                start = Integer.valueOf(m.group(2));
                if (m.group(4) != null) {
                    end = Integer.valueOf(m.group(4));
                } else {
                    end = start;
                }
                String type = m.group(5);
                if (type.equals("ins")) {
                    consequence = IN_FRAME_INSERTION;
                } else if (type.equals("del")) {
                    consequence = IN_FRAME_DELETION;
                } else {
                    // this will be delins, it requires AA after delins to be specified, otherwise, you won't be able to know its consequence
                    Integer deletion = end - start + 1;
                    String groupSix = m.group(6);
                    String groupSixWithoutDigits = MainUtils.removeDigits(groupSix);

                    if (groupSixWithoutDigits.contains("*")) {
                        consequence = "stop_gained";
                    } 
                    else if (groupSixWithoutDigits.length() != groupSix.length() && groupSixWithoutDigits.length() > 0) {
                        if (groupSixWithoutDigits.length() > deletion) {
                            consequence = IN_FRAME_INSERTION;
                        } else {
                            consequence = "NA";
                        }
                    } else {
                        Integer insertion = groupSix.length();
                        if (groupSixWithoutDigits.length() == 0 && insertion > 0) {
                            insertion = Integer.parseInt(groupSix);
                        }
                        if (insertion == 0) {
                            consequence = "NA";
                        } else if (insertion - deletion > 0) {
                            consequence = IN_FRAME_INSERTION;
                        } else if (insertion - deletion == 0) {
                            consequence = MISSENSE_VARIANT;
                        } else {
                            consequence = IN_FRAME_DELETION;
                        }
                    }
                }
            } else {
                p = Pattern.compile("[A-Z]?([0-9]+)(_[A-Z]?([0-9]+))?(_)?splice", Pattern.CASE_INSENSITIVE);
                m = p.matcher(proteinChange);
                if (m.matches()) {
                    start = Integer.valueOf(m.group(1));
                    if (m.group(3) != null) {
                        end = Integer.valueOf(m.group(3));
                    } else {
                        end = start;
                    }
                    consequence = "splice_region_variant";
                } else {
                    p = Pattern.compile("[A-Z]?([0-9]+)_[A-Z]?([0-9]+)(.+)", Pattern.CASE_INSENSITIVE);
                    m = p.matcher(proteinChange);
                    if (m.matches()) {
                        start = Integer.valueOf(m.group(1));
                        end = Integer.valueOf(m.group(2));
                        String v = m.group(3).toLowerCase();
                        switch (v) {
                            case "mis":
                                consequence = MISSENSE_VARIANT;
                                break;
                            case "ins":
                                consequence = IN_FRAME_INSERTION;
                                break;
                            case "del":
                                consequence = IN_FRAME_DELETION;
                                break;
                            case "fs":
                                consequence = FRAMESHIFT_VARIANT;
                                break;
                            case "trunc":
                                consequence = "feature_truncation";
                                break;
                            case "dup":
                                consequence = IN_FRAME_INSERTION;
                                break;
                            case "mut":
                                consequence = "any";
                        }
                    } else {
                        FrameshiftVariant frameshiftVariant = parseFrameshiftVariant(proteinChange);
                        if (frameshiftVariant != null) {
                            ref = frameshiftVariant.getRefResidues();
                            start = frameshiftVariant.getProteinStart();
                            end = start;
                            consequence = FRAMESHIFT_VARIANT;
                        } else {
                            p = Pattern.compile("([A-Z]+)?([0-9]+)((ins)|(del)|(dup)|(mut))", Pattern.CASE_INSENSITIVE);
                            m = p.matcher(proteinChange);
                            if (m.matches()) {
                                ref = m.group(1) == null ? null : m.group(1).toUpperCase();
                                start = Integer.valueOf(m.group(2));
                                end = start;
                                String v = m.group(3).toLowerCase();
                                switch (v) {
                                    case "ins":
                                        consequence = IN_FRAME_INSERTION;
                                        break;
                                    case "dup":
                                        consequence = IN_FRAME_INSERTION;
                                        break;
                                    case "del":
                                        consequence = IN_FRAME_DELETION;
                                        break;
                                    case "mut":
                                        consequence = "any";
                                        break;
                                }
                            } else {
                                p = Pattern.compile("M?1ext(-[0-9]+)?", Pattern.CASE_INSENSITIVE);
                                m = p.matcher(proteinChange);
                                if (m.matches()) {
                                    start = 1;
                                    end = start;
                                    consequence = IN_FRAME_INSERTION;
                                } else {
                                    /**
                                     * support extension variant (https://varnomen.hgvs.org/recommendations/protein/variant/extension/)
                                     * the following examples are supported
                                     * *959Qext*14
                                     * *110Gext*17
                                     * *315TextALGT*
                                     * *327Aext*?
                                     */
                                    p = Pattern.compile("(\\*)?([0-9]+)[A-Z]?ext([A-Z]+)?\\*([0-9]+)?(\\?)?", Pattern.CASE_INSENSITIVE);
                                    m = p.matcher(proteinChange);
                                    if (m.matches()) {
                                        ref = m.group(1) == null ? "" : m.group(1).toUpperCase();
                                        start = Integer.valueOf(m.group(2));
                                        end = start;
                                        consequence = "stop_lost";
                                    } else {
                                        p = Pattern.compile("([A-Z\\*])?([0-9]+)=", Pattern.CASE_INSENSITIVE);
                                        m = p.matcher(proteinChange);
                                        if (m.matches()) {
                                            var = ref = m.group(1) == null ? "" : m.group(1).toUpperCase();
                                            start = Integer.valueOf(m.group(2));
                                            end = start;
                                            if (ref != null && ref.equals("*")) {
                                                consequence = "stop_retained_variant";
                                            } else {
                                                consequence = "synonymous_variant";
                                            }
                                        } else {
                                            p = Pattern.compile("^([A-Z\\*]+)?([0-9]+)([A-Z\\*\\?]*)$", Pattern.CASE_INSENSITIVE);
                                            m = p.matcher(proteinChange);
                                            if (m.matches()) {
                                                ref = m.group(1) == null ? "" : m.group(1).toUpperCase();
                                                start = Integer.valueOf(m.group(2));
                                                end = start;
                                                var = m.group(3).toUpperCase();

                                                Integer refL = ref.length();
                                                Integer varL = var.length();

                                                if (ref.equals("*")) {
                                                    consequence = "stop_lost";
                                                } else if (var.equals("*")) {
                                                    consequence = "stop_gained";
                                                } else if (ref.equalsIgnoreCase(var)) {
                                                    consequence = "synonymous_variant";
                                                } else if (start == 1) {
                                                    consequence = "start_lost";
                                                } else if (var.equals("?")) {
                                                    consequence = "any";
                                                } else {
                                                    end = start + refL - 1;
                                                    if (refL > 1 || varL > 1) {
                                                        // Handle in-frame insertion/deletion event. Exp: IK744K
                                                        if (refL > varL) {
                                                            consequence = IN_FRAME_DELETION;
                                                        } else if (refL < varL) {
                                                            consequence = IN_FRAME_INSERTION;
                                                        } else {
                                                            consequence = MISSENSE_VARIANT;
                                                        }
                                                    } else if (refL == 1 && varL == 1) {
                                                        consequence = MISSENSE_VARIANT;
                                                    } else {
                                                        consequence = "NA";
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // truncating
        if (proteinChange.toLowerCase().matches("truncating mutations?")) {
            consequence = "feature_truncation";
        }

        VariantConsequence variantConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(consequence);

        if (variantConsequence == null) {
            variantConsequence = new VariantConsequence(consequence, null, false);
        }

        if (alteration.getRefResidues() == null && ref != null && !ref.isEmpty()) {
            alteration.setRefResidues(ref);
        }

        if (alteration.getVariantResidues() == null && var != null && !var.isEmpty()) {
            alteration.setVariantResidues(var);
        }

        if (alteration.getProteinStart() == null || (start != null && start != AlterationPositionBoundary.START.getValue())) {
            alteration.setProteinStart(start);
        }

        if (alteration.getProteinEnd() == null || (end != null && end != AlterationPositionBoundary.END.getValue())) {
            alteration.setProteinEnd(end);
        }

        if (alteration.getConsequence() == null && variantConsequence != null) {
            alteration.setConsequence(variantConsequence);
        } else if (alteration.getConsequence() != null && variantConsequence != null &&
            !AlterationUtils.consequenceRelated(alteration.getConsequence(), variantConsequence)) {
            // For the query which already contains consequence but different with OncoKB algorithm,
            // we should keep query consequence unless it is `any`
            if (alteration.getConsequence().equals(VariantConsequenceUtils.findVariantConsequenceByTerm("any"))) {
                alteration.setConsequence(variantConsequence);
            }
            // if alteration is a positional vairant and the consequence is manually assigned to others than NA, we should change it
            if (isPositionedAlteration(alteration) && alteration.getConsequence().equals(VariantConsequenceUtils.findVariantConsequenceByTerm(MISSENSE_VARIANT))) {
                alteration.setConsequence(variantConsequence);
            }
            // if the consequence provided by the user is too broad, we ignore and use the interpreted variant consequence
            if (TOO_BROAD_CONSEQUENCES.contains(alteration.getConsequence().getTerm())) {
                alteration.setConsequence(variantConsequence);
            }
        }

        // Annotate alteration based on consequence and special rules
        if (alteration.getAlteration() == null || alteration.getAlteration().isEmpty()) {
            alteration.setAlteration(proteinChange);
        }
        if (StringUtils.isEmpty(alteration.getAlteration())) {
            if (alteration.getConsequence() != null) {
                if (alteration.getConsequence().getTerm().equals("splice_region_variant")) {
                    alteration.setAlteration("splice mutation");
                }
                if (alteration.getConsequence().getTerm().equals(UPSTREAM_GENE)) {
                    alteration.setAlteration(SpecialVariant.PROMOTER.getVariant());
                }
            }
        } else {
            if (alteration.getAlteration().toLowerCase().matches("gain")) {
                alteration.setAlteration("Amplification");
            } else if (alteration.getAlteration().toLowerCase().matches("loss")) {
                alteration.setAlteration("Deletion");
            }
        }

        if (com.mysql.jdbc.StringUtils.isNullOrEmpty(alteration.getName()) && alteration.getAlteration() != null) {
            // Change the positional name
            if (isPositionedAlteration(alteration)) {
                if (StringUtils.isEmpty(excludedStr)) {
                    alteration.setName(alteration.getAlteration() + " Missense Mutations");
                } else {
                    alteration.setName(proteinChange + " Missense Mutations, excluding " + excludedStr);
                }
            } else {
                alteration.setName(alteration.getAlteration());
            }
        }

        if (alteration.getReferenceGenomes() == null || alteration.getReferenceGenomes().isEmpty()) {
            alteration.setReferenceGenomes(Collections.singleton(DEFAULT_REFERENCE_GENOME));
        }
    }

    public static Alteration getRevertFusions(ReferenceGenome referenceGenome, Alteration alteration, List<Alteration> fullAlterations) {
        if (fullAlterations == null) {
            return getRevertFusions(referenceGenome, alteration);
        } else {
            String revertFusionAltStr = getRevertFusionName(alteration);
            Optional<Alteration> match = fullAlterations.stream().filter(alteration1 -> alteration1.getGene().equals(alteration.getGene()) && alteration1.getAlteration().equalsIgnoreCase(revertFusionAltStr)).findFirst();
            if (match.isPresent()) {
                return match.get();
            } else {
                return null;
            }
        }
    }

    public static Alteration getRevertFusions(ReferenceGenome referenceGenome, Alteration alteration) {
        return alterationBo.findAlteration(alteration.getGene(),
            alteration.getAlterationType(), referenceGenome, getRevertFusionName(alteration));
    }

    public static String getRevertFusionName(Alteration alteration) {
        String revertFusionAltStr = null;
        if (alteration != null && alteration.getAlteration() != null
            && FusionUtils.isFusion(alteration.getAlteration())) {
            revertFusionAltStr = FusionUtils.getRevertFusionName(alteration.getAlteration());
        }
        return revertFusionAltStr;
    }

    public static String trimAlterationName(String alteration) {
        if (alteration != null) {
            if (alteration.startsWith("p.")) {
                alteration = alteration.substring(2);
            }
        }
        return alteration;
    }

    public static Alteration getAlteration(String hugoSymbol, String alteration, AlterationType alterationType,
                                           String consequence, Integer proteinStart, Integer proteinEnd, ReferenceGenome referenceGenome) {
        Alteration alt = new Alteration();

        if (alteration != null) {
            alteration = AlterationUtils.trimAlterationName(alteration);
            alt.setAlteration(alteration);
        }

        Gene gene = null;
        if (hugoSymbol != null) {
            gene = GeneUtils.getGeneByHugoSymbol(hugoSymbol);
        }
        alt.setGene(gene);

        AlterationType type = AlterationType.MUTATION;
        if (alterationType != null) {
            if (alterationType != null) {
                type = alterationType;
            }
        }
        alt.setAlterationType(type);

        VariantConsequence variantConsequence = null;
        if (StringUtils.isNotEmpty(consequence)) {
            variantConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(consequence);

            if (variantConsequence == null) {
                variantConsequence = new VariantConsequence();
                variantConsequence.setTerm(consequence);
            }
        }
        alt.setConsequence(variantConsequence);

        if (proteinEnd == null) {
            proteinEnd = proteinStart;
        }
        alt.setProteinStart(proteinStart);
        alt.setProteinEnd(proteinEnd);

        if (referenceGenome == null) {
            alt.getReferenceGenomes().add(DEFAULT_REFERENCE_GENOME);
        } else {
            alt.getReferenceGenomes().add(referenceGenome);
        }
        AlterationUtils.annotateAlteration(alt, alt.getAlteration());
        return alt;
    }

    private static Alteration convertTranscriptConsequenceSummaryToAlteration(TranscriptConsequenceSummary transcriptConsequenceSummary) {
        if (transcriptConsequenceSummary != null) {
            Alteration alteration = new Alteration();
            String hugoSymbol = transcriptConsequenceSummary.getHugoGeneSymbol();
            Integer entrezGeneId = StringUtils.isNumeric(transcriptConsequenceSummary.getEntrezGeneId()) ? Integer.parseInt(transcriptConsequenceSummary.getEntrezGeneId()) : null;
            if (StringUtils.isNotEmpty(transcriptConsequenceSummary.getHugoGeneSymbol())) {
                Gene gene = GeneUtils.getGene(hugoSymbol);
                if (gene == null) {
                    gene = new Gene();
                    gene.setHugoSymbol(transcriptConsequenceSummary.getHugoGeneSymbol());
                    gene.setEntrezGeneId(entrezGeneId);
                }
                alteration.setGene(gene);
            }

            if (StringUtils.isNotEmpty(transcriptConsequenceSummary.getHgvspShort())) {
                String hgvspShort = transcriptConsequenceSummary.getHgvspShort().trim().replace("p.", "");
                alteration.setAlteration(hgvspShort);
                alteration.setName(hgvspShort);
            }
            if (transcriptConsequenceSummary.getProteinPosition() != null) {
                if (transcriptConsequenceSummary.getProteinPosition().getStart() != null) {
                    alteration.setProteinStart(transcriptConsequenceSummary.getProteinPosition().getStart());
                }
                if (transcriptConsequenceSummary.getProteinPosition().getEnd() != null) {
                    alteration.setProteinEnd(transcriptConsequenceSummary.getProteinPosition().getEnd());
                }
            }
            if (StringUtils.isNotEmpty(transcriptConsequenceSummary.getConsequenceTerms())) {
                alteration.setConsequence(VariantConsequenceUtils.findVariantConsequenceByTerm(transcriptConsequenceSummary.getConsequenceTerms()));
            }
            return alteration;
        } else {
            return null;
        }
    }

    public static List<VariantAnnotation> getHgvsgVariantsAnnotationWithQueries(List<AnnotateMutationByHGVSQuery> queries, ReferenceGenome referenceGenome) throws ApiException {
        return GenomeNexusUtils.getHgvsVariantsAnnotation(queries.stream().map(AnnotateMutationByHGVSQuery::getHgvs).collect(Collectors.toList()), referenceGenome);
    }

    public static List<VariantAnnotation> getGenomicLocationVariantsAnnotationWithQueries(List<AnnotateMutationByGenomicChangeQuery> queries, ReferenceGenome referenceGenome) throws ApiException {
        return GenomeNexusUtils.getGenomicLocationVariantsAnnotation(queries.stream().map(query -> GenomeNexusUtils.convertGenomicLocation(query.getGenomicLocation())).collect(Collectors.toList()), referenceGenome);
    }

    public static Alteration getAlterationFromGenomeNexus(GNVariantAnnotationType type, ReferenceGenome referenceGenome, String query) throws ApiException {
        List<VariantAnnotation> variantAnnotations = new ArrayList<>();
        if (GNVariantAnnotationType.HGVS_G == type) {
            variantAnnotations = GenomeNexusUtils.getHgvsVariantsAnnotation(Collections.singletonList(query), referenceGenome);
        } else if (GNVariantAnnotationType.GENOMIC_LOCATION == type) {
            variantAnnotations = GenomeNexusUtils.getGenomicLocationVariantsAnnotation(Collections.singletonList(GenomeNexusUtils.convertGenomicLocation(query)), referenceGenome);
        }
        if (variantAnnotations.isEmpty()) {
            return null;
        } else {
            List<Alteration> alterations = getAlterationsFromGenomeNexus(variantAnnotations, referenceGenome);
            return alterations.isEmpty() ? null : alterations.get(0);
        }
    }

    public static List<Alteration> getAlterationsFromGenomeNexus(List<VariantAnnotation> variantAnnotations, ReferenceGenome referenceGenome) throws ApiException {
        List<TranscriptConsequenceSummary> transcriptsConsequenceSummary = GenomeNexusUtils.getTranscriptsConsequence(variantAnnotations, referenceGenome);
        List<Alteration> result = new ArrayList<>();

        for (TranscriptConsequenceSummary summary : transcriptsConsequenceSummary) {
            Alteration alteration = convertTranscriptConsequenceSummaryToAlteration(summary);
            if (alteration != null) {
                result.add(alteration);
            } else {
                result.add(new Alteration());
            }
        }
        return result;
    }

    public static String getOncogenic(List<Alteration> alterations) {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        List<Evidence> evidences = evidenceBo.findEvidencesByAlteration(alterations, Collections.singleton(EvidenceType.ONCOGENIC));
        return findHighestOncogenic(evidences);
    }

    private static String findHighestOncogenic(List<Evidence> evidences) {
        String oncogenic = "";
        for (Evidence evidence : evidences) {
            oncogenic = oncogenicList.indexOf(evidence.getKnownEffect()) < oncogenicList.indexOf(oncogenic) ? oncogenic : evidence.getKnownEffect();
        }
        return oncogenic;
    }

    public static List<Alteration> getAllAlterations(ReferenceGenome referenceGenome, Gene gene) {
        Set<Alteration> alterations = new HashSet<>();
        if (gene == null) {
            return new ArrayList<>();
        }
        if (!CacheUtils.containAlterations(gene.getEntrezGeneId())) {
            CacheUtils.setAlterations(gene);
        }
        return CacheUtils.getAlterations(gene.getEntrezGeneId(), referenceGenome);
    }

    public static List<Alteration> getAllAlterations() {
        Set<Gene> genes = CacheUtils.getAllGenes();
        List<Alteration> alterations = new ArrayList<>();
        for (Gene gene : genes) {
            alterations.addAll(getAllAlterations(null, gene));
        }
        return alterations;
    }

    public static Alteration getTruncatingMutations(Gene gene) {
        return findAlteration(gene, null, TRUNCATING_MUTATIONS.getVariant());
    }

    public static Set<Alteration> findVUSFromEvidences(Set<Evidence> evidences) {
        Set<Alteration> alterations = new HashSet<>();

        for (Evidence evidence : evidences) {
            if (evidence.getEvidenceType().equals(EvidenceType.VUS)) {
                alterations.addAll(evidence.getAlterations());
            }
        }

        return alterations;
    }

    public static Set<Alteration> getVUS(Gene gene) {
        Set<Alteration> result = new HashSet<>();
        if (gene != null) {
            result = CacheUtils.getVUS(gene.getEntrezGeneId());
        }
        return result;
    }

    public static List<Alteration> excludeVUS(List<Alteration> alterations) {
        List<Alteration> result = new ArrayList<>();

        for (Alteration alteration : alterations) {
            Set<Alteration> VUS = AlterationUtils.getVUS(alteration.getGene());
            if (!VUS.contains(alteration)) {
                result.add(alteration);
            } else {
                // We should also add alteration when other evidence exists.
                // This is to add alteration when it's in both mutation list and VUS list.
                // When it's a VUS, alteration will only have one evidence(VUS).
                List<Evidence> alterationEvidences = EvidenceUtils.getAlterationEvidences(Collections.singletonList(alteration)).stream().filter(evidence -> !EvidenceType.VUS.equals(evidence.getEvidenceType())).collect(Collectors.toList());
                if (alterationEvidences.size() > 0) {
                    result.add(alteration);
                }
            }
        }

        return result;
    }

    public static List<Alteration> excludeVUS(Gene gene, List<Alteration> alterations) {
        List<Alteration> result = new ArrayList<>();
        Set<Alteration> VUS = CacheUtils.getVUS(gene.getEntrezGeneId());

        if (VUS == null) {
            VUS = new HashSet<>();
        }

        for (Alteration alteration : alterations) {
            if (!VUS.contains(alteration)) {
                result.add(alteration);
            }
        }

        return result;
    }

    public static List<Alteration> excludeInferredAlterations(List<Alteration> alterations) {
        List<Alteration> result = new ArrayList<>();
        for (Alteration alteration : alterations) {
            String name = alteration.getAlteration();
            if (name != null) {
                Boolean contain = false;
                for (String inferredAlt : getInferredMutations()) {
                    if (name.startsWith(inferredAlt)) {
                        contain = true;
                    }
                }
                if (!contain) {
                    result.add(alteration);
                }
            }
        }
        return result;
    }

    public static Boolean isInferredAlterations(String alteration) {
        Boolean isInferredAlt = false;
        if (alteration != null) {
            for (String alt : getInferredMutations()) {
                if (alteration.equalsIgnoreCase(alt)) {
                    isInferredAlt = true;
                    break;
                }
            }
        }
        return isInferredAlt;
    }

    public static Boolean isLikelyInferredAlterations(String alteration) {
        Boolean isLikelyInferredAlt = false;
        if (alteration != null) {
            String lowerCaseAlteration = alteration.trim().toLowerCase();
            if (lowerCaseAlteration.startsWith("likely")) {
                alteration = alteration.replaceAll("(?i)likely", "").trim();
                for (String alt : getInferredMutations()) {
                    if (alteration.equalsIgnoreCase(alt)) {
                        isLikelyInferredAlt = true;
                        break;
                    }
                }
            }
        }
        return isLikelyInferredAlt;
    }

    public static Boolean isTruncatingMutations(String alteration) {
        return TRUNCATING_MUTATIONS.getVariant().toLowerCase().equals(alteration.toLowerCase());
    }

    public static Set<Alteration> getEvidencesAlterations(Set<Evidence> evidences) {
        Set<Alteration> alterations = new HashSet<>();
        if (evidences == null) {
            return alterations;
        }
        for (Evidence evidence : evidences) {
            if (evidence.getAlterations() != null) {
                alterations.addAll(evidence.getAlterations());
            }
        }
        return alterations;
    }

    public static Set<Alteration> getAlterationsByKnownEffectInGene(Gene gene, String knownEffect, Boolean includeLikely) {
        Set<Alteration> alterations = new HashSet<>();
        if (includeLikely == null) {
            includeLikely = false;
        }
        if (gene != null && knownEffect != null) {
            Set<Evidence> evidences = EvidenceUtils.getEvidenceByGenes(Collections.singleton(gene)).get(gene);
            for (Evidence evidence : evidences) {
                if (knownEffect.equalsIgnoreCase(evidence.getKnownEffect())) {
                    alterations.addAll(evidence.getAlterations());
                }
                if (includeLikely) {
                    String likely = "likely " + knownEffect;
                    if (likely.equalsIgnoreCase(evidence.getKnownEffect())) {
                        alterations.addAll(evidence.getAlterations());
                    }
                }
            }
        }
        return alterations;
    }

    public static String getInferredAlterationsKnownEffect(String inferredAlt) {
        String knownEffect = null;
        if (inferredAlt != null) {
            knownEffect = inferredAlt.replaceAll("(?i)\\s+mutations", "");
        }
        return knownEffect;
    }

    private static List<Alteration> getAlterations(Gene gene, ReferenceGenome referenceGenome, String alteration, AlterationType alterationType, String consequence, Integer proteinStart, Integer proteinEnd, List<Alteration> fullAlterations) {
        List<Alteration> alterations = new ArrayList<>();
        VariantConsequence variantConsequence = null;

        if (gene != null && alteration != null) {
            if (consequence != null) {
                Alteration alt = new Alteration();
                alt.setAlteration(alteration);
                variantConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(consequence);
                if (variantConsequence == null) {
                    variantConsequence = new VariantConsequence(consequence, null, false);
                }
                alt.setConsequence(variantConsequence);
                alt.setAlterationType(alterationType == null ? AlterationType.MUTATION : alterationType);
                alt.setGene(gene);
                alt.setProteinStart(proteinStart);
                alt.setProteinEnd(proteinEnd);
                alt.getReferenceGenomes().add(referenceGenome);

                AlterationUtils.annotateAlteration(alt, alt.getAlteration());

                LinkedHashSet<Alteration> alts = alterationBo.findRelevantAlterations(referenceGenome, alt, fullAlterations, true);
                if (!alts.isEmpty()) {
                    alterations.addAll(alts);
                }
            } else {
                Alteration alt = new Alteration();
                alt.setAlteration(alteration);
                alt.setAlterationType(alterationType == null ? AlterationType.MUTATION : alterationType);
                alt.setGene(gene);
                alt.setProteinStart(proteinStart);
                alt.setProteinEnd(proteinEnd);
                alt.getReferenceGenomes().add(referenceGenome);

                AlterationUtils.annotateAlteration(alt, alt.getAlteration());

                LinkedHashSet<Alteration> alts = alterationBo.findRelevantAlterations(referenceGenome, alt, fullAlterations, true);
                if (!alts.isEmpty()) {
                    alterations.addAll(alts);
                }
            }
        }

        if (FusionUtils.isFusion(alteration)) {
            Alteration alt = new Alteration();
            alt.setAlteration(alteration);
            alt.setAlterationType(alterationType == null ? AlterationType.MUTATION : alterationType);
            alt.setGene(gene);

            AlterationUtils.annotateAlteration(alt, alt.getAlteration());
            Alteration revertFusion = getRevertFusions(referenceGenome, alt, fullAlterations);
            if (revertFusion != null) {
                LinkedHashSet<Alteration> alts = alterationBo.findRelevantAlterations(referenceGenome, revertFusion, fullAlterations, true);
                if (alts != null) {
                    alterations.addAll(alts);
                }
            }
        }
        return alterations;
    }

    public static List<Alteration> getAlleleAlterations(ReferenceGenome referenceGenome, Alteration alteration) {
        return getAlleleAlterationsSub(referenceGenome, alteration, getAllAlterations(referenceGenome, alteration.getGene()));
    }

    public static List<Alteration> getAlleleAlterations(ReferenceGenome referenceGenome, Alteration alteration, List<Alteration> fullAlterations) {
        return getAlleleAlterationsSub(referenceGenome, alteration, fullAlterations);
    }

    public static List<Alteration> getAllMissenseAlleles(ReferenceGenome referenceGenome, int position, List<Alteration> fullAlterations) {
        return fullAlterations.stream().filter(alt -> alt.getReferenceGenomes().contains(referenceGenome) && alt.getConsequence() != null && alt.getConsequence().getTerm().equals(MISSENSE_VARIANT) && alt.getProteinStart() != null && alt.getProteinStart() == position).collect(Collectors.toList());
    }

    public static boolean isComplexMissense(String proteinChange) {
        if (StringUtils.isEmpty(proteinChange)) {
            return false;
        }
        Matcher m = COMPLEX_MISSENSE_ONE.matcher(proteinChange);
        if (m.matches()) {
            return (Integer.parseInt(m.group(4)) - Integer.parseInt(m.group(2)) + 1) == m.group(5).length();
        }
        m = COMPLEX_MISSENSE_TWO.matcher(proteinChange);
        if (m.matches()) {
            return m.group(3).length() == m.group(1).length();
        }
        return false;
    }

    public static List<Alteration> getMissenseProteinChangesFromComplexProteinChange(String proteinChange) {
        if (isComplexMissense(proteinChange)) {
            List<Alteration> matches = new ArrayList<>();
            Matcher m = COMPLEX_MISSENSE_ONE.matcher(proteinChange);
            if (m.matches()) {
                for (int i = 0; i < m.group(5).length(); i++) {
                    Alteration alteration = new Alteration();
                    int position = Integer.valueOf(m.group(2)) + i;
                    char varResidue = m.group(5).charAt(i);
                    alteration.setConsequence(VariantConsequenceUtils.findVariantConsequenceByTerm(MISSENSE_VARIANT));
                    alteration.setProteinStart(position);
                    alteration.setProteinEnd(position);
                    alteration.setVariantResidues(Character.toString(varResidue));
                    alteration.setAlteration(Integer.toString(position) + varResidue);
                    matches.add(alteration);
                }
            } else {
                m = COMPLEX_MISSENSE_TWO.matcher(proteinChange);
                if (m.matches()) {
                    for (int i = 0; i < m.group(1).length(); i++) {
                        Alteration alteration = new Alteration();
                        int position = Integer.valueOf(m.group(2)) + i;
                        char refResidue = m.group(1).charAt(i);
                        char varResidue = m.group(3).charAt(i);
                        alteration.setConsequence(VariantConsequenceUtils.findVariantConsequenceByTerm(MISSENSE_VARIANT));
                        alteration.setProteinStart(position);
                        alteration.setProteinEnd(position);
                        alteration.setRefResidues(Character.toString(refResidue));
                        alteration.setVariantResidues(Character.toString(varResidue));
                        alteration.setAlteration(refResidue + Integer.toString(position) + varResidue);
                        matches.add(alteration);
                    }
                }
            }
            return matches;
        } else {
            return new ArrayList<>();
        }
    }

    // Only for missense alteration
    public static List<Alteration> getPositionedAlterations(ReferenceGenome referenceGenome, Alteration alteration) {
        return getPositionedAlterations(referenceGenome, alteration, getAllAlterations(referenceGenome, alteration.getGene()));
    }

    // Only for missense alteration
    public static List<Alteration> getPositionedAlterations(ReferenceGenome referenceGenome, Alteration alteration, List<Alteration> fullAlterations) {
        if (alteration.getGene().getHugoSymbol().equals("ABL1") && alteration.getAlteration().equals("T315I")) {
            return new ArrayList<>();
        }

        if (alteration.getConsequence() != null && alteration.getConsequence().equals(VariantConsequenceUtils.findVariantConsequenceByTerm(MISSENSE_VARIANT))
            && alteration.getProteinStart().intValue() != AlterationPositionBoundary.START.getValue() && alteration.getProteinStart().intValue() != AlterationPositionBoundary.END.getValue()) {
            VariantConsequence variantConsequence = new VariantConsequence();
            variantConsequence.setTerm("NA");
            return AlterationUtils.findMutationsByConsequenceAndPosition(alteration.getGene(), referenceGenome, variantConsequence, alteration.getProteinStart(), alteration.getProteinEnd(), alteration.getRefResidues(), fullAlterations, true);
        }
        return new ArrayList<>();
    }


    private static List<Alteration> getAlleleAlterationsSub(ReferenceGenome referenceGenome, Alteration alteration, List<Alteration> fullAlterations) {
        boolean isPositionalVariant = AlterationUtils.isPositionedAlteration(alteration);
        if (alteration == null || alteration.getConsequence() == null ||
            !(isPositionalVariant || alteration.getConsequence().equals(VariantConsequenceUtils.findVariantConsequenceByTerm(MISSENSE_VARIANT)))) {
            return new ArrayList<>();
        }

        if (alteration.getGene().getHugoSymbol().equals("ABL1") && alteration.getAlteration().equals("T315I")) {
            return new ArrayList<>();
        }

        List<Alteration> missenseVariants = alterationBo.findRelevantOverlapAlterations(
            alteration.getGene(), referenceGenome, VariantConsequenceUtils.findVariantConsequenceByTerm(MISSENSE_VARIANT), alteration.getProteinStart(),
            alteration.getProteinEnd(), alteration.getAlteration(), fullAlterations);


        List<Alteration> complexMissenseMuts = getMissenseProteinChangesFromComplexProteinChange(alteration.getAlteration());

        List<Alteration> alleles = new ArrayList<>();
        for (Alteration alt : missenseVariants) {
            if (alt.getProteinStart() != null &&
                alt.getProteinEnd() != null &&
                alt.getProteinStart().equals(alt.getProteinEnd()) &&
                !alt.equals(alteration) &&
                (alteration.getRefResidues() == null || alt.getRefResidues() == null || alt.getRefResidues().equals(alteration.getRefResidues()))
            ) {
                // do not include the missense mutation from complex missense format
                if (complexMissenseMuts.size() > 0) {
                    Optional<Alteration> matched = complexMissenseMuts.stream().filter(mis -> {
                        String altNameToCompare = StringUtils.isEmpty(mis.getRefResidues()) ? (Integer.toString(alt.getProteinStart()) + alt.getVariantResidues()) : (alt.getRefResidues() + Integer.toString(alt.getProteinStart()) + alt.getVariantResidues());
                        return altNameToCompare.equals(mis.getAlteration());
                    }).findAny();
                    if (!matched.isPresent()) {
                        alleles.add(alt);
                    }
                } else {
                    alleles.add(alt);
                }
            }
        }

        // Special case for PDGFRA: don't match D842V as alternative allele to other alleles
        if (alteration.getGene() != null && alteration.getGene().getEntrezGeneId() == 5156 && !alteration.getAlteration().equals("D842V")) {
            Alteration d842v = AlterationUtils.findAlteration(alteration.getGene(), referenceGenome, "D842V");
            alleles.remove(d842v);
        }

        sortAlternativeAlleles(alleles);
        return alleles;
    }

    public static void removeAlternativeAllele(ReferenceGenome referenceGenome, Alteration alteration, List<Alteration> relevantAlterations) {
        // the alternative alleles do not only include the different variant allele, but also include the delins but it's essentially the same thing.
        // For instance, S768_V769delinsIL. This is equivalent to S768I + V769L, S768I should be listed relevant and not be excluded.
        if (alteration != null && alteration.getConsequence() != null && alteration.getConsequence().getTerm().equals(MISSENSE_VARIANT)) {
            // check for positional variant when the consequence is forced to be missense variant
            boolean isMissensePositionalVariant = StringUtils.isEmpty(alteration.getVariantResidues()) && alteration.getProteinStart() != null && alteration.getProteinEnd() != null && alteration.getProteinStart().equals(alteration.getProteinEnd());
            List<Alteration> overlapAlterations = alterationBo.findRelevantOverlapAlterations(alteration.getGene(), referenceGenome, alteration.getConsequence(), alteration.getProteinStart(), alteration.getProteinEnd(), alteration.getAlteration(), relevantAlterations);
            for (Alteration allele : overlapAlterations) {
//                if (allele.equals(alteration)) {
//                    continue;
//                }
                // range missense mutations is not alternative allele
                if(allele.getAlteration().endsWith("mis")){
                    continue;
                }
                // remove all alleles if the alteration variant residue is empty
                if (isMissensePositionalVariant && !StringUtils.isEmpty(allele.getVariantResidues())) {
                    relevantAlterations.remove(allele);
                    continue;
                }
                if (allele.getConsequence() != null && allele.getConsequence().getTerm().equals(MISSENSE_VARIANT)) {
                    if (alteration.getProteinStart().equals(alteration.getProteinEnd()) && !StringUtils.isEmpty(alteration.getVariantResidues())) {
                        if (allele.getProteinStart().equals(allele.getProteinEnd())) {
                            if (!alteration.getVariantResidues().equalsIgnoreCase(allele.getVariantResidues())) {
                                relevantAlterations.remove(allele);
                            }
                        } else {
                            String alleleVariant = getMissenseVariantAllele(allele, alteration.getProteinStart());
                            if (alleleVariant != null && !alteration.getVariantResidues().equalsIgnoreCase(alleleVariant)) {
                                relevantAlterations.remove(allele);
                            }
                        }
                    } else {
                        boolean isRelevant = false;
                        for (int start = alteration.getProteinStart().intValue(); start <= alteration.getProteinEnd().intValue(); start++) {
                            String alterationAllele = getMissenseVariantAllele(alteration, start);
                            if (alterationAllele != null) {
                                if (allele.getProteinStart().equals(allele.getProteinEnd())) {
                                    if (alterationAllele.equalsIgnoreCase(allele.getVariantResidues())) {
                                        isRelevant = true;
                                        break;
                                    }
                                } else {
                                    String alleleVariant = getMissenseVariantAllele(allele, start);
                                    if (alleleVariant == null || alterationAllele.equalsIgnoreCase(alleleVariant)) {
                                        isRelevant = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!isRelevant) {
                            relevantAlterations.remove(allele);
                        }
                    }
                }
            }
        }
    }

    private static String getMissenseVariantAllele(Alteration alteration, int position) {
        Pattern pattern = Pattern.compile(".*delins([\\w]+)");
        Matcher matcher = pattern.matcher(alteration.getAlteration());
        if (matcher.find()) {
            String variantAlleles = matcher.group(1);
            int index = position - alteration.getProteinStart();
            if (index >= 0 && index < variantAlleles.length()) {
                return variantAlleles.substring(index, index + 1);
            } else {
                return null;
            }
        } else if (alteration.getVariantResidues() != null && alteration.getVariantResidues().length() > 0) {
            return alteration.getVariantResidues().substring(0, 1);
        }
        return null;
    }

    public static List<Alteration> lookupVariant(String query, Boolean exactMatch, Boolean omitExclusion, List<Alteration> alterations) {
        List<Alteration> alterationList = new ArrayList<>();
        // Only support columns(alteration/name) blur search.
        query = query.toLowerCase().trim();
        if (exactMatch == null)
            exactMatch = false;
        if (com.mysql.jdbc.StringUtils.isNullOrEmpty(query))
            return alterationList;
        query = query.trim().toLowerCase();
        for (Alteration alteration : alterations) {
            // since we are doing string search, we should remove the exclusion clause to prevent the clause been matched
            if (isMatch(exactMatch, query, removeExclusionCriteria(alteration.getAlteration()))) {
                alterationList.add(alteration);
                continue;
            }

            if (isMatch(exactMatch, query, removeExclusionCriteria(alteration.getName()))) {
                alterationList.add(alteration);
                continue;
            }
        }

        // if the query is part of the abbreviation list, the corresponding full name should be indexed.
        if (NamingUtils.hasAbbreviation(query)) {
            String fullName = NamingUtils.getFullName(query);
            if (fullName != null) {
                for (Alteration alteration : alterations) {
                    if (isMatch(exactMatch, fullName, alteration.getName())) {
                        alterationList.add(alteration);
                    }
                }
            }
        }
        return alterationList;
    }

    private static Boolean isMatch(Boolean exactMatch, String query, String string) {
        if (string != null) {
            if (exactMatch) {
                if (StringUtils.containsIgnoreCase(string, query)) {
                    return true;
                }
            } else {
                if (StringUtils.containsIgnoreCase(string, query)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Sort the alternative alleles alphabetically
    private static void sortAlternativeAlleles(List<Alteration> alternativeAlleles) {
        Collections.sort(alternativeAlleles, new Comparator<Alteration>() {
            @Override
            public int compare(Alteration a1, Alteration a2) {
                return a1.getAlteration().compareTo(a2.getAlteration());
            }
        });
    }

    public static void sortAlterationsByTheRange(List<Alteration> alterations, final int proteinStart, final int proteinEnd) {
        Collections.sort(alterations, new Comparator<Alteration>() {
            @Override
            public int compare(Alteration a1, Alteration a2) {
                if (a1.getProteinStart() == null || a1.getProteinEnd() == null) {
                    if (a2.getProteinStart() == null || a2.getProteinEnd() == null) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
                if (a2.getProteinStart() == null || a2.getProteinEnd() == null) {
                    return -1;
                }

                int overlap1 = Math.min(a1.getProteinEnd(), proteinEnd) - Math.max(a1.getProteinStart(), proteinStart);
                int overlap2 = Math.min(a2.getProteinEnd(), proteinEnd) - Math.max(a2.getProteinStart(), proteinStart);

                if (overlap1 == overlap2) {
                    int diff = a1.getProteinEnd() - a1.getProteinStart() - (a2.getProteinEnd() - a2.getProteinStart());
                    return diff == 0 ? a1.getAlteration().compareTo(a2.getAlteration()) : diff;
                } else {
                    return overlap2 - overlap1;
                }
            }
        });
    }

    public static List<Alteration> getRelevantAlterations(ReferenceGenome referenceGenome, Alteration alteration) {
        if (alteration == null) {
            return new ArrayList<>();
        }
        Gene gene = alteration.getGene();
        return getRelevantAlterations(referenceGenome, alteration, getAllAlterations(referenceGenome, gene));
    }

    public static List<Alteration> getRelevantAlterations(ReferenceGenome referenceGenome, Alteration alteration, List<Alteration> fullAlterations) {
        if (alteration == null || alteration.getGene() == null) {
            return new ArrayList<>();
        }
        Gene gene = alteration.getGene();
        VariantConsequence consequence = alteration.getConsequence();
        String term = consequence == null ? null : consequence.getTerm();
        Integer proteinStart = alteration.getProteinStart();
        Integer proteinEnd = alteration.getProteinEnd();

        return getAlterations(
            gene, referenceGenome, alteration.getAlteration(), alteration.getAlterationType(), term,
            proteinStart, proteinEnd,
            fullAlterations);
    }

    public static List<Alteration> removeAlterationsFromList(List<Alteration> list, List<Alteration> alterationsToBeRemoved) {
        List<Alteration> cleanedList = new ArrayList<>();
        for (Alteration alt : list) {
            if (!alterationsToBeRemoved.contains(alt)) {
                cleanedList.add(alt);
            }
        }
        return cleanedList;
    }

    public static Alteration findAlteration(Gene gene, ReferenceGenome referenceGenome, String alteration) {
        if (gene == null) {
            return null;
        }
        if (referenceGenome == null) {
            return alterationBo.findAlteration(gene, AlterationType.MUTATION, alteration);
        } else {
            return alterationBo.findAlteration(gene, AlterationType.MUTATION, referenceGenome, alteration);
        }
    }

    public static boolean isCategoricalAlteration(String alteration) {
        if (StringUtils.isEmpty(alteration)) {
            return false;
        }
        alteration = removeExclusionCriteria(alteration);
        List<String> categoricalAlterations = new ArrayList<>();
        categoricalAlterations.addAll(getInferredMutations().stream().map(mut -> mut.toLowerCase()).collect(Collectors.toList()));
        categoricalAlterations.addAll(getStructuralAlterations().stream().map(mut -> mut.toLowerCase()).collect(Collectors.toList()));
        return categoricalAlterations.contains(alteration.toLowerCase());
    }

    public static List<Alteration> findOncogenicMutations(List<Alteration> fullAlterations) {
        return findAlterationsByStartWith(InferredMutation.ONCOGENIC_MUTATIONS.getVariant(), fullAlterations);
    }

    public static List<Alteration> findFusions(List<Alteration> fullAlterations) {
        return findAlterationsByStartWith(StructuralAlteration.FUSIONS.getVariant(), fullAlterations);
    }

    private static boolean startsWithIgnoreCase(String url, String param) {
        return url.regionMatches(true, 0, param, 0, param.length());
    }

    private static List<Alteration> findAlterationsByStartWith(String startWith, List<Alteration> fullAlterations) {
        Comparator<Alteration> byAlt = ((Comparator<Alteration>) (o1, o2) -> {
            int result = o1.getAlteration().compareTo(o2.getAlteration());
            if (result == 0) {
                if (o1.getId() == null) return 1;
                if (o2.getId() == null) return -1;
                return o1.getId().compareTo(o2.getId());
            }
            return result;
        }).reversed();
        TreeSet<Alteration> matchedAlterations = new TreeSet<>(byAlt);

        for (int i = 0; i < fullAlterations.size(); i++) {
            Alteration alt = fullAlterations.get(i);
            if (alt.getAlteration() != null && startsWithIgnoreCase(alt.getAlteration(), startWith)) {
                matchedAlterations.add(alt);
            }
        }
        return matchedAlterations.stream().collect(Collectors.toList());
    }

    public static Alteration findExactlyMatchedAlteration(ReferenceGenome referenceGenome, Alteration alteration, List<Alteration> fullAlterations) {
        Alteration matchedByAlteration = findAlteration(referenceGenome, alteration.getAlteration(), fullAlterations);
        if (matchedByAlteration != null) {
            if (matchedByAlteration.getConsequence() == null
                || alteration.getConsequence() == null
                || matchedByAlteration.getConsequence().getTerm().equalsIgnoreCase("NA")
                || alteration.getConsequence().getTerm().equalsIgnoreCase("NA")
            ) {
                return matchedByAlteration;
            }
            // We also want to do a consequence check, if the consequence has been specified, then it should be respected
            if (consequenceRelated(alteration.getConsequence(), matchedByAlteration.getConsequence())) {
                return matchedByAlteration;
            } else {
                return null;
            }
        } else {
            // For in-frame deletion, we should also look for variant with/out trailing amino acids
            VariantConsequence inframeDeletionConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(IN_FRAME_DELETION);
            if (inframeDeletionConsequence.equals(alteration.getConsequence()) && !alteration.getAlteration().contains("delins")) {
                List<Alteration> matches = findMutationsByConsequenceAndPosition(alteration.getGene(), referenceGenome, inframeDeletionConsequence, alteration.getProteinStart(), alteration.getProteinEnd(), alteration.getAlteration(), fullAlterations, false).stream().filter(alt -> !alt.getAlteration().contains("delins")).collect(Collectors.toList());
                if (matches.size() > 0) {
                    return matches.iterator().next();
                }
            }

            // check missense mutations that ignore reference allele.
            VariantConsequence missenseConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(MISSENSE_VARIANT);
            if (missenseConsequence.equals(alteration.getConsequence())) {
                Optional<Alteration> match = fullAlterations.stream().filter(alt ->
                    alt.getReferenceGenomes().contains(referenceGenome) &&
                        missenseConsequence.equals(alt.getConsequence()) &&
                        alteration.getProteinStart().equals(alt.getProteinStart()) &&
                        alteration.getProteinEnd().equals(alt.getProteinEnd()) &&
                        (com.mysql.jdbc.StringUtils.isNullOrEmpty(alteration.getRefResidues()) || alteration.getRefResidues().equals(alt.getRefResidues())) &&
                        (com.mysql.jdbc.StringUtils.isNullOrEmpty(alteration.getVariantResidues()) || alteration.getVariantResidues().equals(alt.getVariantResidues()))
                ).findAny();
                if (match.isPresent()) {
                    return match.get();
                }
            }

            // check frame shift mutations that ignore variant or reference allele.
            VariantConsequence frameShiftConsequence = VariantConsequenceUtils.findVariantConsequenceByTerm(FRAMESHIFT_VARIANT);
            if (frameShiftConsequence.equals(alteration.getConsequence())) {
                FrameshiftVariant fsAlteration = parseFrameshiftVariant(alteration.getAlteration());
                Optional<Alteration> match = fullAlterations.stream().filter(alt ->
                    frameShiftConsequence.equals(alt.getConsequence()) && isSameFrameshiftVariant(fsAlteration, parseFrameshiftVariant(alt.getAlteration()))
                ).findAny();
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }

        return null;
    }

    public static List<Alteration> findMutationsByConsequenceAndPosition(Gene gene, ReferenceGenome referenceGenome, VariantConsequence consequence, int start, int end, String referenceResidue, List<Alteration> alterations, Boolean onSamePosition) {
        Set<Alteration> result = new HashSet<>();

        if (alterations != null && alterations.size() > 0) {
            for (int i = 0; i < alterations.size(); i++) {
                Alteration alteration = alterations.get(i);
                if (alteration.getGene().equals(gene) && alteration.getConsequence() != null
                    && consequenceRelated(alteration.getConsequence(), consequence)
                    && alteration.getProteinStart() != null
                    && alteration.getProteinEnd() != null
                    && (referenceGenome == null || alteration.getReferenceGenomes().contains(referenceGenome))
                    && alteration.getProteinStart() >= start
                    && alteration.getProteinStart() <= end
                    && (alteration.getRefResidues() == null || referenceResidue == null || referenceResidue.equals(alteration.getRefResidues()))) {
                    if (!onSamePosition || alteration.getProteinStart().equals(alteration.getProteinEnd())) {
                        result.add(alteration);
                    }
                }
            }
        } else {
            Collection<Alteration> queryResult;
            queryResult = CacheUtils.findMutationsByConsequenceAndPositionOnSamePosition(gene, referenceGenome, consequence, start, end, referenceResidue);
            if (queryResult != null) {
                result.addAll(queryResult);
            }
        }

        return new ArrayList<>(result);
    }
    public static Alteration findAlteration(ReferenceGenome referenceGenome, String alteration, List<Alteration> fullAlterations) {
        if (alteration == null) {
            return null;
        }
        // Implement the data access logic
        for (int i = 0; i < fullAlterations.size(); i++) {
            Alteration alt = fullAlterations.get(i);
            if (alt.getAlteration() != null && alt.getAlteration().equalsIgnoreCase(alteration)) {
                if (referenceGenome == null) {
                    return alt;
                } else if (alt.getReferenceGenomes().contains(referenceGenome)) {
                    return alt;
                }
            }
        }
        for (int i = 0; i < fullAlterations.size(); i++) {
            Alteration alt = fullAlterations.get(i);
            if (alt.getAlteration() != null && alt.getName().equalsIgnoreCase(alteration)) {
                if (referenceGenome == null) {
                    return alt;
                } else if (alt.getReferenceGenomes().contains(referenceGenome)) {
                    return alt;
                }
            }
        }

        if (NamingUtils.hasAbbreviation(alteration)) {
            return findAlteration(referenceGenome, NamingUtils.getFullName(alteration), fullAlterations);
        }
        return null;
    }

    public static Alteration findAlteration(ReferenceGenome referenceGenome, String alteration, String name, List<Alteration> fullAlterations) {
        if (alteration == null) {
            return null;
        }
        for (Alteration alt : fullAlterations) {
            if (alt.getAlteration() != null && alt.getAlteration().equalsIgnoreCase(alteration) && alt.getName().equalsIgnoreCase(name)) {
                if (referenceGenome == null) {
                    return alt;
                } else if (alt.getReferenceGenomes().contains(referenceGenome)) {
                    return alt;
                }
            }
        }
        return null;
    }

    public static Boolean isOncogenicAlteration(Alteration alteration) {
        EvidenceBo evidenceBo = ApplicationContextSingleton.getEvidenceBo();
        List<Evidence> oncogenicEvs = evidenceBo.findEvidencesByAlteration(Collections.singleton(alteration), Collections.singleton(EvidenceType.ONCOGENIC));
        Boolean isOncogenic = null;
        for (Evidence evidence : oncogenicEvs) {
            Oncogenicity oncogenicity = Oncogenicity.getByEvidence(evidence);
            if (isOncogenic(oncogenicity)) {
                isOncogenic = true;
                break;
            } else if (oncogenicity != null && oncogenicity.equals(Oncogenicity.LIKELY_NEUTRAL) && oncogenicity.equals(Oncogenicity.INCONCLUSIVE)) {
                isOncogenic = false;
            }
            if (isOncogenic != null) {
                break;
            }
        }

        // If there is no oncogenicity specified by the system and it is hotspot, then this alteration should be oncogenic.
        if (isOncogenic == null && HotspotUtils.isHotspot(alteration)) {
            isOncogenic = true;
        }
        return isOncogenic;
    }

    public static Set<String> getGeneralVariants() {
        Set<String> variants = new HashSet<>();
        variants.addAll(getInferredMutations());
        variants.addAll(getStructuralAlterations());
        variants.addAll(getSpecialVariant());
        return variants;
    }

    public static Set<String> getInferredMutations() {
        Set<String> variants = new HashSet<>();
        for (InferredMutation inferredMutation : InferredMutation.values()) {
            variants.add(inferredMutation.getVariant());
        }
        return variants;
    }

    public static Set<String> getStructuralAlterations() {
        Set<String> variants = new HashSet<>();
        for (StructuralAlteration structuralAlteration : StructuralAlteration.values()) {
            variants.add(structuralAlteration.getVariant());
        }
        return variants;
    }

    public static boolean isPositionedAlteration(Alteration alteration) {
        boolean isPositionVariant = false;
        if (alteration != null
            && alteration.getProteinStart() != null
            && alteration.getProteinEnd() != null
            && alteration.getProteinStart().equals(alteration.getProteinEnd())
            && alteration.getRefResidues() != null && alteration.getRefResidues().length() == 1
            && alteration.getVariantResidues() == null
            && alteration.getConsequence() != null
            && (alteration.getConsequence().getTerm().equals("NA") || alteration.getConsequence().getTerm().equals(MISSENSE_VARIANT))
        )
            isPositionVariant = true;
        return isPositionVariant;
    }

    private static Set<String> getSpecialVariant() {
        Set<String> variants = new HashSet<>();
        for (SpecialVariant variant : SpecialVariant.values()) {
            variants.add(variant.getVariant());
        }
        return variants;
    }

    public static boolean isGeneralAlterations(String variant) {
        boolean is = false;
        Set<String> generalAlterations = getGeneralVariants();
        for (String generalAlteration : generalAlterations) {
            if (generalAlteration.toLowerCase().equals(variant.toLowerCase())) {
                is = true;
                break;
            }
        }
        return is;
    }

    public static Boolean isGeneralAlterations(String mutationStr, Boolean exactMatch) {
        exactMatch = exactMatch || false;
        if (exactMatch) {
            return MainUtils.containsCaseInsensitive(mutationStr, getGeneralVariants());
        } else if (stringContainsItemFromSet(mutationStr, getGeneralVariants())
            && itemFromSetAtEndString(mutationStr, getGeneralVariants())) {
            return true;
        }
        return false;
    }

    public static String toString(Collection<Alteration> relevantAlterations) {
        return toString(relevantAlterations, false);
    }

    public static String toString(Collection<Alteration> relevantAlterations, boolean sort) {
        List<String> names = new ArrayList<>();
        for (Alteration alteration : relevantAlterations) {
            names.add(alteration.getAlteration());
        }
        return MainUtils.listToString(names, ", ", sort);
    }

    private static boolean stringContainsItemFromSet(String inputString, Set<String> items) {
        for (String item : items) {
            if (StringUtils.containsIgnoreCase(inputString, item)) {
                return true;
            }
        }
        return false;
    }

    private static boolean itemFromSetAtEndString(String inputString, Set<String> items) {
        for (String item : items) {
            if (StringUtils.endsWithIgnoreCase(inputString, item)) {
                return true;
            }
        }
        return false;
    }
}
