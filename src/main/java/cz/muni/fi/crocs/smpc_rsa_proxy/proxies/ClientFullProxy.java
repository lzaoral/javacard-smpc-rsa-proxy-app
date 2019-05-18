package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.Util;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import java.io.IOException;

/**
 * The {@link ClientFullProxy} class represents a full client party
 * in the SMPC RSA scheme.
 *
 * @author Lukas Zaoral
 */
public class ClientFullProxy extends AbstractClientProxy {

    private static final byte CLA_RSA_SMPC_CLIENT = (byte) 0x80;

    /**
     * Instruction codes
     */
    private static final byte INS_GENERATE_KEYS = 0x10;
    private static final byte INS_GET_KEYS = 0x12;
    private static final byte INS_SET_MESSAGE = 0x14;
    private static final byte INS_SIGNATURE = 0x16;
    private static final byte INS_RESET = 0x18;

    /**
     * P1 parameters of the INS_GET_KEYS instruction
     */
    private static final byte P1_GET_D1_SERVER = 0x00;
    private static final byte P1_GET_N1 = 0x01;

    /**
     * Applet ID
     */
    private static final String APPLET_AID = "0102030405060708090103";

    /**
     * Connects to a card a selects the client-full applet.
     *
     * @throws CardException if the terminal or card are missing
     *                       or the applet is not installed
     */
    public ClientFullProxy() throws CardException {
        super(Util.hexStringToByteArray(APPLET_AID));
    }

    @Override
    public void generateKeys() throws CardException, IOException {
        printAndFlush("Generating keys...");

        int ret = transmit(new CommandAPDU(CLA_RSA_SMPC_CLIENT, INS_GENERATE_KEYS, NONE, NONE),
                "Keygen", SW_COMMAND_NOT_ALLOWED).getSW();
        if (ret == SW_COMMAND_NOT_ALLOWED)
            throw new CardException("Keys have already been set. Please, reset the card first.");

        printOK();
        storeKeys();
    }

    @Override
    public void signMessage() throws CardException, IOException {
        clientSignMessage(CLA_RSA_SMPC_CLIENT, INS_SET_MESSAGE, INS_SIGNATURE);
    }

    @Override
    public void reset() throws CardException {
        resetHelper(CLA_RSA_SMPC_CLIENT, INS_RESET);
    }

    /**
     * Stores the server share of client keys to the {@code CLIENT_KEYS_SERVER_SHARE_FILE} file.
     *
     * @throws CardException if something on the smart card fails
     * @throws IOException   if the server keys share file cannot be created or written to
     */
    private void storeKeys() throws CardException, IOException {
        printAndFlush("Storing the server client keys share...");

        ResponseAPDU d1Server = transmit(new CommandAPDU(CLA_RSA_SMPC_CLIENT, INS_GET_KEYS, P1_GET_D1_SERVER, NONE,
                PARTIAL_MODULUS_LENGTH), "Get D''1");
        ResponseAPDU n1 = transmit(new CommandAPDU(CLA_RSA_SMPC_CLIENT, INS_GET_KEYS, P1_GET_N1, NONE,
                PARTIAL_MODULUS_LENGTH), "Get N1");

        storeData(CLIENT_KEYS_SERVER_SHARE_FILE, Util.toHexTrimmed(d1Server.getData()),
                Util.toHexTrimmed(n1.getData()));

        printOK();
    }

}
