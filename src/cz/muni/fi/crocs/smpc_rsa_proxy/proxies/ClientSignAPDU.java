package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Test class.
 *
 * Note: If simulator cannot be started try adding "-noverify" JVM parameter
 *
 * @author Petr Svenda, Dusan Klinec (ph4r05), Lukas Zaoral
 */
public class ClientSignAPDU extends AbstractAPDU {
    public static final byte CLA_RSA_SMPC_CLIENT_SIGN = (byte) 0x80;

    public static final byte INS_SET_KEYS = 0x10;
    public static final byte INS_SET_MESSAGE = 0x12;
    public static final byte INS_SIGNATURE = 0x14;
    public static final byte INS_RESET = 0x16;

    public static final byte P1_SET_D = 0x00;
    public static final byte P1_SET_N = 0x01;

    private static String APPLET_AID = "0102030405060708090102";

    /**
     *
     * @throws CardException
     */
    public ClientSignAPDU() throws CardException {
        super(Util.hexStringToByteArray(APPLET_AID));
    }

    /**
     *
     * @throws IOException
     * @throws CardException
     */
    public void setKeys() throws IOException, CardException {
        ArrayList<CommandAPDU> APDU_SET_N = new ArrayList<>();
        ArrayList<CommandAPDU> APDU_SET_D = new ArrayList<>();

        try (InputStream in = new FileInputStream(CLIENT_KEYS_CLIENT_SHARE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            byte[] num = Util.hexStringToByteArray(reader.readLine());
            BigInteger d = new BigInteger(1, num);

            setNumber(APDU_SET_D, num, CLA_RSA_SMPC_CLIENT_SIGN, INS_SET_KEYS, P1_SET_D);

            num = Util.hexStringToByteArray(reader.readLine());
            BigInteger n = new BigInteger(1, num);

            if (num.length != PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException(String.format("Modulus is not a %d-bit number.", PARTIAL_MODULUS_LENGTH / 2));

            if (d.compareTo(n) > 0)
                throw new IllegalArgumentException("Private key cannot be larger than modulus.");

            setNumber(APDU_SET_N, num, CLA_RSA_SMPC_CLIENT_SIGN, INS_SET_KEYS, P1_SET_N);

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", CLIENT_KEYS_CLIENT_SHARE_FILE));
        }

        transmitNumber(APDU_SET_D, "Set D'1");
        transmitNumber(APDU_SET_N, "Set N1");
    }

    @Override
    public void generateKeys() throws IOException, CardException {
        setKeys();
    }

    @Override
    public void signMessage() throws IOException, CardException {
        String message;
        ArrayList<CommandAPDU> APDU_MESSAGE = new ArrayList<>();

        try (InputStream in = new FileInputStream(MESSAGE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            message = reader.readLine();
            byte[] num = Util.hexStringToByteArray(message);

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException("Message key cannot be larger than modulus.");

            setNumber(APDU_MESSAGE, num, CLA_RSA_SMPC_CLIENT_SIGN, INS_SET_MESSAGE, NONE);

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", MESSAGE_FILE));
        }

        transmitNumber(APDU_MESSAGE, "Set message");

        ResponseAPDU res = transmit(new CommandAPDU(
                CLA_RSA_SMPC_CLIENT_SIGN, INS_SIGNATURE, NONE, NONE, PARTIAL_MODULUS_LENGTH
        ));
        handleError(res, "Signing");

        try (OutputStream out = new FileOutputStream(CLIENT_SIG_SHARE_FILE)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(String.format(
                    "%s%n%s%n", message, Util.toHex(Util.trimLeadingZeroes(res.getData()))
            ));
            writer.flush();
        }

    }

}
