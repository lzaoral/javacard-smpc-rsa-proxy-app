package cz.muni.fi.crocs.smpc_rsa_proxy.proxies;

import cz.muni.fi.crocs.smpc_rsa_proxy.cardTools.CardManager;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class AbstractAPDU {

    public static final String CLIENT_KEYS_CLIENT_SHARE_FILE = "client_card.key";
    public static final String CLIENT_KEYS_SERVER_SHARE_FILE = "for_server.key";
    public static final String PUBLIC_KEY_FILE = "public.key";
    public static final String MESSAGE_FILE = "message.txt";
    public static final String CLIENT_SIG_SHARE_FILE = "client.sig";
    public static final String FINAL_SIG_FILE = "final.sig";

    public static final String SW_CONDITIONS_NOT_SATISFIED = "6985";
    public static final String SW_COMMAND_NOT_ALLOWED = "6986";

    public static final byte P2_PART_0 = 0x00;
    public static final byte P2_PART_1 = 0x01;
    public static final byte P2_SINGLE = 0x00;
    public static final byte P2_DIVIDED = 0x10;

    public static final byte NONE = 0x00;

    public static final short PARTIAL_MODULUS_LENGTH = 256;
    private static final short MAX_CMD_APDU_LENGTH = 255;

    private final CardManager cardMgr;

    /**
     *
     * @param appletID
     * @throws CardException
     */
    public AbstractAPDU(byte[] appletID) throws CardException {
        cardMgr = new CardManager(appletID);

        printAndFlush("Connecting to card...");
        if (!cardMgr.connect()) {
            System.err.println(" \u001B[1;32mOK\u001B[0m");
            System.exit(1); // TODO: ugly
        }

        printOK();
    }

    /**
     *
     *
     * @param cmds
     * @param num
     * @param ins
     * @param p1
     */
    protected void setNumber(ArrayList<CommandAPDU> cmds, byte[] num, byte cla, byte ins, byte p1) {
        if (num.length <= MAX_CMD_APDU_LENGTH) {
            cmds.add(new CommandAPDU(cla, ins, p1, P2_PART_0 | P2_SINGLE, num));
            return;
        }

        for (int i = num.length; i > 0; i -= MAX_CMD_APDU_LENGTH) {
            cmds.add(new CommandAPDU(
                    cla, ins, p1, (num.length / MAX_CMD_APDU_LENGTH - i / MAX_CMD_APDU_LENGTH) | P2_DIVIDED,
                    Arrays.copyOfRange(num, i - MAX_CMD_APDU_LENGTH > 0 ? i - MAX_CMD_APDU_LENGTH : 0, i)
            ));
        }
    }

    /**
     *
     * @throws IOException
     * @throws CardException
     */
    public abstract void generateKeys() throws IOException, CardException;

    /**
     *
     * @throws IOException
     * @throws CardException
     */
    public abstract void signMessage() throws IOException, CardException;

    /**
     *
     * @throws CardException
     */
    public abstract void reset() throws CardException;

    /**
     *
     */
    protected void resetHelper(byte cla, byte ins) throws CardException {
        printAndFlush("Resettting...");

        handleError(transmit(new CommandAPDU(cla, ins, NONE, NONE)), "Reset");
        printOK();
    }

    /**
     *
     * @param cmd
     * @return
     * @throws Exception
     */
    protected ResponseAPDU transmit(CommandAPDU cmd) throws CardException {
        return cardMgr.transmit(cmd);
    }

    /**
     *
     * @param cmd
     * @param operation
     * @throws Exception
     */
    protected void transmitNumber(ArrayList<CommandAPDU> cmd, String operation) throws CardException {
        for (CommandAPDU c : cmd) {
            handleError(transmit(c), operation);
        }
    }

    /**
     *
     * @param debug
     */
    public void setDebug(boolean debug) {
        cardMgr.setbDebug(debug);
    }

    /**
     *
     * @param res
     * @param operation
     * @throws CardException
     */
    protected void handleError(ResponseAPDU res, String operation) throws CardException {
        if (res.getSW() != 0x9000) {
            System.err.println(" \u001B[1;31mNOK\u001B[0m");
            throw new CardException(String.format("%s: %02X", operation, res.getSW()));
        }
    }

    /**
     *
     * @param str
     */
    public void printAndFlush(String str) {
        System.out.print(str);
        System.out.flush();
    }

    public void printOK() {
        printAndFlush(" \u001B[1;32mOK\u001B[0m" + System.lineSeparator());
    }

    /**
     *
     * @throws CardException
     */
    public void disconnect() throws CardException {
        cardMgr.disconnect(false);
    }

}
