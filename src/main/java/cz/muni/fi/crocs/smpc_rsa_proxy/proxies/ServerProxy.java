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

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link ServerProxy} class represents a server party
 * in the SMPC RSA scheme.
 *
 * @author Lukas Zaoral
 */
public class ServerProxy extends AbstractProxy {

    private static final byte CLA_RSA_SMPC_SERVER = (byte) 0x80;

    /**
     * Instruction codes
     */
    private static final byte INS_GENERATE_KEYS = 0x10;
    private static final byte INS_SET_CLIENT_KEYS = 0x12;
    private static final byte INS_GET_PUBLIC_MODULUS = 0x14;
    private static final byte INS_SET_CLIENT_SIGNATURE = 0x16;
    private static final byte INS_SIGNATURE = 0x18;
    private static final byte INS_GET_SIGNATURE = 0x20;
    private static final byte INS_RESET = 0x22;

    /**
     * P1 parameters of the INS_SET_CLIENT_KEYS instruction
     */
    private static final byte P1_SET_N1 = 0x00;
    private static final byte P1_SET_D1_SERVER = 0x01;

    /**
     * P1 parameters of the INS_SET_CLIENT_SIGNATURE instruction
     */
    private static final byte P1_SET_MESSAGE = 0x00;
    private static final byte P1_SET_SIGNATURE = 0x01;

    /**
     * Applet ID
     */
    private static final String APPLET_AID = "0102030405060708090104";

    /**
     * Connects to a card a selects the server applet.
     *
     * @throws CardException if the terminal or card are missing
     *                       or the applet is not installed
     */
    public ServerProxy() throws CardException {
        super(Util.hexStringToByteArray(APPLET_AID));
    }

    @Override
    public void generateKeys() throws CardException, IOException {
        printAndFlush("Generating keys...");

        int ret = transmit(new CommandAPDU(CLA_RSA_SMPC_SERVER, INS_GENERATE_KEYS, NONE, NONE),
                "Keygen", SW_COMMAND_NOT_ALLOWED).getSW();
        if (ret == SW_COMMAND_NOT_ALLOWED)
            throw new CardException("Keys have already been set. Please, reset the card first.");

        printOK();
        setClientKeys();
        getPublicModulus();
    }

    @Override
    public void signMessage() throws IOException, CardException {
        String message = loadClientSignature();

        printAndFlush("Signing...");
        transmit(new CommandAPDU(CLA_RSA_SMPC_SERVER, INS_SIGNATURE, NONE, NONE), "Sign");
        printOK();

        getFinalSignature(message);
    }

    @Override
    public void reset() throws CardException {
        resetHelper(CLA_RSA_SMPC_SERVER, INS_RESET);
    }

    /**
     * Sets the server share of client keys.
     *
     * @throws CardException if something on the smart card fails
     * @throws IOException   if the file with keys is missing or cannot be read
     */
    private void setClientKeys() throws CardException, IOException {
        List<CommandAPDU> cmdD1Server = new ArrayList<>();
        List<CommandAPDU> cmdN1 = new ArrayList<>();

        loadClientKeys(cmdD1Server, cmdN1);

        printAndFlush("Transmitting client keys share...");
        transmitBatch(cmdD1Server, "Set D''1");
        transmitBatch(cmdN1, "Set N1");
        printOK();
    }

    /**
     * Loads the client keys to the given {@code cmdD1Server} and {@code cmdN1} lists.
     *
     * @param cmdD1Server list of commands to set the server private exponent share
     * @param cmdN1       list of commands to set the client partial modulus
     * @throws IOException if the file with keys is missing or cannot be read
     */
    private void loadClientKeys(List<CommandAPDU> cmdD1Server, List<CommandAPDU> cmdN1) throws IOException {
        printAndFlush("Loading client keys...");

        try {
            loadFile(CLIENT_KEYS_SERVER_SHARE_FILE, cmdD1Server, cmdN1, CLA_RSA_SMPC_SERVER, INS_SET_CLIENT_KEYS,
                    P1_SET_D1_SERVER, P1_SET_N1);
        } catch (FileNotFoundException e) {
            System.err.println("The keys have not been generated. Generate the client keys first.");
            throw e;
        }

        printOK();
    }

    /**
     * Gets and stores the public modulus.
     *
     * @throws CardException if something on the smart card fails
     * @throws IOException   if the file with public modulus cannot be created or written to
     */
    private void getPublicModulus() throws CardException, IOException {
        printAndFlush("Storing public modulus...");
        storeMultipartData(PUBLIC_KEY_FILE, Util.toHexTrimmed(E), "GetModulus", CLA_RSA_SMPC_SERVER,
                INS_GET_PUBLIC_MODULUS);
        printOK();
    }

    /**
     * Loads the message and client signature from the {@code CLIENT_SIG_SHARE_FILE} file
     * and transmits it to the smart card.
     *
     * @return hex string with the message
     * @throws CardException if something on the smart card fails
     * @throws IOException   if the file with message is missing or cannot be read
     */
    private String loadClientSignature() throws CardException, IOException {
        printAndFlush("Loading client signature...");

        List<CommandAPDU> cmdMessage = new ArrayList<>();
        List<CommandAPDU> cmdClientSig = new ArrayList<>();
        String message;

        try (InputStream in = new FileInputStream(CLIENT_SIG_SHARE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            message = reader.readLine();
            byte[] num = Util.hexStringToByteArray(message);

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException("Message cannot be larger than the modulus.");

            cmdMessage.addAll(splitArrayToCmd(num, CLA_RSA_SMPC_SERVER, INS_SET_CLIENT_SIGNATURE, P1_SET_MESSAGE));

            num = Util.hexStringToByteArray(reader.readLine());

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException("Client signature share cannot be larger than the modulus.");

            cmdClientSig.addAll(splitArrayToCmd(num, CLA_RSA_SMPC_SERVER, INS_SET_CLIENT_SIGNATURE, P1_SET_SIGNATURE));

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", CLIENT_SIG_SHARE_FILE));

        } catch (FileNotFoundException e) {
            System.err.println("The client signature share has not been computed. Run the client first.");
            throw e;
        }

        printOK();
        sendClientSignature(cmdMessage, cmdClientSig);
        return message;
    }

    /**
     * Transmits the message and client signature share to the smart card.
     *
     * @param cmdMessage   list of commands to set the message
     * @param cmdClientSig list of commands to set the client signature share
     * @throws CardException if something on the smart card fails
     */
    private void sendClientSignature(List<CommandAPDU> cmdMessage, List<CommandAPDU> cmdClientSig)
            throws CardException {
        printAndFlush("Transmitting client signature...");
        int ret = transmitBatch(cmdMessage, "Set message", SW_CONDITIONS_NOT_SATISFIED).get(0).getSW();
        if (ret == SW_CONDITIONS_NOT_SATISFIED)
            throw new CardException("Keys have not been generated/set yet.");

        transmitBatch(cmdClientSig, "Set client signature share");
        printOK();
    }

    /**
     * Gets and stores the final signature.
     *
     * @param message hex string with message
     * @throws CardException if something on the smart card fails
     * @throws IOException   if the file with final signature cannot be created or written to
     */
    private void getFinalSignature(String message) throws CardException, IOException {
        printAndFlush("Storing final signature...");
        storeMultipartData(FINAL_SIG_FILE, message, "GetFinalSig", CLA_RSA_SMPC_SERVER, INS_GET_SIGNATURE);
        printOK();
    }


}
