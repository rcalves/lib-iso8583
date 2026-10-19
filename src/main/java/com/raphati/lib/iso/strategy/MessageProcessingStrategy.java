package com.raphati.lib.iso.strategy;

import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.loader.IsoDefinitionLoader;
import com.raphati.lib.iso.model.DEModel;
import com.raphati.lib.iso.model.IsoModel;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;
import com.raphati.lib.iso.utils.IsoUtils;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;

/**
 * Parses messages of one card network and message type. The parsing flow is shared (template method);
 * subclasses only supply the network, the message type and the default packager template.
 */
public abstract class MessageProcessingStrategy {

    private final String templatePath;
    private volatile GenericPackager packager;

    protected MessageProcessingStrategy(String templatePath) {
        this.templatePath = Objects.requireNonNull(templatePath, "templatePath");
    }

    public abstract CardNetworkType cardNetwork();

    public abstract MessageType messageType();

    public String templatePath() {
        return templatePath;
    }

    /**
     * The packager of this strategy, loaded from {@link #templatePath()} on first use and kept by this instance.
     *
     * @throws IsoParserException if the template cannot be loaded
     */
    public GenericPackager packager() {
        GenericPackager result = packager;
        if (result == null) {
            synchronized (this) {
                result = packager;
                if (result == null) {
                    result = IsoUtils.loadPackager(templatePath);
                    packager = result;
                }
            }
        }
        return result;
    }

    /**
     * Parses the packed wire bytes of a message. Every byte must belong to the message.
     */
    public IsoModel parse(byte[] data, IsoDefinitionLoader loader) {
        return parse(data, loader, false);
    }

    /**
     * Parses a raw (not hex encoded) message, turning the string into wire bytes with {@code charset}. Whitespace
     * after the last field (for example the line break of a message copied from a log) is ignored.
     */
    public IsoModel parseAscii(String rawMessage, Charset charset, IsoDefinitionLoader loader) {
        if (rawMessage == null) {
            throw new IsoParserException("ISO message must not be null");
        }
        Objects.requireNonNull(charset, "charset");
        return parse(rawMessage.getBytes(charset), loader, true);
    }

    private IsoModel parse(byte[] data, IsoDefinitionLoader loader, boolean allowTrailingWhitespace) {
        Objects.requireNonNull(loader, "loader");
        ISOMsg msg = IsoUtils.unpack(data, packager(), allowTrailingWhitespace);
        String mti;
        try {
            mti = msg.getMTI();
        } catch (ISOException e) {
            throw new IsoParserException("Message has no MTI", e);
        }
        return new IsoModel(IsoUtils.parseMti(mti), IsoUtils.parseBitmap(msg),
                IsoUtils.toFieldMap(msg, loader.load(cardNetwork())));
    }

    /**
     * Parses a message given as the hex encoding of its packed bytes (whitespace is ignored).
     */
    public IsoModel parse(String isoMessage, IsoDefinitionLoader loader) {
        return parse(IsoUtils.hexToBytes(isoMessage), loader);
    }

    public Map<String, DEModel> extractFields(String isoMessage, IsoDefinitionLoader loader) {
        return parse(isoMessage, loader).fields();
    }

    /**
     * Packs a message (MTI and data elements from their raw values) back into wire bytes. The bitmap is rebuilt from
     * the fields present ({@link IsoModel#bitmap()} is not used), so a non-canonical wire bitmap, such as bit 1 set
     * with an all-zero secondary bitmap, is packed in its canonical form.
     *
     * @throws IsoParserException if the message cannot be built or packed
     */
    public byte[] pack(IsoModel message, IsoDefinitionLoader loader) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(loader, "loader");
        if (message.mti() == null) {
            throw new IsoParserException("Message has no MTI");
        }
        return IsoUtils.pack(IsoUtils.toIsoMsg(message.mti().value(), message.fields(),
                loader.load(cardNetwork()), packager()));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[network=" + cardNetwork() + ", type=" + messageType()
                + ", template=" + templatePath + "]";
    }
}
