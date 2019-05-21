package cz.muni.fi.crocs.smpc_rsa_proxy.cardTools;

import java.util.Arrays;

/**
 * The {@link Util} static class provides tools for conversion
 * between byte arrays and hex strings.
 *
 * @author Vasilios Mavroudis, Petr Svenda, adapted by Lukas Zaoral
 */
public class Util {

    /**
     * Converts the given byte array to a hex string
     *
     * @param bytes byte array
     * @return converted hex string
     */
    public static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte b : bytes)
            result.append(String.format("%02X", b));

        return result.toString();
    }

    /**
     * Converts and trims the given byte array to a hex string.
     * The input array is unchanged.
     *
     * @param bytes byte array
     * @return converted and trimmed hex string
     */
    public static String toHexTrimmed(byte[] bytes) {
        return toHex(trimLeadingZeroes(bytes));
    }

    /**
     * Converts the given hex string to a byte array
     *
     * @param str hex string
     * @return converted byte array
     */
    public static byte[] hexStringToByteArray(String str) {
        String sanitized = str.replaceAll("\\s+", "");
        byte[] b = new byte[sanitized.length() / 2];

        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            b[i] = (byte) Integer.parseInt(sanitized.substring(index, index + 2), 16);
        }

        return trimLeadingZeroes(b);
    }

    /**
     * Removes leading zero bytes from given byte array
     *
     * @param array byte array
     * @return trimmed byte array
     */
    private static byte[] trimLeadingZeroes(byte[] array) {
        int startOffset = 0;

        while (array[startOffset] == 0x00)
            startOffset++;

        return Arrays.copyOfRange(array, startOffset, array.length);
    }

}
