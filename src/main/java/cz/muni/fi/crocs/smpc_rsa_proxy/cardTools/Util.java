package cz.muni.fi.crocs.smpc_rsa_proxy.cardTools;

/**
 *
 * @author Vasilios Mavroudis and Petr Svenda
 */
public class Util {

    public static String toHex(byte[] bytes) {
        return toHex(bytes, 0, bytes.length);
    }

    public static String toHex(byte[] bytes, int offset, int len) {
        StringBuilder result = new StringBuilder();

        for (int i = offset; i < offset + len; i++) {
            result.append(String.format("%02X", bytes[i]));
        }

        return result.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        String sanitized = s.replace(" ", "");
        byte[] b = new byte[sanitized.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(sanitized.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }    

    public static byte[] trimLeadingZeroes(byte[] array) {
        short startOffset = 0;
        for (byte b : array) {
            if (b != 0)
                break;

            startOffset++;
        }

        byte[] result = new byte[array.length - startOffset];
        System.arraycopy(array, startOffset, result, 0, array.length - startOffset);
        return result;
    }

}
