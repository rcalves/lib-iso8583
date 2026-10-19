package com.raphati.lib.iso.model;

import com.raphati.lib.iso.type.mti.ClassType;
import com.raphati.lib.iso.type.mti.FunctionType;
import com.raphati.lib.iso.type.mti.OriginType;
import com.raphati.lib.iso.type.mti.VersionType;

/**
 * Decoded Message Type Indicator (e.g. {@code 0100}).
 *
 * @param value        the raw 4-digit MTI
 * @param version      position 1, ISO version
 * @param messageClass position 2, message class
 * @param function     position 3, message function
 * @param origin       position 4, message origin
 */
public record MtiModel(String value, VersionType version, ClassType messageClass, FunctionType function,
                       OriginType origin) {
}
