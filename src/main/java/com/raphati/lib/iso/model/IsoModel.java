package com.raphati.lib.iso.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed ISO 8583 message. {@code toString} is safe to log: field values are masked by {@link DEModel}.
 *
 * @param mti    decoded MTI
 * @param bitmap decoded bitmap
 * @param fields data elements 2..128 keyed by DE number as a String, ascending
 */
public record IsoModel(MtiModel mti, BitmapModel bitmap, Map<String, DEModel> fields) {

    public IsoModel {
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Optional<DEModel> field(int id) {
        return Optional.ofNullable(fields.get(String.valueOf(id)));
    }

    /**
     * @return the {@link DEModel#errors()} of every field that has any, keyed by DE number (empty when all are valid)
     */
    public Map<String, List<String>> errors() {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        fields.forEach((id, field) -> {
            if (field.hasErrors()) {
                errors.put(id, field.errors());
            }
        });
        return Collections.unmodifiableMap(errors);
    }
}
