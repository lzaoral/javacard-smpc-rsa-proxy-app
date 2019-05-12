package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.*;
import java.util.ArrayList;

/**
 * Test class.
 * Note: If simulator cannot be started try adding "-noverify" JVM parameter
 *
 * @author Petr Svenda, Dusan Klinec (ph4r05), Lukas Zaoral
 */
public class ClientFullAPDU extends AbstractAPDU {
    public static final byte CLA_RSA_SMPC_CLIENT = (byte) 0x81;

    public static final byte INS_GENERATE_KEYS = 0x10;
    public static final byte INS_GET_KEYS = 0x12;
    public static final byte INS_SET_MESSAGE = 0x14;
    public static final byte INS_SIGNATURE = 0x16;
    public static final byte INS_RESET = 0x18;

    public static final byte P1_GET_N = 0x00;
    public static final byte P1_GET_D1_SERVER = 0x01;

    private static String APPLET_AID = "0102030405060708090203";

    /**
     *
     * @throws CardException
     */
    public ClientFullAPDU() throws CardException {
        super(Util.hexStringToByteArray(APPLET_AID));
    }

    @Override
    public void generateKeys() throws CardException, IOException {
        handleError(transmit(new CommandAPDU(
                CLA_RSA_SMPC_CLIENT, INS_GENERATE_KEYS, NONE, NONE
        )), "Key generation");

        getKeys();
    }

    /**
     *
     * @throws IOException
     * @throws CardException
     */
    public void getKeys() throws IOException, CardException {
        ResponseAPDU dServer = transmit(new CommandAPDU(
                CLA_RSA_SMPC_CLIENT, INS_GET_KEYS, P1_GET_D1_SERVER, NONE, PARTIAL_MODULUS_LENGTH
        ));
        handleError(dServer, "Get d1Server");

        ResponseAPDU n = transmit(new CommandAPDU(
                CLA_RSA_SMPC_CLIENT, INS_GET_KEYS, P1_GET_N, NONE, PARTIAL_MODULUS_LENGTH
        ));
        handleError(n, "Get n");

        try (OutputStream out = new FileOutputStream(CLIENT_KEYS_SERVER_SHARE_FILE)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));

            writer.write(String.format("%s%n%s%n", Util.toHex(Util.trimLeadingZeroes(dServer.getData())),
                    Util.toHex(Util.trimLeadingZeroes(n.getData()))));

            writer.flush();
        }
    }

    @Override
    public void signMessage() throws CardException, IOException  {
        ArrayList<CommandAPDU> APDU_MESSAGE = new ArrayList<>();
        String message;

        try (InputStream in = new FileInputStream(MESSAGE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            message = reader.readLine();
            byte[] num = Util.hexStringToByteArray(message);

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException("Message key cannot be longer than modulus.");

            setNumber(APDU_MESSAGE, num, CLA_RSA_SMPC_CLIENT, INS_SET_MESSAGE, NONE);

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", MESSAGE_FILE));
        }

        for (CommandAPDU cmd : APDU_MESSAGE)
            handleError(transmit(cmd), "Set message");

        ResponseAPDU res = transmit(new CommandAPDU(
                CLA_RSA_SMPC_CLIENT, INS_SIGNATURE, NONE, NONE, PARTIAL_MODULUS_LENGTH
        ));
        handleError(res, "Signing");

        String data = Util.toHex(Util.trimLeadingZeroes(res.getData()));

        try (OutputStream out = new FileOutputStream(CLIENT_SIG_SHARE_FILE)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(String.format("%s%n%s%n", message, data));
            writer.flush();
        }
    }

}
