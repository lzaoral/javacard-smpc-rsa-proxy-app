package cz.muni.fi.crocs.smpc_rsa_proxy.cardTools;

import javax.smartcardio.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Petr Svenda
 */
public class CardManager {
    protected boolean bDebug = false;
    protected byte[] appletId = null;
    protected Long lastTransmitTime = (long) 0;
    protected CommandAPDU lastCommand = null;
    protected CardChannel channel = null;
    
    public CardManager(byte[] appletAID) {
        this.appletId = appletAID;
    }

    /**
     * Card connect
     * @return true if connected
     * @throws Exception exceptions from underlying connects
     */
    public boolean Connect() throws CardException {
        return (channel = ConnectPhysicalCard(0)) != null;
    }
    
    public void Disconnect(boolean bReset) throws CardException {
        channel.getCard().disconnect(bReset); // Disconnect from the card
    }

    public CardChannel ConnectPhysicalCard(int targetReaderIndex) throws CardException {
        if (bDebug)
            System.out.print("Looking for physical cards... ");

        TerminalFactory factory = TerminalFactory.getDefault();
        List<CardTerminal> terminals = new ArrayList<>();

        boolean card_found = false;
        CardTerminal terminal;
        Card card = null;
        try {
            for (CardTerminal t : factory.terminals().list()) {
                terminals.add(t);
                if (t.isCardPresent()) {
                    card_found = true;
                }
            }

            if (bDebug)
                System.out.println("Success.");
        } catch (Exception e) {
            return null;
        }

        if (card_found) {
            if (bDebug)
                System.out.println("Cards found: " + terminals);

            terminal = terminals.get(targetReaderIndex); // Prioritize physical card over simulations

            if (bDebug)
                System.out.print("Connecting...");
            card = terminal.connect("*"); // Connect with the card

            if (bDebug) {
                System.out.println(" Done.");
                System.out.print("Establishing channel...");
            }

            channel = card.getBasicChannel();

            if (bDebug)
                System.out.println(" Done.");

            // Select applet (mpcapplet)
            if (bDebug)
                System.out.println("Smartcard: Selecting applet...");

            CommandAPDU cmd = new CommandAPDU(0x00, 0xa4, 0x04, 0x00, appletId);
            transmit(cmd);
        } else {
            return null;
        }

        return card.getBasicChannel();
    }
    
    public ResponseAPDU transmit(CommandAPDU cmd) throws CardException {
        lastCommand = cmd;
        if (bDebug) {
            log(cmd);
        }

        long elapsed = -System.currentTimeMillis();
        ResponseAPDU response = channel.transmit(cmd);
        elapsed += System.currentTimeMillis();
        lastTransmitTime = elapsed;

        if (bDebug) {
            log(response, lastTransmitTime);
        }

        return response;
    }

    private void log(CommandAPDU cmd) {
        System.out.printf("--> %s\n", Util.toHex(cmd.getBytes()),
                cmd.getBytes().length);
    }

    private void log(ResponseAPDU response, long time) {
        String swStr = String.format("%02X", response.getSW());
        byte[] data = response.getData();
        if (data.length > 0) {
            System.out.printf("<-- %s %s (%d) [%d ms]\n", Util.toHex(data), swStr,
                    data.length, time);
        } else {
            System.out.printf("<-- %s [%d ms]\n", swStr, time);
        }
    }

    public CardManager setbDebug(boolean bDebug) {
        this.bDebug = bDebug;
        return this;
    }

}
