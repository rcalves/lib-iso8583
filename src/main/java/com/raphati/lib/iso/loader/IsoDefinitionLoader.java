package com.raphati.lib.iso.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.model.FieldDefinitionModel;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.utils.ResourceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Loads the JSON field definitions of each card network and caches them.
 */
public class IsoDefinitionLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FIELD_ID = Pattern.compile("\\d{1,3}");
    private static final Pattern HEX_TAG = Pattern.compile("[0-9A-Fa-f]+");

    private final IsoParserConfig config;
    private final Map<CardNetworkType, Map<String, FieldDefinitionModel>> cache = new ConcurrentHashMap<>();

    public IsoDefinitionLoader() {
        this(IsoParserConfig.defaults());
    }

    public IsoDefinitionLoader(IsoParserConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * @return the definitions of the network keyed by id, ordered by numeric id (unmodifiable, cached)
     * @throws IsoParserException if the definitions cannot be read or are invalid
     */
    public Map<String, FieldDefinitionModel> load(CardNetworkType network) {
        Objects.requireNonNull(network, "network");
        return cache.computeIfAbsent(network, this::read);
    }

    public Optional<FieldDefinitionModel> find(CardNetworkType network, String id) {
        return Optional.ofNullable(load(network).get(id));
    }

    private Map<String, FieldDefinitionModel> read(CardNetworkType network) {
        String path = config.definitionPath(network);
        if (path == null) {
            throw new IsoParserException("No field definitions configured for network " + network);
        }
        DefinitionFile file;
        try (InputStream in = ResourceUtils.open(path)) {
            file = MAPPER.readValue(in, DefinitionFile.class);
        } catch (IOException e) {
            throw new IsoParserException("Invalid field definitions in " + path, e);
        }
        if (file == null || file.fields() == null) {
            throw new IsoParserException("No fields in definitions " + path);
        }
        if (file.network() == null || !network.name().equalsIgnoreCase(file.network().trim())) {
            throw new IsoParserException("Definitions " + path + " declare network " + file.network()
                    + " but " + network + " was expected");
        }

        for (FieldDefinitionModel def : file.fields()) {
            if (def == null || def.id() == null || !FIELD_ID.matcher(def.id()).matches()) {
                throw new IsoParserException("Invalid field id in " + path + ": " + (def == null ? null : def.id()));
            }
            validateSubelements(def, path);
        }
        List<FieldDefinitionModel> sorted = file.fields().stream()
                .sorted(Comparator.comparingInt(def -> Integer.parseInt(def.id())))
                .toList();

        Map<String, FieldDefinitionModel> result = new LinkedHashMap<>();
        for (FieldDefinitionModel def : sorted) {
            if (result.putIfAbsent(def.id(), def) != null) {
                throw new IsoParserException("Duplicate field id " + def.id() + " in " + path);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Subelement ids must be unique inside their field (case-insensitive) and match the field's subelement format:
     * exactly {@code subelementTagLength} characters for TAG_LENGTH_VALUE, an even number of hex digits for BER_TLV.
     * A TAG_LENGTH_VALUE field with a {@code subelementOffset} may also declare its offset prefix as
     * {@value FieldDefinitionModel#OFFSET_SUBELEMENT_ID}.
     */
    private static void validateSubelements(FieldDefinitionModel def, String path) {
        Set<String> seen = new HashSet<>();
        for (FieldDefinitionModel sub : def.subelements()) {
            String id = sub == null ? null : sub.id();
            boolean valid = id != null && !id.isBlank() && switch (def.subelementFormat()) {
                case TAG_LENGTH_VALUE -> id.length() == def.subelementTagLength()
                        || def.subelementOffset() > 0 && FieldDefinitionModel.OFFSET_SUBELEMENT_ID.equals(id);
                case BER_TLV -> id.length() % 2 == 0 && HEX_TAG.matcher(id).matches();
                case NONE -> true;
            };
            if (!valid) {
                throw new IsoParserException("Invalid subelement id in DE" + def.id() + " of " + path + ": " + id);
            }
            if (!seen.add(id.toUpperCase(Locale.ROOT))) {
                throw new IsoParserException("Duplicate subelement id " + id + " in DE" + def.id() + " of " + path);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DefinitionFile(String network, List<FieldDefinitionModel> fields) {
    }
}
