package com.raphati.lib.iso.type;

/**
 * How a data element encodes its length on the wire.
 */
public enum LengthType {
    /** Fixed length, no length prefix. */
    FIXED,
    /** Two-digit length prefix. */
    LLVAR,
    /** Three-digit length prefix. */
    LLLVAR
}
