package cz.muni.fi.crocs.smpc_rsa_proxy.cardTools;

import javax.smartcardio.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Petr Svenda
 */
public class CardManager {
    private boolean bDebug = false;
    private byte[] appletId;
    private CardChannel channel = null;
    
    public CardManager(byte[] appletAID) {
        this.appletId = appletAID;
    }

    /**
     * Card connect
     * @return true if connected
     * @throws CardException exceptions from underlying connects
     */
    public boolean connect() throws CardException {
        if (bDebug)
            System.out.print("Looking for physical cards... ");

        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = new ArrayList<>();

        boolean card_found = false;
        CardTerminal terminal;
        Card card;

        try {
            for (CardTerminal t : factory.terminals().list()) {
                terminals.add(t);
                if (t.isCardPresent())
                    card_found = true;
            }

            if (bDebug)
                System.out.println("Success.");

        } catch (Exception e) {
            return false;
        }

        if (!card_found)
            return false;

        terminal = terminals.get(0); // Prioritize physical card over simulations

        if (bDebug)
            System.out.print("Connecting...");
        card = terminal.connect("*"); // connect with the card
        if (bDebug) {
            System.out.println(" Done.");
            System.out.print("Establishing channel...");
        }

        channel = card.getBasicChannel();

        if (bDebug) {
            System.out.println(" Done.");
            System.out.println("Smartcard: Selecting applet...");
        }

        CommandAPDU cmd = new CommandAPDU(0x00, 0xa4, 0x04, 0x00, appletId);
        transmit(cmd);

        return card.getBasicChannel() != null;
    }
    
    public void disconnect(boolean bReset) throws CardException {
        channel.getCard().disconnect(bReset);
    }

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
        System.out.printf("--> %s (%d)\n", Util.toHex(cmd.getBytes()), cmd.getBytes().length);
    }

    private void log(ResponseAPDU response, long time) {
        String swStr = String.format("%02X", response.getSW());
        byte[] data = response.getData();
        if (data.length > 0) {
            System.out.printf("<-- %s %s (%d) [%d ms]\n", Util.toHex(data), swStr, data.length, time);
            return;
        }

        System.out.printf("<-- %s [%d ms]\n", swStr, time);
    }

    public void setbDebug(boolean bDebug) {
        this.bDebug = bDebug;
    }

}
