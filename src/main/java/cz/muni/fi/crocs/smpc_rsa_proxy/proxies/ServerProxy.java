package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Test class.
 * Note: If simulator cannot be started try adding "-noverify" JVM parameter
 *
 * @author Petr Svenda, Dusan Klinec (ph4r05)
 */
public class ServerProxy extends AbstractProxy {
    public static final byte CLA_RSA_SMPC_SERVER = (byte) 0x83;

    public static final byte INS_GENERATE_KEYS = 0x10;
    public static final byte INS_SET_CLIENT_KEYS = 0x12;
    public static final byte INS_GET_PUBLIC_N = 0x14;
    public static final byte INS_SET_CLIENT_SIGNATURE = 0x16;
    public static final byte INS_SIGNATURE = 0x18;
    public static final byte INS_GET_SIGNATURE = 0x20;
    public static final byte INS_RESET = 0x22;

    public static final byte P1_SET_N1 = 0x00;
    public static final byte P1_SET_D1_SERVER = 0x01;

    public static final byte P1_SET_MESSAGE = 0x00;
    public static final byte P1_SET_SIGNATURE = 0x01;

    private static String APPLET_AID = "0102030405060708090304";

    /**
     *
     * @throws CardException
     */
    public ServerProxy() throws CardException {
        super(Util.hexStringToByteArray(APPLET_AID));
    }

    @Override
    public void generateKeys() throws CardException, IOException {
        handleError(transmit(new CommandAPDU(
                CLA_RSA_SMPC_SERVER, INS_GENERATE_KEYS, 0x00, 0x00
        )), "Key generation");

        setClientKeys();
        getPublicModulus();
    }


    public void setClientKeys() throws IOException, CardException {
        ArrayList<CommandAPDU> APDU_SET_D1_SERVER = new ArrayList<>();
        ArrayList<CommandAPDU> APDU_SET_N1 = new ArrayList<>();

        try (InputStream in = new FileInputStream(CLIENT_KEYS_SERVER_SHARE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            byte[] num = Util.hexStringToByteArray(reader.readLine());
            BigInteger d = new BigInteger(1, num);

            setNumber(APDU_SET_D1_SERVER, num, CLA_RSA_SMPC_SERVER, INS_SET_CLIENT_KEYS, P1_SET_D1_SERVER);

            num = Util.hexStringToByteArray(reader.readLine());
            BigInteger n = new BigInteger(1, num);

            if (num.length != PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException(String.format("Modulus is not a %d-bit number.", PARTIAL_MODULUS_LENGTH));

            if (d.compareTo(n) > 0)
                throw new IllegalArgumentException("Private key cannot be larger than modulus.");

            setNumber(APDU_SET_N1, num, CLA_RSA_SMPC_SERVER, INS_SET_CLIENT_KEYS, P1_SET_N1);

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", CLIENT_KEYS_SERVER_SHARE_FILE));
        }

        transmitNumber(APDU_SET_D1_SERVER, "Set D''1");
        transmitNumber(APDU_SET_N1, "Set N1");
    }

    public ArrayList<ResponseAPDU> getPublicModulus() throws CardException, IOException {
        ArrayList<ResponseAPDU> res = new ArrayList<>();

        // zjednodusit
        res.add(transmit(new CommandAPDU(
            CLA_RSA_SMPC_SERVER, INS_GET_PUBLIC_N, 0x00, P2_PART_0
        )));

        res.add(transmit(new CommandAPDU(
                CLA_RSA_SMPC_SERVER, INS_GET_PUBLIC_N, 0x00, P2_PART_1
        )));

        try (OutputStream out = new FileOutputStream(PUBLIC_KEY_FILE)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(String.format("%s%n", "10001")); //todo hardcoded E
            for (ResponseAPDU r: res) {
                writer.write(Util.toHex(r.getData()));
            }
            writer.write(System.lineSeparator());
            writer.flush();
        }

        return res;
    }

    @Override
    public void signMessage() throws IOException, CardException {
        ArrayList<CommandAPDU> APDU_SET_MESSAGE = new ArrayList<>();
        ArrayList<CommandAPDU> APDU_SET_CLIENT_SIGNATURE = new ArrayList<>();
        String message;

        try (InputStream in = new FileInputStream(CLIENT_SIG_SHARE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            message = reader.readLine();
            byte[] num = Util.hexStringToByteArray(message);

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException("Message cannot be larger than the modulus.");

            setNumber(APDU_SET_MESSAGE, num, CLA_RSA_SMPC_SERVER, INS_SET_CLIENT_SIGNATURE, P1_SET_MESSAGE);

            num = Util.hexStringToByteArray(reader.readLine());

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException("Client signature share cannot be larger than the modulus.");

            setNumber(APDU_SET_CLIENT_SIGNATURE, num, CLA_RSA_SMPC_SERVER, INS_SET_CLIENT_SIGNATURE, P1_SET_SIGNATURE);

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", CLIENT_KEYS_CLIENT_SHARE_FILE));
        } catch (FileNotFoundException e) {
            System.err.println("TODO:"); //TODO:
            throw e;
        }

        transmitNumber(APDU_SET_MESSAGE, "Set message");
        transmitNumber(APDU_SET_CLIENT_SIGNATURE, "Set client signature share");

        handleError(transmit(new CommandAPDU(
                CLA_RSA_SMPC_SERVER, INS_SIGNATURE, NONE, NONE
        )), "Signature");

        ArrayList<ResponseAPDU> responses = new ArrayList<>();
        responses.add(transmit(new CommandAPDU(
                CLA_RSA_SMPC_SERVER, INS_GET_SIGNATURE, 0x00, P2_PART_0
        )));

        responses.add(transmit(new CommandAPDU(
                CLA_RSA_SMPC_SERVER, INS_GET_SIGNATURE, 0x00, P2_PART_1
        )));

        try (OutputStream out = new FileOutputStream(FINAL_SIG_FILE)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(String.format("%s%n", message));

            for (ResponseAPDU r:responses) {
                writer.write(Util.toHex(r.getData()));
            }

            writer.write(System.lineSeparator());
            writer.flush();
        }
    }

    @Override
    public void reset() throws CardException {
        resetHelper(CLA_RSA_SMPC_SERVER, INS_RESET);
    }

}
