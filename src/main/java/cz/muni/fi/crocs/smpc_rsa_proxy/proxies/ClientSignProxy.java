package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * TODO:Test class.
 *
 * Note: If simulator cannot be started try adding "-noverify" JVM parameter
 *
 * @author Lukáš Zaoral
 */
public class ClientSignProxy extends AbstractClientProxy {
    public static final byte CLA_RSA_SMPC_CLIENT_SIGN = (byte) 0x80;

    public static final byte INS_SET_KEYS = 0x10;
    public static final byte INS_SET_MESSAGE = 0x12;
    public static final byte INS_SIGNATURE = 0x14;
    public static final byte INS_RESET = 0x16;

    public static final byte P1_SET_D1_CLIENT = 0x00;
    public static final byte P1_SET_N1 = 0x01;

    public static String APPLET_AID = "0102030405060708090102";

    /**
     *
     * @throws CardException
     */
    public ClientSignProxy() throws CardException {
        super(Util.hexStringToByteArray(APPLET_AID));
    }

    @Override
    public void generateKeys() throws IOException, CardException {
        setKeys();
    }

    @Override
    public void signMessage() throws IOException, CardException {
        clientSignMessage(CLA_RSA_SMPC_CLIENT_SIGN, INS_SET_MESSAGE, INS_SIGNATURE);
    }

    @Override
    public void reset() throws CardException {
        resetHelper(CLA_RSA_SMPC_CLIENT_SIGN, INS_RESET);
    }

    /**
     *
     * @throws IOException
     * @throws CardException
     */
    public void setKeys() throws IOException, CardException {
        ArrayList<CommandAPDU> cmdD1Client = new ArrayList<>();
        ArrayList<CommandAPDU> cmdN1 = new ArrayList<>();

        loadKeys(cmdD1Client, cmdN1);

        printAndFlush("Transmitting keys...");

        try {
            transmitNumber(cmdD1Client, "Set D'1");
            transmitNumber(cmdN1, "Set N1");
        } catch (CardException e) {
            if (e.getMessage().contains(SW_COMMAND_NOT_ALLOWED))
                System.err.println("Keys have already been set. Please, reset the card first.");

            throw e;
        }

        printOK();
    }

    /**
     *
     * @param cmdD1
     * @param cmdN1
     * @throws IOException
     */
    private void loadKeys(ArrayList<CommandAPDU> cmdD1, ArrayList<CommandAPDU> cmdN1) throws IOException {
        printAndFlush("Loading keys...");

        try (InputStream in = new FileInputStream(CLIENT_KEYS_CLIENT_SHARE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            byte[] num = Util.hexStringToByteArray(reader.readLine());
            BigInteger d1Client = new BigInteger(1, num);

            setNumber(cmdD1, num, CLA_RSA_SMPC_CLIENT_SIGN, INS_SET_KEYS, P1_SET_D1_CLIENT);

            num = Util.hexStringToByteArray(reader.readLine());
            BigInteger n1 = new BigInteger(1, num);

            if (num.length != PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException(String.format("Modulus is not a %d-bit number.", PARTIAL_MODULUS_LENGTH / 2));

            if (d1Client.compareTo(n1) > 0)
                throw new IllegalArgumentException("Private key cannot be larger than modulus.");

            setNumber(cmdN1, num, CLA_RSA_SMPC_CLIENT_SIGN, INS_SET_KEYS, P1_SET_N1);

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", CLIENT_KEYS_CLIENT_SHARE_FILE));

        } catch (FileNotFoundException e) {
            printNOK();
            System.err.println("The keys have not been generated. Run the reference implementation first.");
            throw e;
        }

        printOK();
    }

}
