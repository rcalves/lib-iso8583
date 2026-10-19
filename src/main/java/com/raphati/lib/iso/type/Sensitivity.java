package com.raphati.lib.iso.type;

import com.raphati.lib.iso.utils.MaskingUtils;

/**
 * How the value of a data element or subelement is masked in {@code toString}. Declared per element in the field
 * definitions ({@code "sensitivity"}); an element without a declaration, or without a definition at all, is
 * {@link #FULL} (masked by default).
 */
public enum Sensitivity {
    /** Not sensitive: printed as is. */
    NONE,
    /** Account numbers (PAN, account identification): at most the first 6 and last 4 characters visible. */
    PARTIAL,
    /** Printed as {@code ****}. */
    FULL;

    /**
     * @return the value as it may be printed (null stays null)
     */
    public String mask(String value) {
        return switch (this) {
            case NONE -> value;
            case PARTIAL -> MaskingUtils.maskPan(value);
            case FULL -> MaskingUtils.maskFully(value);
        };
    }
}
