package com.raphati.lib.iso.utils;

import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.model.BitmapModel;
import com.raphati.lib.iso.model.DEModel;
import com.raphati.lib.iso.model.FieldDefinitionModel;
import com.raphati.lib.iso.model.MtiModel;
import com.raphati.lib.iso.type.LengthType;
import com.raphati.lib.iso.type.Sensitivity;
import com.raphati.lib.iso.type.mti.ClassType;
import com.raphati.lib.iso.type.mti.FunctionType;
import com.raphati.lib.iso.type.mti.OriginType;
import com.raphati.lib.iso.type.mti.VersionType;
import org.jpos.iso.ISOBinaryField;
import org.jpos.iso.ISOComponent;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Low-level helpers around jPOS: packager loading, hex conversion, pack/unpack and field decoding.
 */
public final class IsoUtils {

    private static final String UNKNOWN = "Unknown";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    /** jPOS stores the unpacked wire bitmap as the component with key -1. */
    private static final int BITMAP_KEY = -1;

    private IsoUtils() {
    }

    /**
     * Loads a jPOS {@link GenericPackager} from a template path ({@code classpath:} or filesystem). Every call parses
     * the template again; callers that parse many messages should keep the packager, as
     * {@link com.raphati.lib.iso.strategy.MessageProcessingStrategy#packager()} does.
     */
    public static GenericPackager loadPackager(String templatePath) {
        if (templatePath == null) {
            throw new IsoParserException("Packager template path must not be null");
        }
        try (InputStream in = ResourceUtils.open(templatePath)) {
            return new GenericPackager(in);
        } catch (ISOException | IOException e) {
            throw new IsoParserException("Unable to load packager template " + templatePath, e);
        }
    }

    /**
     * Decodes a hex string (whitespace is ignored).
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IsoParserException("Hex value must not be null");
        }
        String clean = stripWhitespace(hex);
        if (clean.length() % 2 != 0) {
            throw new IsoParserException("Hex value must have an even number of characters");
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(clean.charAt(2 * i), 16);
            int lo = Character.digit(clean.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IsoParserException("Invalid hex character at position " + (2 * i));
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String stripWhitespace(String value) {
        int first = 0;
        while (first < value.length() && !Character.isWhitespace(value.charAt(first))) {
            first++;
        }
        if (first == value.length()) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length()).append(value, 0, first);
        for (int i = first; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Encodes bytes as uppercase hex.
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return bytesToHex(bytes, 0, bytes.length);
    }

    private static String bytesToHex(byte[] bytes, int offset, int length) {
        char[] out = new char[length * 2];
        for (int i = 0; i < length; i++) {
            int b = bytes[offset + i] & 0xFF;
            out[2 * i] = HEX[b >>> 4];
            out[2 * i + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }

    /**
     * Unpacks a message.
     *
     * @throws IsoParserException if the message is empty, cannot be unpacked, or has bytes left after the last field
     */
    public static ISOMsg unpack(byte[] data, GenericPackager packager) {
        return unpack(data, packager, false);
    }

    /**
     * Unpacks a message, optionally accepting whitespace (space, tab, CR, LF) after the last field, as left by a
     * message copied from a log line. Only bytes that jPOS did not consume are checked, so a last field that ends
     * with spaces is never shortened.
     *
     * @throws IsoParserException if the message is empty, cannot be unpacked, or has other bytes after the last field
     */
    public static ISOMsg unpack(byte[] data, GenericPackager packager, boolean allowTrailingWhitespace) {
        if (data == null || data.length == 0) {
            throw new IsoParserException("ISO message must not be empty");
        }
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        int consumed;
        try {
            consumed = msg.unpack(data);
        } catch (ISOException | RuntimeException e) {
            throw new IsoParserException("Unable to unpack ISO message: " + e.getMessage(), e);
        }
        if (consumed != data.length && !(allowTrailingWhitespace && isWhitespace(data, consumed))) {
            throw new IsoParserException("Unable to unpack ISO message: " + (data.length - consumed)
                    + " trailing byte(s) after the last field (consumed " + consumed + " of " + data.length + ")");
        }
        return msg;
    }

    private static boolean isWhitespace(byte[] data, int from) {
        for (int i = Math.max(from, 0); i < data.length; i++) {
            byte b = data[i];
            if (b != ' ' && b != '\t' && b != '\r' && b != '\n') {
                return false;
            }
        }
        return true;
    }

    public static ISOMsg unpackHex(String hex, GenericPackager packager) {
        return unpack(hexToBytes(hex), packager);
    }

    public static byte[] pack(ISOMsg msg) {
        try {
            return msg.pack();
        } catch (ISOException e) {
            throw new IsoParserException("Unable to pack ISO message: " + e.getMessage(), e);
        }
    }

    /**
     * Decodes a 4-digit MTI. Every decimal digit maps to a constant (unassigned digits to a {@code RESERVED_*} one),
     * so only a value that is not exactly 4 digits is rejected.
     */
    public static MtiModel parseMti(String mti) {
        if (mti == null || mti.length() != 4 || !isDigits(mti)) {
            throw new IsoParserException("MTI must be exactly 4 digits: " + mti);
        }
        return new MtiModel(mti,
                VersionType.fromDigit(mti.charAt(0)),
                ClassType.fromDigit(mti.charAt(1)),
                FunctionType.fromDigit(mti.charAt(2)),
                OriginType.fromDigit(mti.charAt(3)));
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads the bitmap of an unpacked message as it was on the wire (bit 1 and the secondary bitmap included, even
     * when the secondary bitmap is all zeros). For a message that was built rather than unpacked there is no wire
     * bitmap, so it is derived from the fields present, setting bit 1 only when a field above 64 is present.
     */
    public static BitmapModel parseBitmap(ISOMsg msg) {
        BitSet wire = wireBitmap(msg);
        BitSet bits = wire != null ? wire : derivedBitmap(msg);
        boolean secondary = bits.get(1);
        List<Integer> present = new ArrayList<>();
        for (int f = 2; f <= (secondary ? 128 : 64); f++) {
            if (bits.get(f)) {
                present.add(f);
            }
        }
        return new BitmapModel(bitmapHex(bits, 1), secondary ? bitmapHex(bits, 65) : null, secondary, present);
    }

    private static BitSet wireBitmap(ISOMsg msg) {
        ISOComponent component = msg.getComponent(BITMAP_KEY);
        try {
            return component != null && component.getValue() instanceof BitSet bitSet ? bitSet : null;
        } catch (ISOException e) {
            return null;
        }
    }

    private static BitSet derivedBitmap(ISOMsg msg) {
        BitSet bits = new BitSet(129);
        for (int f = 2; f <= 128; f++) {
            if (msg.hasField(f)) {
                bits.set(f);
            }
        }
        if (bits.nextSetBit(65) > 0) {
            bits.set(1);
        }
        return bits;
    }

    /** Encodes the 64 bits starting at field {@code first} (1 or 65) as 16 uppercase hex characters. */
    private static String bitmapHex(BitSet bits, int first) {
        byte[] out = new byte[8];
        for (int i = 0; i < 64; i++) {
            if (bits.get(first + i)) {
                out[i / 8] |= (byte) (0x80 >>> (i % 8));
            }
        }
        return bytesToHex(out);
    }

    /**
     * Converts data elements 2..128 into {@link DEModel}s, decoding subelements as defined. Problems confined to one
     * field (a value that violates its definition, subelements that cannot be split) do not abort the parse: they are
     * reported in {@link DEModel#errors()} and the field keeps its raw value.
     */
    public static Map<String, DEModel> toFieldMap(ISOMsg msg, Map<String, FieldDefinitionModel> definitions) {
        Map<String, DEModel> fields = new LinkedHashMap<>();
        for (int i = 2; i <= 128; i++) {
            if (!msg.hasField(i)) {
                continue;
            }
            String id = String.valueOf(i);
            String raw = msg.getComponent(i) instanceof ISOBinaryField
                    ? bytesToHex(msg.getBytes(i))
                    : msg.getString(i);
            FieldDefinitionModel def = definitions == null ? null : definitions.get(id);
            String name = def == null || def.description() == null ? UNKNOWN : def.description();
            Sensitivity sensitivity = def == null ? Sensitivity.FULL : def.sensitivity();
            List<String> errors = new ArrayList<>(validate(id, raw, def));
            Map<String, DEModel> subelements;
            try {
                subelements = subelements(raw, def);
            } catch (IsoParserException e) {
                if (notHexMessage(def).equals(e.getMessage())) {
                    // the hex error already says why the value is unusable; the character-class error repeats it
                    errors.remove(typeError(id, def.type()));
                }
                errors.add(e.getMessage());
                subelements = Map.of();
                // the subelements cannot be classified, so the value may hide a sensitive one
                sensitivity = Sensitivity.FULL;
            }
            fields.put(id, new DEModel(id, name, raw, decode(raw, def), subelements, errors, id, sensitivity));
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Checks a value against its definition: fixed length, maximum length, the limit of the length prefix and the
     * character class of the ISO type ({@code n}, {@code an}, {@code ans}, {@code b}, {@code z}, {@code x+n}).
     * Lengths of binary fields are counted in bytes. Messages never include the value itself.
     */
    private static List<String> validate(String id, String raw, FieldDefinitionModel def) {
        if (raw == null || def == null) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        String type = def.type();
        int length = "b".equals(type) ? raw.length() / 2 : raw.length();
        LengthType lengthType = def.lengthType();
        if (lengthType == LengthType.FIXED && def.length() > 0 && length != def.length()) {
            errors.add("DE" + id + ": length " + length + " differs from the fixed length " + def.length());
        }
        if (def.maxLength() > 0 && length > def.maxLength()) {
            errors.add("DE" + id + ": length " + length + " exceeds the maximum length " + def.maxLength());
        }
        int prefixLimit = lengthType == LengthType.LLVAR ? 99 : lengthType == LengthType.LLLVAR ? 999 : Integer.MAX_VALUE;
        if (length > prefixLimit) {
            errors.add("DE" + id + ": length " + length + " does not fit a " + lengthType + " prefix");
        }
        if (type != null && !matchesType(raw, type)) {
            errors.add(typeError(id, type));
        }
        return errors;
    }

    private static String typeError(String id, String type) {
        return "DE" + id + ": contains characters not allowed for type " + type;
    }

    private static boolean matchesType(String value, String type) {
        return switch (type) {
            case "n" -> isDigits(value);
            case "an" -> value.chars().allMatch(c -> Character.isLetterOrDigit(c) && c < 0x80 || c == ' ');
            case "ans" -> value.chars().allMatch(IsoUtils::isPrintable);
            case "b" -> value.chars().allMatch(c -> Character.digit(c, 16) >= 0);
            case "z" -> value.chars().allMatch(c -> c >= '0' && c <= '9' || "=Dd;?".indexOf(c) >= 0);
            case "x+n" -> !value.isEmpty() && (value.charAt(0) == 'C' || value.charAt(0) == 'D')
                    && isDigits(value.substring(1));
            default -> true;
        };
    }

    /** Printable ISO-8859-1: no C0/C1 control characters and no DEL. */
    private static boolean isPrintable(int c) {
        return c >= 0x20 && c != 0x7F && (c < 0x80 || c >= 0xA0) && c <= 0xFF;
    }

    private static String decode(String raw, FieldDefinitionModel def) {
        if (raw == null || def == null) {
            return raw;
        }
        if ("an".equals(def.type()) || "ans".equals(def.type())) {
            int end = raw.length();
            while (end > 0 && raw.charAt(end - 1) == ' ') {
                end--;
            }
            return raw.substring(0, end);
        }
        return raw;
    }

    private static Map<String, DEModel> subelements(String raw, FieldDefinitionModel def) {
        if (raw == null || def == null) {
            return Map.of();
        }
        return switch (def.subelementFormat()) {
            case TAG_LENGTH_VALUE -> parseTagLengthValue(raw, def);
            case BER_TLV -> parseBerTlv(hexToBytesFor(raw, def), def);
            case NONE -> Map.of();
        };
    }

    /** BER-TLV fields carry the EMV bytes as hex text (the packager templates declare them as character fields). */
    private static byte[] hexToBytesFor(String raw, FieldDefinitionModel def) {
        try {
            return hexToBytes(raw);
        } catch (IsoParserException e) {
            throw new IsoParserException(notHexMessage(def), e);
        }
    }

    private static String notHexMessage(FieldDefinitionModel def) {
        return "Malformed subelements in DE" + (def == null ? null : def.id())
                + ": the value is not hex text (EMV data must be hex encoded)";
    }

    /**
     * Parses ASCII tag + numeric length + value subelements (e.g. DE48). When {@code subelementOffset > 0} the leading
     * characters become subelement {@value FieldDefinitionModel#OFFSET_SUBELEMENT_ID} (the Mastercard Transaction
     * Category Code), named and masked by its subelement definition like any other tag: undeclared, it is
     * {@code "Unknown"} and fully masked.
     *
     * @throws IsoParserException if the data is truncated, a length is not numeric or a tag is repeated
     */
    public static Map<String, DEModel> parseTagLengthValue(String value, FieldDefinitionModel def) {
        int tagLen = def.subelementTagLength();
        int lenLen = def.subelementLengthLength();
        int offset = def.subelementOffset();
        if (tagLen <= 0 || lenLen <= 0 || offset < 0) {
            throw new IsoParserException("Invalid subelement layout for DE" + def.id());
        }
        String malformed = "Malformed subelements in DE" + def.id();
        if (value == null || value.length() < offset) {
            throw new IsoParserException(malformed);
        }
        Map<String, FieldDefinitionModel> known = subelementIndex(def);
        Map<String, DEModel> result = new LinkedHashMap<>();
        if (offset > 0) {
            String prefix = FieldDefinitionModel.OFFSET_SUBELEMENT_ID;
            result.put(prefix, subelement(def, known, prefix, value.substring(0, offset)));
        }
        int pos = offset;
        while (pos < value.length()) {
            if (pos + tagLen + lenLen > value.length()) {
                throw new IsoParserException(malformed);
            }
            String tag = value.substring(pos, pos + tagLen);
            String lenText = value.substring(pos + tagLen, pos + tagLen + lenLen);
            if (!isDigits(lenText)) {
                throw new IsoParserException(malformed);
            }
            int len = Integer.parseInt(lenText);
            int start = pos + tagLen + lenLen;
            if (start + len > value.length()) {
                throw new IsoParserException(malformed);
            }
            String data = value.substring(start, start + len);
            putUnique(result, tag, subelement(def, known, tag, data), malformed);
            pos = start + len;
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Parses BER-TLV data (e.g. DE55 EMV). Parsing is flat: constructed tags are not descended into, their value is
     * kept as raw hex. Supports multi-byte tags and 1-2 byte long-form lengths. {@code 0x00} and {@code 0xFF} bytes
     * where a tag would start are padding (EMV Book 3) and are skipped.
     *
     * @throws IsoParserException if the data is truncated, a length form is unsupported or a tag is repeated
     */
    public static Map<String, DEModel> parseBerTlv(byte[] data, FieldDefinitionModel def) {
        String malformed = "Malformed subelements in DE" + def.id();
        if (data == null) {
            throw new IsoParserException(malformed);
        }
        Map<String, FieldDefinitionModel> known = subelementIndex(def);
        Map<String, DEModel> result = new LinkedHashMap<>();
        int pos = 0;
        while (pos < data.length) {
            int first = data[pos] & 0xFF;
            if (first == 0x00 || first == 0xFF) {
                pos++;
                continue;
            }
            int tagStart = pos++;
            if ((first & 0x1F) == 0x1F) {
                int next;
                do {
                    if (pos >= data.length) {
                        throw new IsoParserException(malformed);
                    }
                    next = data[pos++] & 0xFF;
                } while ((next & 0x80) != 0);
            }
            String tag = bytesToHex(data, tagStart, pos - tagStart);

            if (pos >= data.length) {
                throw new IsoParserException(malformed);
            }
            int len = data[pos++] & 0xFF;
            if (len >= 0x80) {
                int count = len & 0x7F;
                if (count < 1 || count > 2 || pos + count > data.length) {
                    throw new IsoParserException(malformed);
                }
                len = 0;
                for (int i = 0; i < count; i++) {
                    len = (len << 8) | (data[pos++] & 0xFF);
                }
            }
            if (pos + len > data.length) {
                throw new IsoParserException(malformed);
            }
            String hexValue = bytesToHex(data, pos, len);
            putUnique(result, tag, subelement(def, known, tag, hexValue), malformed);
            pos += len;
        }
        return Collections.unmodifiableMap(result);
    }

    private static void putUnique(Map<String, DEModel> result, String tag, DEModel sub, String malformed) {
        if (result.putIfAbsent(tag, sub) != null) {
            throw new IsoParserException(malformed + ": duplicate tag " + tag);
        }
    }

    private static Map<String, FieldDefinitionModel> subelementIndex(FieldDefinitionModel def) {
        Map<String, FieldDefinitionModel> index = new LinkedHashMap<>();
        for (FieldDefinitionModel sub : def.subelements()) {
            if (sub != null && sub.id() != null) {
                index.put(sub.id().toUpperCase(Locale.ROOT), sub);
            }
        }
        return index;
    }

    /**
     * A subelement at path {@code parent/tag}, named and masked by its definition. An undeclared tag is
     * {@code "Unknown"} and fully masked.
     */
    private static DEModel subelement(FieldDefinitionModel parent, Map<String, FieldDefinitionModel> known,
                                      String tag, String value) {
        FieldDefinitionModel sub = known.get(tag.toUpperCase(Locale.ROOT));
        String name = sub == null || sub.description() == null ? UNKNOWN : sub.description();
        Sensitivity sensitivity = sub == null ? Sensitivity.FULL : sub.sensitivity();
        return new DEModel(tag, name, value, value, null, null, parent.id() + "/" + tag, sensitivity);
    }

    /**
     * Builds an {@link ISOMsg} from parsed fields. Binary fields ({@code type == "b"}) are set from their hex raw value,
     * everything else from the raw string.
     */
    public static ISOMsg toIsoMsg(String mti, Map<String, DEModel> fields, Map<String, FieldDefinitionModel> definitions,
                                  GenericPackager packager) {
        ISOMsg msg = new ISOMsg();
        msg.setPackager(packager);
        try {
            msg.setMTI(mti);
            for (Map.Entry<String, DEModel> entry : fields.entrySet()) {
                int id = Integer.parseInt(entry.getKey());
                String raw = entry.getValue().rawValue();
                FieldDefinitionModel def = definitions == null ? null : definitions.get(entry.getKey());
                if (def != null && "b".equals(def.type())) {
                    msg.set(id, hexToBytes(raw));
                } else {
                    msg.set(id, raw);
                }
            }
        } catch (ISOException | NumberFormatException e) {
            throw new IsoParserException("Unable to build ISO message: " + e.getMessage(), e);
        }
        return msg;
    }
}
