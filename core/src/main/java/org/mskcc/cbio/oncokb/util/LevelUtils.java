package org.mskcc.cbio.oncokb.util;

import org.apache.commons.collections.CollectionUtils;
import org.mskcc.cbio.oncokb.apiModels.InfoLevel;
import org.mskcc.cbio.oncokb.model.Evidence;
import org.mskcc.cbio.oncokb.model.LevelOfEvidence;
import org.mskcc.cbio.oncokb.model.TumorForm;
import org.mskcc.cbio.oncokb.model.TumorType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Hongxin Zhang
 */
public class LevelUtils {
    private static final List<LevelOfEvidence> PUBLIC_LEVELS = Collections.unmodifiableList(
        Arrays.asList(
            LevelOfEvidence.LEVEL_Fda3, LevelOfEvidence.LEVEL_Fda2, LevelOfEvidence.LEVEL_Fda1,
            LevelOfEvidence.LEVEL_Px3, LevelOfEvidence.LEVEL_Px2, LevelOfEvidence.LEVEL_Px1,
            LevelOfEvidence.LEVEL_Dx3, LevelOfEvidence.LEVEL_Dx2, LevelOfEvidence.LEVEL_Dx1,
            LevelOfEvidence.LEVEL_R2, LevelOfEvidence.LEVEL_4, LevelOfEvidence.LEVEL_3B, LevelOfEvidence.LEVEL_3A,
            LevelOfEvidence.LEVEL_2, LevelOfEvidence.LEVEL_1, LevelOfEvidence.LEVEL_R1
            )
    );

    // Levels related to therapy

    public static final List<LevelOfEvidence> THERAPEUTIC_SENSITIVE_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_4, LevelOfEvidence.LEVEL_3B, LevelOfEvidence.LEVEL_3A,
            LevelOfEvidence.LEVEL_2, LevelOfEvidence.LEVEL_1)
    );

    public static final List<LevelOfEvidence> THERAPEUTIC_RESISTANCE_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_R2, LevelOfEvidence.LEVEL_R1)
    );

    private static final List<LevelOfEvidence> ALLOWED_PROPAGATION_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_4, LevelOfEvidence.LEVEL_3B, LevelOfEvidence.NO)
    );

    // This is for sorting treatments when all levels are in one array. The only difference at the moment is the level 3A will be prioritised over 2B.
    // But 2B is still higher level of 3A
    private static final List<LevelOfEvidence> THERAPEUTIC_LEVELS_WITH_PRIORITY = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_R2, LevelOfEvidence.LEVEL_4, LevelOfEvidence.LEVEL_3B,
            LevelOfEvidence.LEVEL_3A, LevelOfEvidence.LEVEL_2, LevelOfEvidence.LEVEL_1, LevelOfEvidence.LEVEL_R1)
    );

    private static final List<LevelOfEvidence> THERAPEUTIC_OTHER_INDICATION_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_3B)
    );

    // Levels related to prognostic implications
    public static final List<LevelOfEvidence> PROGNOSTIC_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_Px3, LevelOfEvidence.LEVEL_Px2, LevelOfEvidence.LEVEL_Px1)
    );

    // Levels related to diagnostic implications
    public static final List<LevelOfEvidence> DIAGNOSTIC_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_Dx3, LevelOfEvidence.LEVEL_Dx2, LevelOfEvidence.LEVEL_Dx1)
    );

    // FDA Levels of Evidence
    public static final List<LevelOfEvidence> FDA_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_Fda3, LevelOfEvidence.LEVEL_Fda2, LevelOfEvidence.LEVEL_Fda1)
    );

    // levels that should be provided as additional info. Highest level calculation should not remove it.
    public static final List<LevelOfEvidence> INFO_LEVELS = Collections.unmodifiableList(
        Arrays.asList(LevelOfEvidence.LEVEL_R2)
    );

    public static Integer compareLevel(LevelOfEvidence a, LevelOfEvidence b) {
        return compareLevel(a, b, PUBLIC_LEVELS);
    }

    public static Integer compareLevel(LevelOfEvidence a, LevelOfEvidence b, List<LevelOfEvidence> levels) {
        if (!levels.contains(a)) {
            if (!levels.contains(b)) {
                return 0;
            }
            return 1;
        }
        if (!levels.contains(b)) {
            return -1;
        }

        return levels.indexOf(b) - levels.indexOf(a);
    }

    public static LevelOfEvidence getHighestLevelFromEvidence(Set<Evidence> evidences) {
        if (evidences != null) {
            Integer highestLevelIndex = -1;

            for (Evidence evidence : evidences) {
                LevelOfEvidence level = evidence.getLevelOfEvidence();
                if (level != null) {
                    Integer _index = PUBLIC_LEVELS.indexOf(level);
                    highestLevelIndex = _index > highestLevelIndex ? _index : highestLevelIndex;
                }
            }

            return highestLevelIndex > -1 ? PUBLIC_LEVELS.get(highestLevelIndex) : null;

        }
        return null;
    }

    public static LevelOfEvidence getHighestLevel(Set<LevelOfEvidence> levels) {
        return getHighestLevelByType(levels, PUBLIC_LEVELS);
    }

    public static LevelOfEvidence getHighestDiagnosticImplicationLevel(Set<LevelOfEvidence> levels) {
        return getHighestLevelByType(levels, DIAGNOSTIC_LEVELS);
    }

    public static LevelOfEvidence getHighestPrognosticImplicationLevel(Set<LevelOfEvidence> levels) {
        return getHighestLevelByType(levels, PROGNOSTIC_LEVELS);
    }

    public static LevelOfEvidence getHighestSensitiveLevel(Set<LevelOfEvidence> levels) {
        return getHighestLevelByType(levels, THERAPEUTIC_SENSITIVE_LEVELS);
    }
    public static LevelOfEvidence getHighestResistanceLevel(Set<LevelOfEvidence> levels) {
        return getHighestLevelByType(levels, THERAPEUTIC_RESISTANCE_LEVELS);
    }

    public static LevelOfEvidence getHighestFdaLevel(Set<LevelOfEvidence> levels) {
        return getHighestLevelByType(levels, FDA_LEVELS);
    }

    public static LevelOfEvidence getHighestLevelByType(Set<LevelOfEvidence> levels, List<LevelOfEvidence> levelPool) {
        Integer highestLevelIndex = -1;
        for (LevelOfEvidence levelOfEvidence : levels) {
            if (levelOfEvidence != null) {
                Integer _index = levelPool.indexOf(levelOfEvidence);
                highestLevelIndex = _index > highestLevelIndex ? _index : highestLevelIndex;
            }
        }
        return highestLevelIndex > -1 ? levelPool.get(highestLevelIndex) : null;
    }

    public static LevelOfEvidence getHighestLevelFromEvidenceByLevels(Set<Evidence> evidences, Set<LevelOfEvidence> levels, Boolean germline) {
        return getHighestLevelFromEvidenceByLevels(evidences, levels, false, germline);
    }

    public static LevelOfEvidence getHighestLevelFromEvidenceByLevels(Set<Evidence> evidences, Set<LevelOfEvidence> levels, boolean fdaLevel, Boolean germline) {
        if (levels == null) {
            return getHighestLevelFromEvidence(evidences);
        }
        if (evidences != null) {
            Integer highestLevelIndex = -1;

            for (Evidence evidence : evidences) {
                if(germline != null && !evidence.getForGermline().equals(germline)) continue;
                LevelOfEvidence level = evidence.getLevelOfEvidence();
                if(fdaLevel) {
                    level = evidence.getFdaLevel();
                }
                if (level != null && levels.contains(level)) {
                    Integer _index = PUBLIC_LEVELS.indexOf(level);
                    highestLevelIndex = _index > highestLevelIndex ? _index : highestLevelIndex;
                }
            }

            return highestLevelIndex > -1 ? PUBLIC_LEVELS.get(highestLevelIndex) : null;

        }
        return null;
    }

    public static Set<LevelOfEvidence> getLevelsFromEvidence(Set<Evidence> evidences) {
        Set<LevelOfEvidence> levels = new HashSet<>();
        if (evidences != null) {
            for (Evidence evidence : evidences) {
                LevelOfEvidence level = evidence.getLevelOfEvidence();
                if (!levels.contains(level)) {
                    levels.add(level);
                }
                LevelOfEvidence fdaLevel = evidence.getFdaLevel();
                if (!levels.contains(fdaLevel)) {
                    levels.add(fdaLevel);
                }
            }

        }
        return levels;
    }

    public static Set<LevelOfEvidence> getLevelsFromEvidenceByLevels(Set<Evidence> evidences, Set<LevelOfEvidence> levels) {
        Set<LevelOfEvidence> result = new HashSet<>();
        if (evidences != null) {
            for (Evidence evidence : evidences) {
                LevelOfEvidence level = evidence.getLevelOfEvidence();
                if (levels.contains(level) && !result.contains(level)) {
                    result.add(level);
                }
                LevelOfEvidence fdaLevel = evidence.getFdaLevel();
                if (levels.contains(fdaLevel) && !result.contains(fdaLevel)) {
                    result.add(fdaLevel);
                }
            }

        }
        return result;
    }

    public static Set<LevelOfEvidence> parseStringLevelOfEvidences(String levelOfEvidenceStr) {
        Set<LevelOfEvidence> levelOfEvidences = new HashSet<>();
        if (levelOfEvidenceStr != null) {
            String[] levelStrs = levelOfEvidenceStr.trim().split("\\s*,\\s*");
            for (int i = 0; i < levelStrs.length; i++) {
                LevelOfEvidence level = LevelOfEvidence.getByName(levelStrs[i]);
                if (level != null) {
                    levelOfEvidences.add(level);
                }
            }
        }
        return levelOfEvidences;
    }

    public static Boolean isSensitiveLevel(LevelOfEvidence levelOfEvidence) {
        Boolean flag = false;
        if (levelOfEvidence != null && THERAPEUTIC_SENSITIVE_LEVELS.contains(levelOfEvidence)) {
            flag = true;
        }
        return flag;
    }

    public static Boolean isResistanceLevel(LevelOfEvidence levelOfEvidence) {
        Boolean flag = false;
        if (levelOfEvidence != null && THERAPEUTIC_RESISTANCE_LEVELS.contains(levelOfEvidence)) {
            flag = true;
        }
        return flag;
    }

    public static Set<LevelOfEvidence> getPublicLevels() {
        return new HashSet<>(PUBLIC_LEVELS);
    }

    public static Set<LevelOfEvidence> getSensitiveLevels() {
        return new HashSet<>(CollectionUtils.intersection(PUBLIC_LEVELS, THERAPEUTIC_SENSITIVE_LEVELS));
    }

    public static Set<LevelOfEvidence> getResistanceLevels() {
        return new HashSet<>(CollectionUtils.intersection(PUBLIC_LEVELS, THERAPEUTIC_RESISTANCE_LEVELS));
    }

    public static Set<LevelOfEvidence> getTherapeuticLevels() {
        Set<LevelOfEvidence> levels = new HashSet<>();
        levels.addAll(new HashSet<>(CollectionUtils.intersection(PUBLIC_LEVELS, THERAPEUTIC_SENSITIVE_LEVELS)));
        levels.addAll(new HashSet<>(CollectionUtils.intersection(PUBLIC_LEVELS, THERAPEUTIC_RESISTANCE_LEVELS)));
        return levels;
    }

    public static int getSensitiveLevelIndex(LevelOfEvidence levelOfEvidence) {
        return THERAPEUTIC_SENSITIVE_LEVELS.indexOf(levelOfEvidence);
    }

    public static int getResistanceLevelIndex(LevelOfEvidence levelOfEvidence) {
        return THERAPEUTIC_RESISTANCE_LEVELS.indexOf(levelOfEvidence);
    }

    public static LevelOfEvidence getSensitiveLevelByIndex(int index) {
        return THERAPEUTIC_SENSITIVE_LEVELS.get(index);
    }

    public static LevelOfEvidence getResistanceLevelByIndex(int index) {
        return THERAPEUTIC_RESISTANCE_LEVELS.get(index);
    }

    public static List<LevelOfEvidence> getIndexedPublicLevels() {
        return new ArrayList<>(PUBLIC_LEVELS);
    }

    public static List<LevelOfEvidence> getIndexedTherapeuticLevels() {
        return new ArrayList<>(THERAPEUTIC_LEVELS_WITH_PRIORITY);
    }

    public static List<LevelOfEvidence> getIndexedDiagnosticLevels() {
        return new ArrayList<>(DIAGNOSTIC_LEVELS);
    }

    public static List<LevelOfEvidence> getIndexedPrognosticLevels() {
        return new ArrayList<>(PROGNOSTIC_LEVELS);
    }

        public static List<LevelOfEvidence> getIndexedFdaLevels() {
        return new ArrayList<>(FDA_LEVELS);
    }

    public static Set<LevelOfEvidence> getPrognosticLevels() {
        return new HashSet<>(CollectionUtils.intersection(PUBLIC_LEVELS, PROGNOSTIC_LEVELS));
    }

    public static Set<LevelOfEvidence> getDiagnosticLevels() {
        return new HashSet<>(CollectionUtils.intersection(PUBLIC_LEVELS, DIAGNOSTIC_LEVELS));
    }

    public static Set<LevelOfEvidence> geFdaLevels() {
        return new HashSet<>(CollectionUtils.intersection(PUBLIC_LEVELS, FDA_LEVELS));
    }

    public static Set<LevelOfEvidence> getAllowedCurationLevels() {
        Set levels = new HashSet<>(PUBLIC_LEVELS);
        levels.remove(LevelOfEvidence.LEVEL_3B);
        return levels;
    }

    public static List<LevelOfEvidence> getAllowedPropagationLevels() {
        return ALLOWED_PROPAGATION_LEVELS;
    }

    public static List<LevelOfEvidence> getAllowedFdaLevels() {
        return FDA_LEVELS;
    }

    public static LevelOfEvidence getDefaultPropagationLevelByTumorForm(Evidence evidence, TumorForm tumorForm) {
        TumorForm evidenceTumorForm = resolveEvidenceTumorForm(evidence);
        if (evidenceTumorForm == null || tumorForm == null) {
            return null;
        } else if (evidenceTumorForm.equals(tumorForm) && tumorForm.equals(TumorForm.SOLID)) {
            if (evidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_1) ||
                evidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_2)) {
                return LevelOfEvidence.LEVEL_3B;
            } else if (evidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_3A)) {
                return LevelOfEvidence.LEVEL_3B;
            } else if (evidence.getLevelOfEvidence().equals(LevelOfEvidence.LEVEL_4)) {
                return LevelOfEvidence.NO;
            }
        }
        return LevelOfEvidence.NO;
    }

    public static TumorForm resolveEvidenceTumorForm(Evidence evidence) {
        Set<TumorForm> tumorForms = evidence.getCancerTypes().stream().map(tumorType -> tumorType.getTumorForm()).distinct().collect(Collectors.toSet());
        if (tumorForms.size() == 1) {
            return tumorForms.iterator().next();
        } else {
            return null;
        }
    }

    public static ListIterator getTherapeuticLevelsWithPriorityLIstIterator() {
        return THERAPEUTIC_LEVELS_WITH_PRIORITY.listIterator(THERAPEUTIC_LEVELS_WITH_PRIORITY.size());
    }

    public static Boolean areSameLevels(LevelOfEvidence l1, LevelOfEvidence l2) {
        if (l1 == null && l2 == null) {
            return true;
        } else if (l1 != null && l2 != null) {
            return l1.equals(l2);
        } else {
            return false;
        }
    }

    public static List<InfoLevel> getInfoLevels() {
        List<LevelOfEvidence> levels = new ArrayList<>();
        levels.addAll(CollectionUtils.intersection(PUBLIC_LEVELS, THERAPEUTIC_RESISTANCE_LEVELS));
        levels.addAll(CollectionUtils.intersection(PUBLIC_LEVELS, THERAPEUTIC_SENSITIVE_LEVELS));
        levels.addAll(CollectionUtils.intersection(PUBLIC_LEVELS, DIAGNOSTIC_LEVELS));
        levels.addAll(CollectionUtils.intersection(PUBLIC_LEVELS, PROGNOSTIC_LEVELS));
        levels.addAll(CollectionUtils.intersection(PUBLIC_LEVELS, FDA_LEVELS));
        levels.sort(Comparator.comparing(LevelOfEvidence::getLevel));

        return levels.stream().map(levelOfEvidence -> new InfoLevel(levelOfEvidence)).collect(Collectors.toList());
    }
}
