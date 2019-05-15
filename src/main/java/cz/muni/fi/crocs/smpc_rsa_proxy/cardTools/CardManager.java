package cz.muni.fi.crocs.smpc_rsa_proxy.cardTools;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link CardManager} class represent a connection to the card.
 *
 * @author Petr Svenda, Lukáš Zaoral
 */
public class CardManager {
    private boolean bDebug = false;
    private byte[] appletId;
    private CardChannel channel = null;
    
    public CardManager(byte[] appletAID) {
        this.appletId = appletAID;
    }

    /**
     * Connects to the card
     *
     * @return true if connected
     * @throws CardException exceptions from underlying connections
     */
    public boolean connect() throws CardException {
        if (bDebug)
            System.out.print("Looking for physical cards... ");

        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = new ArrayList<>();

        boolean cardFound = false;

        try {
            for (CardTerminal t : factory.terminals().list()) {
                terminals.add(t);
                if (t.isCardPresent())
                    cardFound = true;
            }

            if (bDebug)
                System.out.println("Success.");

        } catch (Exception e) {
            return false;
        }

        if (!cardFound)
            return false;

        CardTerminal terminal = terminals.get(0); // Prioritize physical card over simulations

        if (bDebug)
            System.out.print("Connecting...");
        Card card = terminal.connect("*"); // connect to the card
        if (bDebug) {
            System.out.println(" Done.");
            System.out.print("Establishing channel...");
        }

        channel = card.getBasicChannel();

        if (bDebug) {
            System.out.println(" Done.");
            System.out.print("Smartcard: Selecting applet...");
        }

        CommandAPDU cmd = new CommandAPDU(0x00, 0xa4, 0x04, 0x00, appletId);
        if (transmit(cmd).getSW() != 0x9000)
            return false;

        if (bDebug)
            System.out.println(" Done.");

        return channel != null;
    }

    /**
     * Disconnects from the card
     *
     * @throws CardException exceptions from underlying connections
     */
    public void disconnect() throws CardException {
        channel.getCard().disconnect(false);
    }

    /**
     * Transmit the given Command APDU to the card.
     *
     * @param cmd Command APDU to be send
     * @return Response APDU
     * @throws CardException exceptions from underlying connections
     */
    public ResponseAPDU transmit(CommandAPDU cmd) throws CardException {
        if (bDebug)
            log(cmd);

        long elapsed = -System.currentTimeMillis();
        ResponseAPDU response = channel.transmit(cmd);
        elapsed += System.currentTimeMillis();

        if (bDebug)
            log(response, elapsed);

        return response;
    }

    private void log(CommandAPDU cmd) {
        System.out.printf("--> %s (%d)%n", Util.toHex(cmd.getBytes()), cmd.getBytes().length);
    }

    private void log(ResponseAPDU response, long time) {
        String swStr = String.format("%02X", response.getSW());
        byte[] data = response.getData();
        if (data.length > 0) {
            System.out.printf("<-- %s %s (%d) [%d ms]%n", Util.toHex(data), swStr, data.length, time);
            return;
        }

        System.out.printf("<-- %s [%d ms]%n", swStr, time);
    }

    public void setBDebug(boolean bDebug) {
        this.bDebug = bDebug;
    }

}
