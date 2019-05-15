package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

/**
 * Test class.
 * Note: If simulator cannot be started try adding "-noverify" JVM parameter
 *
 * @author Petr Svenda, Dusan Klinec (ph4r05), Lukas Zaoral
 */
public class ClientFullProxy extends AbstractClientProxy {
    public static final byte CLA_RSA_SMPC_CLIENT = (byte) 0x80;

    public static final byte INS_GENERATE_KEYS = 0x10;
    public static final byte INS_GET_KEYS = 0x12;
    public static final byte INS_SET_MESSAGE = 0x14;
    public static final byte INS_SIGNATURE = 0x16;
    public static final byte INS_RESET = 0x18;

    public static final byte P1_GET_D1_SERVER = 0x00;
    public static final byte P1_GET_N1 = 0x01;

    public static String APPLET_AID = "0102030405060708090103";

    /**
     *
     * @throws CardException
     */
    public ClientFullProxy() throws CardException {
        super(Util.hexStringToByteArray(APPLET_AID));
    }

    @Override
    public void generateKeys() throws CardException, IOException {
        printAndFlush("Generating keys...");

        try {
            handleError(transmit(new CommandAPDU(CLA_RSA_SMPC_CLIENT, INS_GENERATE_KEYS, NONE, NONE)), "Keygen");
        } catch (CardException e) {
            if (e.getMessage().contains(SW_COMMAND_NOT_ALLOWED))
                System.err.println("Keys have already been set. Please, reset the card first.");

            throw e;
        }

        printOK();
        getKeys();
    }

    @Override
    public void signMessage() throws CardException, IOException  {
        clientSignMessage(CLA_RSA_SMPC_CLIENT, INS_SET_MESSAGE, INS_SIGNATURE);
    }

    @Override
    public void reset() throws CardException {
        resetHelper(CLA_RSA_SMPC_CLIENT, INS_RESET);
    }

    /**
     *
     * @throws IOException
     * @throws CardException
     */
    private void getKeys() throws IOException, CardException {
        printAndFlush("Storing the server client keys share...");

        ResponseAPDU d1Server, n1;

        try {
            d1Server = transmit(new CommandAPDU(
                    CLA_RSA_SMPC_CLIENT, INS_GET_KEYS, P1_GET_D1_SERVER, NONE, PARTIAL_MODULUS_LENGTH
            ));
            handleError(d1Server, "Get D''1");

            n1 = transmit(new CommandAPDU(
                    CLA_RSA_SMPC_CLIENT, INS_GET_KEYS, P1_GET_N1, NONE, PARTIAL_MODULUS_LENGTH
            ));
            handleError(n1, "Get N1");


            try (OutputStream out = new FileOutputStream(CLIENT_KEYS_SERVER_SHARE_FILE)) {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));

                writer.write(String.format("%s%n%s%n", Util.toHex(Util.trimLeadingZeroes(d1Server.getData())),
                        Util.toHex(Util.trimLeadingZeroes(n1.getData()))));

                writer.flush();
            }

        } catch (CardException e) {
            System.err.println("The client keys has not been generated yet!");
            throw e;
        } catch (FileNotFoundException e) {
            printNOK();
            System.err.println("The keys have not been generated. Run the reference implementation first.");
            throw e;
        }

        printOK();
    }

}
