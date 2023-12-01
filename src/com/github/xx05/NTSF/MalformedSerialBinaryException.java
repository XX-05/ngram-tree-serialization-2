package com.github.xx05.NTSF;

/**
 * Exception class used for signaling errors related to malformed binary serialization data.
 * This exception is typically thrown during the deserialization process when the binary data
 * cannot be properly parsed or does not conform to the expected format.
 */
public class MalformedSerialBinaryException extends Exception {
    public MalformedSerialBinaryException(String msg) {
        super(msg);
    }
}
