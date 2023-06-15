package org.mskcc.cbio.oncokb.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.model.*;
import org.mskcc.cbio.oncokb.model.clinicalTrialsMatching.Tumor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.model.SpecialTumorType.ALL_LIQUID_TUMORS;
import static org.mskcc.cbio.oncokb.model.SpecialTumorType.ALL_SOLID_TUMORS;


public class TumorTypeUtils {
    private static String ONCO_TREE_API_URL = null;
    private static final ImmutableList<String> LiquidTumorTissues = ImmutableList.of(
        "Lymph", "Blood", "Lymphoid", "Myeloid"
    );

    public static Boolean isSolidTumor(TumorType tumorType) {
        return isDesiredTumorForm(tumorType, TumorForm.SOLID);
    }

    public static Boolean isLiquidTumor(TumorType tumorType) {
        return isDesiredTumorForm(tumorType, TumorForm.LIQUID);
    }

    private static boolean isDesiredTumorForm(TumorType tumorType, TumorForm tumorForm) {
        if (tumorForm == null || tumorType == null) {
            return false;
        }

        // This is mainly for the tissue
        if (tumorType.getTumorForm() != null) {
            return tumorForm.equals(tumorType.getTumorForm());
        }

        // when the code is null, we need to validate the main type
        if (tumorType.getCode() == null) {
            TumorForm mainTypeTumorForm = getTumorForm(tumorType.getMainType());
            if (tumorType.getMainType() != null && mainTypeTumorForm != null) {
                return mainTypeTumorForm.equals(tumorForm);
            }
        } else {
            return tumorType.getTumorForm() != null && tumorType.getTumorForm().equals(tumorForm);
        }
        return false;
    }

    public static Boolean hasSolidTumor(Set<TumorType> tumorTypes) {
        if (tumorTypes == null)
            return null;
        for (TumorType tumorType : tumorTypes) {
            if (isSolidTumor(tumorType)) {
                return true;
            }
        }
        return false;
    }

    public static Boolean hasLiquidTumor(Set<TumorType> tumorTypes) {
        if (tumorTypes == null)
            return null;
        for (TumorType tumorType : tumorTypes) {
            if (isLiquidTumor(tumorType)) {
                return true;
            }
        }
        return false;
    }

    public static List<TumorType> findRelevantTumorTypes(String tumorType) {
        return findRelevantTumorTypes(tumorType, null, RelevantTumorTypeDirection.UPWARD);
    }

    public static Set<TumorType> getDxOneRelevantCancerTypes(Set<TumorType> tumorTypes) {
        Set<TumorType> dxRelevantCancerTypes = new HashSet<>();
        for (TumorType tumorType : tumorTypes) {
            if (StringUtils.isEmpty(tumorType.getSubtype())) {
                dxRelevantCancerTypes.add(tumorType);
            } else {
                dxRelevantCancerTypes.addAll(TumorTypeUtils.findRelevantTumorTypes(TumorTypeUtils.getTumorTypeName(tumorType), false, RelevantTumorTypeDirection.UPWARD).stream().filter(ct -> ct.getLevel() > 0).collect(Collectors.toSet()));
            }
        }
        return dxRelevantCancerTypes;
    }

    public static Set<TumorType> findEvidenceRelevantCancerTypes(Evidence evidence) {
        if (evidence == null)
            return new HashSet<>();

        if (evidence.getId() != null) {
            Set<TumorType> relevantCancerTypes = CacheUtils.getEvidenceRelevantCancerTypes(evidence.getGene().getEntrezGeneId(), evidence.getId());
            if (relevantCancerTypes != null) {
                return relevantCancerTypes;
            }
        }

        if (!evidence.getRelevantCancerTypes().isEmpty())
            return evidence.getRelevantCancerTypes();

        RelevantTumorTypeDirection relevantTumorTypeDirection = RelevantTumorTypeDirection.DOWNWARD;
        Set<TumorType> tumorTypes = evidence.getCancerTypes().stream().map(tumorType -> findEvidenceRelevantCancerTypes(getTumorTypeName(tumorType), StringUtils.isEmpty(tumorType.getSubtype()), relevantTumorTypeDirection)).flatMap(Collection::stream).collect(Collectors.toSet());
        tumorTypes.removeAll(evidence.getExcludedCancerTypes().stream().map(tumorType -> findEvidenceRelevantCancerTypes(getTumorTypeName(tumorType), StringUtils.isEmpty(tumorType.getSubtype()), relevantTumorTypeDirection)).flatMap(Collection::stream).collect(Collectors.toSet()));
        return tumorTypes;
    }

    private static List<TumorType> findEvidenceRelevantCancerTypes(String tumorType, boolean isMainType, RelevantTumorTypeDirection direction) {

        // Check whether the tumorType is special tumor type
        SpecialTumorType specialTumorType = ApplicationContextSingleton.getTumorTypeBo().getSpecialTumorTypeByName(tumorType);
        if (specialTumorType != null) {
            return findRelevantTumorTypesForSpecialCancerTypes(specialTumorType, direction);
        }
        LinkedHashSet<TumorType> mappedTumorTypes = new LinkedHashSet<>();


        if (direction == null) {
            direction = RelevantTumorTypeDirection.DOWNWARD;
        }

        if (direction.equals(RelevantTumorTypeDirection.DOWNWARD)) {
            if (isMainType) {
                TumorType mainType = ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType);
                if (mainType != null) {
                    mappedTumorTypes.add(mainType);
                    mappedTumorTypes.addAll(ApplicationContextSingleton.getTumorTypeBo().getAllSubtypesByMainType(mainType.getMainType()));
                }
            } else {
                TumorType cancerType = ApplicationContextSingleton.getTumorTypeBo().getByName(tumorType);
                if (cancerType != null) {
                    mappedTumorTypes.add(cancerType);
                    mappedTumorTypes.addAll(getChildTumorTypes(cancerType, true));
                }
            }
        } else if (direction.equals(RelevantTumorTypeDirection.UPWARD)) {
            if (isMainType) {
                TumorType mainType = ApplicationContextSingleton.getTumorTypeBo().getByMainType(tumorType);
                if (mainType != null) {
                    mappedTumorTypes.add(mainType);
                }
            } else {
                TumorType cancerType = ApplicationContextSingleton.getTumorTypeBo().getByName(tumorType);
                if (cancerType != null) {
                    mappedTumorTypes.add(cancerType);
                    mappedTumorTypes.addAll(getParentTumorTypes(cancerType, true));
                }
            }
        }

        return new ArrayList<>(new LinkedHashSet<>(mappedTumorTypes));
    }

    public static LinkedHashSet<TumorType> getParentTumorTypes(TumorType tumorType, boolean onlySameMaintype) {
        if (tumorType == null || tumorType.getParent() == null) return new LinkedHashSet<>();
        LinkedHashSet parentTumorTypes = new LinkedHashSet();
        // we do not want to include the tissue level which is 1
        if (tumorType.getParent() != null && tumorType.getLevel() > 2) {
            if (!onlySameMaintype || tumorType.getParent().getMainType().equals(tumorType.getMainType())) {
                parentTumorTypes.add(tumorType.getParent());
            }
            parentTumorTypes.addAll(getParentTumorTypes(tumorType.getParent(), onlySameMaintype));
        }
        return parentTumorTypes;
    }

    public static LinkedHashSet<TumorType> getChildTumorTypes(TumorType tumorType, boolean onlySameMaintype) {
        if (tumorType == null || tumorType.getChildren().isEmpty()) return new LinkedHashSet<>();
        LinkedHashSet<TumorType> childTumorTypes = new LinkedHashSet();
        if (onlySameMaintype) {
            childTumorTypes.addAll(tumorType.getChildren().stream().filter(child -> child.getMainType().equals(tumorType.getMainType())).collect(Collectors.toList()));
        } else {
            childTumorTypes.addAll(tumorType.getChildren());
        }
        tumorType.getChildren().forEach(child -> childTumorTypes.addAll(getChildTumorTypes(child, onlySameMaintype)));
        return childTumorTypes;
    }

    public static List<TumorType> findRelevantTumorTypesForSpecialCancerTypes(SpecialTumorType specialTumorType, RelevantTumorTypeDirection direction) {
        List<TumorType> relevantCancerTypes = new ArrayList<>();
        if (specialTumorType == null) {
            return relevantCancerTypes;
        }
        relevantCancerTypes.add(ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(specialTumorType));
        if (RelevantTumorTypeDirection.UPWARD.equals(direction)) {
            switch (specialTumorType) {
                case ALL_SOLID_TUMORS:
                case ALL_LIQUID_TUMORS:
                    relevantCancerTypes.add(ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(SpecialTumorType.ALL_TUMORS));
                    break;
                default:
                    break;
            }
        } else if (RelevantTumorTypeDirection.DOWNWARD.equals(direction)) {
            Set<TumorType> allOncoTreeTypes =  new HashSet<>();
            allOncoTreeTypes.addAll(ApplicationContextSingleton.getTumorTypeBo().getAllSubtypes());
            allOncoTreeTypes.addAll(ApplicationContextSingleton.getTumorTypeBo().getAllMainTypes());
            allOncoTreeTypes.removeAll(ApplicationContextSingleton.getTumorTypeBo().getAllSpecialTumorOncoTreeTypes());
            switch (specialTumorType) {
                case ALL_SOLID_TUMORS:
                    relevantCancerTypes.addAll(
                        allOncoTreeTypes
                            .stream()
                            .filter(tumorType -> TumorForm.SOLID.equals(tumorType.getTumorForm()) || TumorForm.MIXED.equals(tumorType.getTumorForm()))
                            .collect(Collectors.toSet())
                    );
                    break;
                case ALL_LIQUID_TUMORS:
                    relevantCancerTypes.addAll(
                        allOncoTreeTypes
                            .stream()
                            .filter(tumorType -> TumorForm.LIQUID.equals(tumorType.getTumorForm()) || TumorForm.MIXED.equals(tumorType.getTumorForm()))
                            .collect(Collectors.toSet())
                    );
                    break;
                case ALL_TUMORS:
                    relevantCancerTypes.addAll(allOncoTreeTypes);
                    relevantCancerTypes.add(ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(ALL_SOLID_TUMORS));
                    relevantCancerTypes.add(ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(ALL_LIQUID_TUMORS));
                    break;
                default:
                    break;
            }
        }
        return relevantCancerTypes;
    }
    public static List<TumorType> findRelevantTumorTypes(String tumorType, Boolean isMainType, RelevantTumorTypeDirection direction) {
        // Check whether the tumorType is special tumor type
        SpecialTumorType specialTumorType = ApplicationContextSingleton.getTumorTypeBo().getSpecialTumorTypeByName(tumorType);
        if (specialTumorType != null) {
            return findRelevantTumorTypesForSpecialCancerTypes(specialTumorType, direction);
        }

        LinkedHashSet<TumorType> mappedTumorTypes = new LinkedHashSet<>();
        TumorType matchedTumorType = null;

        if (isMainType != Boolean.TRUE) {
            matchedTumorType = ApplicationContextSingleton.getTumorTypeBo().getByCode(tumorType);
            if (matchedTumorType == null) {
                matchedTumorType = ApplicationContextSingleton.getTumorTypeBo().getBySubtype(tumorType);
            }
        }

        String mainTypeName = tumorType;
        if (matchedTumorType != null) {
            // Add matched tumor type
            mappedTumorTypes.add(matchedTumorType);
            mainTypeName = matchedTumorType.getMainType();
        }

        // Add main type
        TumorType matchedMainType = ApplicationContextSingleton.getTumorTypeBo().getByMainType(mainTypeName);
        if (matchedMainType != null) {
            mappedTumorTypes.add(matchedMainType);
        }

        if (direction.equals(RelevantTumorTypeDirection.UPWARD)) {
            if (matchedTumorType != null) {
                // Add matched parent tumor types
                mappedTumorTypes.addAll(getParentTumorTypes(matchedTumorType, true));
            }
        } else {
            if (matchedTumorType != null) {
                // Add matched child tumor types
                mappedTumorTypes.addAll(getChildTumorTypes(matchedTumorType, true));
            }
        }

        // Include all solid tumors
        if (hasSolidTumor(new HashSet<>(mappedTumorTypes))) {
            mappedTumorTypes.add(ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(ALL_SOLID_TUMORS));
        }

        // Include all liquid tumors
        if (hasLiquidTumor(new HashSet<>(mappedTumorTypes))) {
            mappedTumorTypes.add(ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(ALL_LIQUID_TUMORS));
        }

        // Include all tumors
        TumorType allTumor = ApplicationContextSingleton.getTumorTypeBo().getBySpecialTumor(SpecialTumorType.ALL_TUMORS);
        if (allTumor != null) {
            mappedTumorTypes.add(allTumor);
        }

        return new ArrayList<>(new LinkedHashSet<>(mappedTumorTypes));
    }

    public static String getTumorTypeName(TumorType tumorType) {
        if (tumorType == null) {
            return "";
        } else {
            if (!StringUtils.isEmpty(tumorType.getSubtype())) {
                return tumorType.getSubtype();
            } else if (!StringUtils.isEmpty(tumorType.getMainType())) {
                return tumorType.getMainType();
            } else {
                return "";
            }
        }
    }

    public static String getTumorTypesName(Collection<TumorType> tumorTypes) {
        return tumorTypes.stream().map(tumorType -> getTumorTypeName(tumorType)).collect(Collectors.joining(", "));
    }

    public static String getEvidenceTumorTypesName(Evidence evidence) {
        return getTumorTypesNameWithExclusion(evidence.getCancerTypes(), evidence.getExcludedCancerTypes());
    }

    public static String getTumorTypesNameWithExclusion(Collection<TumorType> tumorTypes, Collection<TumorType> excludedTumorTypes) {
        StringBuilder sb = new StringBuilder(getTumorTypesName(tumorTypes));
        if (excludedTumorTypes != null && excludedTumorTypes.size() > 0) {
            sb.append(" (excluding ");
            sb.append(getTumorTypesName(excludedTumorTypes));
            sb.append(")");
        }
        return sb.toString();
    }

    public static Map<String, org.mskcc.oncotree.model.TumorType> getAllNestedOncoTreeSubtypesFromSource() {
        String url = getOncoTreeApiUrl() + "tumorTypes?version=" + CacheUtils.getInfo().getOncoTreeVersion() + "&flat=false";
        Map<String, org.mskcc.oncotree.model.TumorType> result = new HashMap<>();
        try {
            String json = IOUtils.toString(new InputStreamReader(TumorTypeUtils.class.getResourceAsStream("/data/oncotree/tumortypes.json")));
            Map map = JsonUtils.jsonToMap(json);
            Map<String, org.mskcc.oncotree.model.TumorType> data = (Map<String, org.mskcc.oncotree.model.TumorType>) map;
            org.mskcc.oncotree.model.TumorType oncoTreeTumorType = new ObjectMapper().convertValue(data.get("TISSUE"), org.mskcc.oncotree.model.TumorType.class);
            result.put("TISSUE", oncoTreeTumorType);
        } catch (Exception e) {
            System.out.println("You need to include oncotree nested file. Endpoint: tumorTypes?version=" + CacheUtils.getInfo().getOncoTreeVersion() + "&flat=false");
            e.printStackTrace();
        }
        return result;
    }

    public static List<TumorType> getOncoTreeSubtypesFromSource() {
        List<TumorType> tumorTypes = new ArrayList<>();
        try {
            Gson gson = new GsonBuilder().create();
            org.mskcc.oncotree.model.TumorType[] oncoTreeTumorTypes = gson.fromJson(new BufferedReader(new InputStreamReader(TumorTypeUtils.class.getResourceAsStream("/data/oncotree/tumortypes-flat.json"))), org.mskcc.oncotree.model.TumorType[].class);
            for (org.mskcc.oncotree.model.TumorType oncotreeTumorType : Arrays.asList(oncoTreeTumorTypes)) {
                TumorType tumorType = new TumorType(oncotreeTumorType);
                if (tumorType.getLevel() != null && tumorType.getLevel() == 0) {
                    tumorType.setTumorForm(TumorForm.MIXED);
                } else {
                    tumorType.setTumorForm(getTumorForm(tumorType.getTissue()));
                }
                tumorTypes.add(tumorType);
            }
        } catch (Exception e) {
            System.out.println("You need to include oncotree flat file. Endpoint: tumorTypes?version=" + CacheUtils.getInfo().getOncoTreeVersion() + "&flat=true");
            e.printStackTrace();
        }
        return tumorTypes;
    }

    private static String getOncoTreeApiUrl() {
        if (ONCO_TREE_API_URL == null) {
            ONCO_TREE_API_URL = PropertiesUtils.getProperties("oncotree.api");
            if (ONCO_TREE_API_URL != null) {
                ONCO_TREE_API_URL = ONCO_TREE_API_URL.trim();
            }
            if (ONCO_TREE_API_URL == null || ONCO_TREE_API_URL.isEmpty()) {
                ONCO_TREE_API_URL = "http://oncotree.info/api/";
            }
        }
        return ONCO_TREE_API_URL;
    }

    public static TumorForm getTumorForm(SpecialTumorType specialTumorType) {
        if (specialTumorType == null)
            return null;

        if (specialTumorType.equals(ALL_LIQUID_TUMORS) || specialTumorType.equals(SpecialTumorType.OTHER_LIQUID_TUMOR_TYPES)) {
            return TumorForm.LIQUID;
        } else if (specialTumorType.equals(ALL_SOLID_TUMORS) || specialTumorType.equals(SpecialTumorType.OTHER_SOLID_TUMOR_TYPES)) {
            return TumorForm.SOLID;
        } else {
            return TumorForm.MIXED;
        }
    }

    public static TumorForm getTumorForm(String tissue) {
        if (!com.mysql.jdbc.StringUtils.isNullOrEmpty(tissue)) {
            if (LiquidTumorTissues.contains(tissue))
                return TumorForm.LIQUID;
            else
                return TumorForm.SOLID;
        }
        return null;
    }

    private static TumorForm getTumorForm(TumorType tumorType) {
        if (tumorType.getTumorForm() != null) {
            return tumorType.getTumorForm();
        }
        return null;
    }

    public static TumorForm checkTumorForm(Set<TumorType> tumorTypes) {
        Set<TumorType> withoutSpecial = new HashSet<>(tumorTypes);
        withoutSpecial.removeAll(ApplicationContextSingleton.getTumorTypeBo().getAllSpecialTumorOncoTreeTypes());

        TumorForm tumorForm = getTumorForm(withoutSpecial);
        if (tumorForm == null) {
            Set<TumorType> existSpecial = new HashSet<>(tumorTypes);
            existSpecial.retainAll(ApplicationContextSingleton.getTumorTypeBo().getAllSpecialTumorOncoTreeTypes());
            return getTumorForm(existSpecial.stream().filter(tumorType -> tumorType.getTumorForm() != null && !tumorType.getTumorForm().equals(TumorForm.MIXED)).collect(Collectors.toSet()));
        } else {
            return tumorForm;
        }
    }

    private static TumorForm getTumorForm(Set<TumorType> tumorTypes) {
        if (tumorTypes == null)
            return null;
        Set<TumorForm> uniqueTumorForms = tumorTypes.stream()
            .filter(tumorType -> {
                TumorForm tumorForm = getTumorForm(tumorType);
                return tumorForm != null;
            })
            .map(tumorType -> getTumorForm(tumorType)).collect(Collectors.toSet());
        if (uniqueTumorForms.size() > 1 || uniqueTumorForms.size() == 0) {
            // There are ambiguous tumor forms
            return null;
        } else {
            return uniqueTumorForms.iterator().next();
        }
    }
}
