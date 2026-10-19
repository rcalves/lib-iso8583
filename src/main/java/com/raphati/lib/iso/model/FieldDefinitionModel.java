package com.raphati.lib.iso.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.raphati.lib.iso.type.LengthType;
import com.raphati.lib.iso.type.Sensitivity;
import com.raphati.lib.iso.type.SubelementFormat;

import java.util.List;

/**
 * Definition of a data element (or subelement) loaded from the JSON resources.
 *
 * @param id                     DE number or subelement tag
 * @param description            human-readable name
 * @param length                 exact length for FIXED fields, 0 for variable fields
 * @param maxLength              maximum length (bytes for binary fields)
 * @param type                   ISO data type (n, an, ans, b, z, x+n)
 * @param lengthType             FIXED, LLVAR or LLLVAR
 * @param subelementFormat       how the value is split into subelements (defaults to NONE)
 * @param subelementTagLength    tag length for TAG_LENGTH_VALUE
 * @param subelementLengthLength length-prefix length for TAG_LENGTH_VALUE
 * @param subelementOffset       leading characters before the first subelement (e.g. 1 for a TCC), exposed as
 *                               subelement {@link #OFFSET_SUBELEMENT_ID}
 * @param subelements            known subelement definitions (never null)
 * @param sensitivity            how the value is masked in {@code toString}; {@link Sensitivity#FULL} when not
 *                               declared, so an element is only printed when its definition says it is safe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FieldDefinitionModel(String id, String description, int length, int maxLength, String type,
                                   LengthType lengthType, SubelementFormat subelementFormat,
                                   int subelementTagLength, int subelementLengthLength, int subelementOffset,
                                   List<FieldDefinitionModel> subelements, Sensitivity sensitivity) {

    /**
     * Subelement id of the {@code subelementOffset} leading characters of a TAG_LENGTH_VALUE field (the Mastercard
     * DE48 Transaction Category Code). It may be declared in {@code subelements} like any tag, exempt from
     * {@code subelementTagLength}, when the field has an offset.
     */
    public static final String OFFSET_SUBELEMENT_ID = "TCC";

    public FieldDefinitionModel {
        subelementFormat = subelementFormat == null ? SubelementFormat.NONE : subelementFormat;
        subelements = subelements == null ? List.of() : List.copyOf(subelements);
        sensitivity = sensitivity == null ? Sensitivity.FULL : sensitivity;
    }

    /**
     * Creates a definition without a declared sensitivity ({@link Sensitivity#FULL}).
     */
    public FieldDefinitionModel(String id, String description, int length, int maxLength, String type,
                                LengthType lengthType, SubelementFormat subelementFormat,
                                int subelementTagLength, int subelementLengthLength, int subelementOffset,
                                List<FieldDefinitionModel> subelements) {
        this(id, description, length, maxLength, type, lengthType, subelementFormat, subelementTagLength,
                subelementLengthLength, subelementOffset, subelements, null);
    }
}
