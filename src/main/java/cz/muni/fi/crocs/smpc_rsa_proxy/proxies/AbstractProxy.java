package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.CardManager;
import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The {@link AbstractProxy} abstract class represents a common interface
 * of a party in the SMPC RSA scheme.
 *
 * @author Lukas Zaoral
 */
public abstract class AbstractProxy {

    /**
     * File name constants
     */
    protected static final String CLIENT_KEYS_CLIENT_SHARE_FILE = "client_card.key";
    protected static final String CLIENT_KEYS_SERVER_SHARE_FILE = "for_server.key";
    protected static final String PUBLIC_KEY_FILE = "public.key";
    protected static final String MESSAGE_FILE = "message.txt";
    protected static final String CLIENT_SIG_SHARE_FILE = "client.sig";
    protected static final String FINAL_SIG_FILE = "final.sig";

    /**
     * Response APDU SW codes
     */
    protected static final int SW_CONDITIONS_NOT_SATISFIED = 0x6985;
    protected static final int SW_COMMAND_NOT_ALLOWED = 0x6986;
    protected static final int SW_NO_ERROR = 0x9000;

    /**
     * P2 parameters of received keys and messages
     * <p>
     * Part can be combined with divided data into one byte.
     */
    protected static final byte P2_PART_0 = 0x00;
    protected static final byte P2_PART_1 = 0x01;
    private static final byte P2_SINGLE = 0x00;
    private static final byte P2_DIVIDED = 0x10;

    /**
     * Constants
     */
    protected static final byte NONE = 0x00;
    protected static final short PARTIAL_MODULUS_LENGTH = 256;
    protected static final byte[] E = new byte[]{0x01, 0x00, 0x01};
    private static final short MAX_CMD_APDU_LENGTH = 255;

    /**
     * Card manager
     */
    private final CardManager cardMgr;

    /**
     * Connects to a card a selects applet with {@code appletID} ID.
     *
     * @param appletID byte array with an id of a given applet
     * @throws CardException if the terminal or card are missing or the applet is not installed
     */
    AbstractProxy(byte[] appletID) throws CardException {
        cardMgr = new CardManager(appletID);

        printAndFlush("Connecting to card...");
        if (!cardMgr.connect())
            throw new CardException("Make sure that the terminal and card are connected " +
                    "and that the correct applet is installed.");

        printOK();
    }

    /**
     * Disconnects from the given smart card.
     *
     * @throws CardException if something on smart card fails
     */
    public void disconnect() throws CardException {
        cardMgr.disconnect();
    }

    /**
     * Generates/Loads the RSA keys of a given party.
     *
     * @throws IOException   if keys could not be read/written to a file
     * @throws CardException if something on smart card fails
     */
    public abstract void generateKeys() throws IOException, CardException;

    /**
     * Performs a computation of a signature share of a given party
     *
     * @throws IOException   if signature could not be read/written to a file
     * @throws CardException if something on the smart card fails
     */
    public abstract void signMessage() throws IOException, CardException;

    /**
     * Resets the applet.
     *
     * @throws CardException if something on smart card fails
     */
    public abstract void reset() throws CardException;

    /**
     * Resets the applet using {@code cla} class {@code ins} instruction.
     *
     * @param cla class byte
     * @param ins instruction byte
     * @throws CardException if something on the smart card fails
     */
    protected void resetHelper(byte cla, byte ins) throws CardException {
        printAndFlush("Resetting...");
        transmit(new CommandAPDU(cla, ins, NONE, NONE), "Reset");
        printOK();
    }

    /**
     * Generates a list of commands to transfer the {@code num} byte array by segments
     * if necessary.
     *
     * @param num byte array
     * @param cla class byte
     * @param ins instruction byte
     * @param p1  first parameter byte
     * @return list of commands to transfer the given byte array
     */
    protected List<CommandAPDU> splitArrayToCmd(byte[] num, byte cla, byte ins, byte p1) {
        List<CommandAPDU> cmds = new ArrayList<>();

        if (num.length <= MAX_CMD_APDU_LENGTH) {
            cmds.add(new CommandAPDU(cla, ins, p1, P2_PART_0 | P2_SINGLE, num));
            return cmds;
        }

        for (int i = num.length; i > 0; i -= MAX_CMD_APDU_LENGTH) {
            cmds.add(new CommandAPDU(
                    cla, ins, p1, (num.length / MAX_CMD_APDU_LENGTH - i / MAX_CMD_APDU_LENGTH) | P2_DIVIDED,
                    Arrays.copyOfRange(num, i - MAX_CMD_APDU_LENGTH > 0 ? i - MAX_CMD_APDU_LENGTH : 0, i)
            ));
        }

        return cmds;
    }

    /**
     * Transmit the {@code cmd} Command APDU to the card. If the command fails with
     * an error code different from  {@code skipSW}, throws an exception with
     * command name {@code op} and return code.
     *
     * @param cmd    Command APDU to be sent
     * @param name   name of given command
     * @param skipSW if this error SW is returned, throw nothing
     * @return Response APDU
     * @throws CardException if something on the smart card fails
     */
    protected ResponseAPDU transmit(CommandAPDU cmd, String name, int skipSW) throws CardException {
        ResponseAPDU res = cardMgr.transmit(cmd);

        if (res.getSW() != SW_NO_ERROR && res.getSW() != skipSW)
            throw new CardException(String.format("%s SW: %02X", name, res.getSW()));

        return res;
    }

    /**
     * Transmit the {@code cmd} Command APDU to the card. If the command fails
     * throws an exception with its name {@code op} and return code.
     *
     * @param cmd  Command APDU to be sent
     * @param name name of given command
     * @return Response APDU
     * @throws CardException if something on the smart card fails
     */
    protected ResponseAPDU transmit(CommandAPDU cmd, String name) throws CardException {
        return transmit(cmd, name, SW_NO_ERROR);
    }

    /**
     * Transmit the {@code cmds} list of Command APDUs to the card. If some command fails,
     * an error code different from {@code skipSW}, throws an exception with batch
     * command name {@code op} and return code.
     *
     * @param cmds   list of Command APDUs to be sent
     * @param name   name of given command batch
     * @param skipSW if this error SW is returned, throw nothing
     * @return list of Response APDUs
     * @throws CardException if something on the smart card fails
     */
    protected List<ResponseAPDU> transmitBatch(List<CommandAPDU> cmds, String name, int skipSW) throws CardException {
        List<ResponseAPDU> res = new ArrayList<>();

        for (CommandAPDU c : cmds)
            res.add(transmit(c, name, skipSW));

        return res;
    }

    /**
     * Transmit the {@code cmds} list of Command APDUs to the card. If some command fails,
     * throws an exception with the batch name {@code op} and return code.
     *
     * @param cmds list of Command APDUs to be sent
     * @param name name of given command batch
     * @return list of Response APDUs
     * @throws CardException if something on the smart card fails
     */
    protected List<ResponseAPDU> transmitBatch(List<CommandAPDU> cmds, String name) throws CardException {
        List<ResponseAPDU> res = new ArrayList<>();

        for (CommandAPDU c : cmds)
            res.add(transmit(c, name));

        return res;
    }

    /**
     * Loads the data to the given {@code cmdA} and {@code cmdB} lists.
     *
     * @param cmdA list of commands to transfer the first line to smart card
     * @param cmdB list of commands tor transfer the second line to smart card
     * @throws IOException if the file with keys is missing or cannot be read
     */
    protected void loadFile(String fileName, List<CommandAPDU> cmdA, List<CommandAPDU> cmdB, byte cla, byte ins,
                            byte p1D, byte p1N) throws IOException {
        try (InputStream in = new FileInputStream(fileName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            byte[] num = Util.hexStringToByteArray(reader.readLine());
            BigInteger d1Client = new BigInteger(1, num);

            cmdA.addAll(splitArrayToCmd(num, cla, ins, p1D));

            num = Util.hexStringToByteArray(reader.readLine());
            BigInteger n1 = new BigInteger(1, num);

            if (num.length != PARTIAL_MODULUS_LENGTH)
                throw new IOException(String.format("Modulus is not a %d-bit number.",
                        PARTIAL_MODULUS_LENGTH));

            if (d1Client.compareTo(n1) > 0)
                throw new IOException("Private key cannot be larger than modulus.");

            cmdB.addAll(splitArrayToCmd(num, cla, ins, p1N));

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", fileName));
        }
    }

    /**
     * Performs operation {@code ins} to get multipart data and stores them into {@code fileName} file.
     *
     * @param fileName file name
     * @param firstLine first line of saved file
     * @param opName name of operation
     * @param cla class byte
     * @param ins instruction byte
     * @throws CardException if something on the smart card fails
     * @throws IOException if the {@code fileName} file cannot be created or written to
     */
    protected void storeMultipartData(String fileName, String firstLine, String opName, byte cla, byte ins)
            throws CardException, IOException {

        List<ResponseAPDU> res = transmitBatch(Arrays.asList(
                new CommandAPDU(cla, ins, NONE, P2_PART_0, PARTIAL_MODULUS_LENGTH),
                new CommandAPDU(cla, ins, NONE, P2_PART_1, PARTIAL_MODULUS_LENGTH)), opName);

        StringBuilder data = new StringBuilder();
        for (ResponseAPDU r : res) {
            data.append(Util.toHex(r.getData()));
        }

        storeData(fileName, firstLine, data.toString());
    }

    /**
     * Stores given two lines into {@code fileName} file.
     *
     * @param fileName file name
     * @param firstLine first line
     * @param secondLine second line
     * @throws IOException if the {@code fileName} file cannot be created or written to
     */
    protected void storeData(String fileName, String firstLine, String secondLine) throws IOException {
        try (OutputStream out = new FileOutputStream(fileName)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(String.format("%s%n%s%n", firstLine, secondLine));
            writer.flush();
        }
    }

    /**
     * Toggles the debug mode of the card manager.
     */
    public void setDebug(boolean debug) {
        cardMgr.setBDebug(debug);
    }

    /**
     * Prints given {@code str} to the standard output and flushes it.
     *
     * @param str string
     */
    protected void printAndFlush(String str) {
        System.out.print(str);
        System.out.flush();
    }

    /**
     * Prints the "OK" message to the standard output.
     */
    protected void printOK() {
        printAndFlush(" \u001B[1;32mOK\u001B[0m%n");
    }

}
