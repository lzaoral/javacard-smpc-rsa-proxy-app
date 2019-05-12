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

import java.util.ArrayList;

public abstract class AbstractClientProxy extends AbstractProxy {

    AbstractClientProxy(byte[] appletID) throws CardException {
        super(appletID);
    }

    /**
     *
     * @param cla
     * @param insSetMessage
     * @param insSignature
     * @throws CardException
     * @throws IOException
     */
    protected void clientSignMessage(byte cla, byte insSetMessage, byte insSignature) throws CardException, IOException {
        ArrayList<CommandAPDU> cmdMessage = new ArrayList<>();
        String message = clientSetMessage(cmdMessage, cla, insSetMessage);

        printAndFlush("Transmitting message...");
        try {
            transmitNumber(cmdMessage, "Set message");
        } catch (CardException e) {
            if (e.getMessage().contains(SW_CONDITIONS_NOT_SATISFIED))
                System.err.println("The client keys share has not been set!");

            throw e;
        }
        printOK();

        printAndFlush("Signing...");
        ResponseAPDU respSign = transmit(new CommandAPDU(cla, insSignature, NONE, NONE, PARTIAL_MODULUS_LENGTH));
        handleError(respSign, "Signing");
        printOK();

        clientSaveSignature(respSign, message);
    }

    /**
     *
     * @param cmdMessage
     * @param cla
     * @param ins
     * @return
     * @throws IOException
     */
    private String clientSetMessage(ArrayList<CommandAPDU> cmdMessage, byte cla, byte ins) throws IOException {
        printAndFlush("Loading message...");

        String message;

        try (InputStream in = new FileInputStream(MESSAGE_FILE)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            message = reader.readLine();
            byte[] num = Util.hexStringToByteArray(message);

            if (num.length > PARTIAL_MODULUS_LENGTH)
                throw new IllegalArgumentException("Message key cannot be larger than modulus.");

            setNumber(cmdMessage, num, cla, ins, NONE);

            if (reader.readLine() != null)
                throw new IOException(String.format("Wrong '%s' file format.", MESSAGE_FILE));
        } catch (FileNotFoundException e) {
            printNOK();
            System.err.println("The message file is missing.");
            throw e;
        }

        printOK();
        return message;
    }

    /**
     *
     * @param respSign
     * @param message
     * @throws IOException
     */
    private void clientSaveSignature(ResponseAPDU respSign, String message) throws IOException {
        printAndFlush("Storing signature...");

        try (OutputStream out = new FileOutputStream(CLIENT_SIG_SHARE_FILE)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(String.format(
                    "%s%n%s%n", message, Util.toHex(Util.trimLeadingZeroes(respSign.getData()))
            ));
            writer.flush();
        } catch (FileNotFoundException e) {
            printNOK();
            throw e;
        }

        printOK();
    }
}
