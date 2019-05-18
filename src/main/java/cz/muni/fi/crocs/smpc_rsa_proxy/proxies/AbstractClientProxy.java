package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link AbstractClientProxy} abstract class represents a common interface
 * of a client party in the SMPC RSA scheme.
 *
 * @author Lukas Zaoral
 */
public abstract class AbstractClientProxy extends AbstractProxy {

    /**
     * Connects to a card a selects a client applet with {@code appletID} ID.
     *
     * @param appletID byte array with an id of a given client applet
     * @throws CardException if the terminal or card are missing or the applet is not installed
     */
    AbstractClientProxy(byte[] appletID) throws CardException {
        super(appletID);
    }

    /**
     * Signs the the given message.
     *
     * @param cla           class byte
     * @param insSetMessage set message instruction byte
     * @param insSignature  sign instruction byte
     * @throws CardException if something on the smart card fails
     * @throws IOException   if the file with message is missing or cannot be read
     *                       or the file with client signature cannot be created or written to
     */
    protected void clientSignMessage(byte cla, byte insSetMessage, byte insSignature)
            throws CardException, IOException {

        String message = clientSetMessage(cla, insSetMessage);

        printAndFlush("Signing...");
        ResponseAPDU respSign = transmit(new CommandAPDU(cla, insSignature, NONE, NONE, PARTIAL_MODULUS_LENGTH), "Sign");
        printOK();

        clientSaveSignature(respSign, message);
    }

    /**
     * Loads the message from the {@code MESSAGE_FILE} file and transmits it to the smart card.
     *
     * @param cla class byte
     * @param ins instruction byte
     * @return hex string with the message
     * @throws CardException if something on the smart card fails
     * @throws IOException   if the file with message is missing or cannot be read
     */
    private String clientSetMessage(byte cla, byte ins) throws CardException, IOException {
        printAndFlush("Loading message...");

        List<CommandAPDU> cmdMessage = new ArrayList<>();
        String message;

        try (InputStream in = new FileInputStream(MESSAGE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            message = reader.readLine();
            byte[] num = Util.hexStringToByteArray(message);

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IOException("Message key cannot be larger than modulus.");

            cmdMessage.addAll(splitArrayToCmd(num, cla, ins, NONE));

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", MESSAGE_FILE));
        } catch (FileNotFoundException e) {
            System.err.println("The message file is missing.");
            throw e;
        }

        printOK();

        transmitMessage(cmdMessage);
        return message;
    }

    /**
     * Transmits the message to the smart card.
     *
     * @param cmdMessage list of commands to set the message
     * @throws CardException if something on the smart card fails
     */
    private void transmitMessage(List<CommandAPDU> cmdMessage) throws CardException {
        printAndFlush("Transmitting message...");
        int res = transmitBatch(cmdMessage, "Set message", SW_CONDITIONS_NOT_SATISFIED).get(0).getSW();
        if (res == SW_CONDITIONS_NOT_SATISFIED)
            throw new CardException("The client keys have not been set/exported yet!");

        printOK();
    }

    /**
     * Saves the client signature share to the {@code CLIENT_SIG_SHARE_FILE} file.
     *
     * @param respSign Response APDU with client signature
     * @param message  hex string with the message
     * @throws IOException if the signature file cannot be created or written to
     */
    private void clientSaveSignature(ResponseAPDU respSign, String message) throws IOException {
        printAndFlush("Storing signature...");
        storeData(CLIENT_SIG_SHARE_FILE, message, Util.toHexTrimmed(respSign.getData()));
        printOK();
    }
}
