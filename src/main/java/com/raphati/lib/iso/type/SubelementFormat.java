package com.raphati.lib.iso.type;

/**
 * How a data element is split into subelements.
 */
public enum SubelementFormat {
    /** The field has no subelements. */
    NONE,
    /** ASCII tag + numeric ASCII length + value (e.g. DE48). */
    TAG_LENGTH_VALUE,
    /** BER-TLV encoded bytes (e.g. DE55 EMV data). */
    BER_TLV
}
