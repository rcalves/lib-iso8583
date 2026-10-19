package com.raphati.lib.iso;

import com.raphati.lib.iso.type.CardNetworkType;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;

/**
 * Builds synthetic test messages. All values are fake (PAN 5555555555554444 is a public test number).
 */
public final class TestMessages {

    public static final String PAN = "5555555555554444";
    public static final String MASKED_PAN = "555555******4444";
    public static final String GENERIC_DE48 = "0105HELLO0203ABC";
    /** TCC {@code R} + SE42 (len 07) {@code 0102105} + SE92 (len 03) {@code 123}. */
    public static final String MASTERCARD_DE48 = "R420701021059203123";
    public static final String DE55 = "9F02060000000010005F2A0209869A03261004";
    public static final String DE102 = "12345678";

    private TestMessages() {
    }

    public static ISOMsg build(GenericPackager packager, CardNetworkType network) {
        return build(packager, network, false);
    }

    public static ISOMsg build(GenericPackager packager, CardNetworkType network, boolean withSecondaryBitmap) {
        try {
            ISOMsg msg = new ISOMsg();
            msg.setPackager(packager);
            msg.setMTI("0100");
            msg.set(2, PAN);
            msg.set(3, "000000");
            msg.set(4, "000000001000");
            msg.set(7, "1004183326");
            msg.set(11, "123456");
            msg.set(22, "051");
            msg.set(41, "TERM0001");
            msg.set(42, "MERCHANT0000001");
            msg.set(48, network == CardNetworkType.MASTERCARD ? MASTERCARD_DE48 : GENERIC_DE48);
            msg.set(49, "986");
            msg.set(55, DE55);
            if (withSecondaryBitmap) {
                msg.set(102, DE102);
            }
            return msg;
        } catch (ISOException e) {
            throw new IllegalStateException(e);
        }
    }
}
