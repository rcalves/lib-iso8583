package com.raphati.lib.iso.model;

import java.util.List;

/**
 * Bitmap of a message, as read from the wire (see {@link com.raphati.lib.iso.utils.IsoUtils#parseBitmap}).
 *
 * @param primaryHex       primary bitmap (8 bytes) as uppercase hex, bit 1 included
 * @param secondaryHex     secondary bitmap (8 bytes) as uppercase hex, or {@code null} if bit 1 is not set
 * @param secondaryPresent whether bit 1 (secondary bitmap) is set
 * @param presentFields    data elements 2..128 flagged by the bitmap, ascending
 */
public record BitmapModel(String primaryHex, String secondaryHex, boolean secondaryPresent,
                          List<Integer> presentFields) {

    public BitmapModel {
        presentFields = presentFields == null ? List.of() : List.copyOf(presentFields);
    }
}
