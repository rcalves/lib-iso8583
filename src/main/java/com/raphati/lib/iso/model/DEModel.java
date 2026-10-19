package com.raphati.lib.iso.model;

import com.raphati.lib.iso.type.Sensitivity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed data element (or subelement).
 * <p>
 * Masking is a property of the element: {@code toString} masks the value according to {@link #sensitivity()}, so an
 * element is masked the same way whether it is printed on its own, inside its parent or through a map view such as
 * {@link #subelements()} or {@link IsoModel#fields()}. {@link #rawValue()} and {@link #decodedValue()} are the
 * explicit accessors to the unmasked values.
 *
 * @param id           DE number ("2", "48") or subelement tag ("42", "9F02", "TCC")
 * @param name         description from the field definitions, or {@code "Unknown"}
 * @param rawValue     value as read from the message (binary fields as uppercase hex), <strong>unmasked</strong>
 * @param decodedValue value after light decoding (e.g. trailing spaces stripped for text fields),
 *                     <strong>unmasked</strong>
 * @param subelements  parsed subelements keyed by tag, in message order (never null). Each one's {@link #path()} is
 *                     set to this element's path plus its tag
 * @param errors       problems found while decoding this element, e.g. a value that violates its definition or
 *                     subelements that could not be split (never null; empty when the element is valid)
 * @param path         position in the message: the DE number for a data element ({@code "48"}), parent path and tag
 *                     for a subelement ({@code "48/92"}). Defaults to {@code id}
 * @param sensitivity  how {@code toString} masks the value. Defaults to {@link Sensitivity#FULL}; the parser sets it
 *                     from the field definitions
 */
public record DEModel(String id, String name, String rawValue, String decodedValue,
                      Map<String, DEModel> subelements, List<String> errors, String path, Sensitivity sensitivity) {

    public DEModel {
        path = path == null ? id : path;
        sensitivity = sensitivity == null ? Sensitivity.FULL : sensitivity;
        subelements = subelements == null ? Map.of() : Collections.unmodifiableMap(rooted(path, subelements));
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates an element without errors, masked fully ({@link Sensitivity#FULL}).
     */
    public DEModel(String id, String name, String rawValue, String decodedValue, Map<String, DEModel> subelements) {
        this(id, name, rawValue, decodedValue, subelements, null);
    }

    /**
     * Creates an element masked fully ({@link Sensitivity#FULL}).
     */
    public DEModel(String id, String name, String rawValue, String decodedValue, Map<String, DEModel> subelements,
                   List<String> errors) {
        this(id, name, rawValue, decodedValue, subelements, errors, null, null);
    }

    /** Copies the subelements, moving any whose path does not match its position under {@code path}. */
    private static Map<String, DEModel> rooted(String path, Map<String, DEModel> subelements) {
        Map<String, DEModel> copy = new LinkedHashMap<>();
        subelements.forEach((tag, sub) -> {
            String childPath = path + "/" + tag;
            copy.put(tag, sub == null || childPath.equals(sub.path()) ? sub : sub.withPath(childPath));
        });
        return copy;
    }

    private DEModel withPath(String newPath) {
        return new DEModel(id, name, rawValue, decodedValue, subelements, errors, newPath, sensitivity);
    }

    public boolean hasSubelements() {
        return !subelements.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * @return whether this element or any of its subelements is masked in {@code toString}
     */
    public boolean containsSensitiveData() {
        if (sensitivity != Sensitivity.NONE) {
            return true;
        }
        for (DEModel sub : subelements.values()) {
            if (sub != null && sub.containsSensitiveData()) {
                return true;
            }
        }
        return false;
    }

    /** The strongest sensitivity among the descendants ({@link Sensitivity#NONE} without subelements). */
    private Sensitivity strongestInSubelements() {
        Sensitivity strongest = Sensitivity.NONE;
        for (DEModel sub : subelements.values()) {
            if (sub != null) {
                strongest = max(strongest, max(sub.sensitivity, sub.strongestInSubelements()));
            }
        }
        return strongest;
    }

    private static Sensitivity max(Sensitivity a, Sensitivity b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Safe to log. The value contains the data of its subelements, so it is masked with the strongest sensitivity
     * found in the subtree: the maximum of {@link #sensitivity()} and the sensitivities of all descendants. An element
     * that is not sensitive itself declares no masking window for its value, so it is printed as {@code ****} as soon
     * as any descendant is sensitive. Subelements are printed with their own {@code toString}.
     */
    @Override
    public String toString() {
        Sensitivity inner = strongestInSubelements();
        Sensitivity shown = inner == Sensitivity.NONE ? sensitivity
                : sensitivity == Sensitivity.NONE ? Sensitivity.FULL : max(sensitivity, inner);
        StringBuilder subs = new StringBuilder("{");
        subelements.forEach((tag, sub) -> {
            if (subs.length() > 1) {
                subs.append(", ");
            }
            subs.append(tag).append('=').append(sub);
        });
        subs.append('}');
        return "DEModel[id=" + id
                + ", name=" + name
                + ", rawValue=" + shown.mask(rawValue)
                + ", decodedValue=" + shown.mask(decodedValue)
                + ", subelements=" + subs
                + ", errors=" + errors
                + "]";
    }
}
