package com.raphati.lib.iso.utils;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.loader.IsoDefinitionLoader;
import com.raphati.lib.iso.model.FieldDefinitionModel;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.Sensitivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Masking primitives ({@link #maskPan(String)}, {@link #maskFully(String)}) and a network-agnostic reference policy
 * ({@link #maskValue(String, String)}, {@link #isSensitive(String)}) for callers that mask values without field
 * definitions.
 * <p>
 * The reference policy is derived from the field definitions shipped with the library (the default definition file
 * of every {@link CardNetworkType}): a path is masked with the strongest {@link Sensitivity} any network declares for
 * it, so {@code 48/10} is fully masked because Mastercard declares it {@code FULL}, even though the generic definition
 * declares it {@code NONE}. A path no shipped definition declares is returned unchanged; parsed elements are stricter
 * and mask anything undeclared.
 * <p>
 * Parsed elements do not use the reference policy: {@link com.raphati.lib.iso.model.DEModel} masks itself according
 * to the {@link Sensitivity} declared in the definitions it was parsed with.
 * <p>
 * Paths are a data element number ({@code "2"}) or, for subelements, the parent number and the subelement tag joined
 * by {@code /} ({@code "48/92"}, {@code "55/5A"}). Matching on the full path keeps a subelement tag from being
 * confused with a first-level data element of the same number.
 */
public final class MaskingUtils {

    private static final String FULL_MASK = "****";
    private static final int VISIBLE_PREFIX = 6;
    private static final int VISIBLE_SUFFIX = 4;
    private static final int MIN_MASKED = 6;

    private MaskingUtils() {
    }

    /**
     * Sensitive paths of the shipped definitions, loaded on first use of {@link #maskValue} or {@link #isSensitive}.
     */
    private static final class ReferencePolicy {

        static final Map<String, Sensitivity> SENSITIVE_PATHS = load();

        private static Map<String, Sensitivity> load() {
            IsoDefinitionLoader loader = new IsoDefinitionLoader(IsoParserConfig.defaults());
            Map<String, Sensitivity> paths = new HashMap<>();
            for (CardNetworkType network : CardNetworkType.values()) {
                if (IsoParserConfig.defaults().definitionPath(network) != null) {
                    loader.load(network).values().forEach(def -> collect(def.id(), def, paths));
                }
            }
            paths.values().removeIf(sensitivity -> sensitivity == Sensitivity.NONE);
            return Collections.unmodifiableMap(paths);
        }

        private static void collect(String path, FieldDefinitionModel def, Map<String, Sensitivity> paths) {
            paths.merge(path.toUpperCase(Locale.ROOT), def.sensitivity(),
                    (a, b) -> a.compareTo(b) >= 0 ? a : b);
            for (FieldDefinitionModel sub : def.subelements()) {
                collect(path + "/" + sub.id(), sub, paths);
            }
        }
    }

    /**
     * Keeps at most the first 6 and the last 4 characters of a PAN while always masking at least 6 characters: the
     * visible prefix shrinks for short values (a 10-character value keeps only its last 4). Values shorter than 10
     * characters are fully masked.
     */
    public static String maskPan(String pan) {
        if (pan == null) {
            return null;
        }
        int len = pan.length();
        if (len < MIN_MASKED + VISIBLE_SUFFIX) {
            return "*".repeat(len);
        }
        int prefix = Math.min(VISIBLE_PREFIX, len - VISIBLE_SUFFIX - MIN_MASKED);
        return pan.substring(0, prefix) + "*".repeat(len - prefix - VISIBLE_SUFFIX) + pan.substring(len - VISIBLE_SUFFIX);
    }

    /**
     * Masks a value according to its path ({@code "2"}, {@code "48/92"}, ...) and the strongest sensitivity the
     * shipped definitions declare for it: {@code PARTIAL} paths (PANs, account identification) keep at most 6+4
     * characters (see {@link #maskPan(String)}), {@code FULL} paths (track data, PIN data, keys, CVC 2, opaque and
     * reserved/private data elements) become {@code ****}, anything else is returned unchanged.
     */
    public static String maskValue(String path, String value) {
        if (value == null || path == null) {
            return value;
        }
        Sensitivity sensitivity = ReferencePolicy.SENSITIVE_PATHS.get(path.toUpperCase(Locale.ROOT));
        return sensitivity == null ? value : sensitivity.mask(value);
    }

    /**
     * @return whether values at this path are masked by {@link #maskValue(String, String)}
     */
    public static boolean isSensitive(String path) {
        return path != null && ReferencePolicy.SENSITIVE_PATHS.containsKey(path.toUpperCase(Locale.ROOT));
    }

    /**
     * @return {@code ****}, or {@code null} for a null value
     */
    public static String maskFully(String value) {
        return value == null ? null : FULL_MASK;
    }
}
