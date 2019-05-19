package cz.muni.fi.crocs.smpc_rsa_proxy;

import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.AbstractProxy;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ClientFullProxy;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ClientSignProxy;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ServerProxy;

import javax.smartcardio.CardException;

/**
 * The {@link Main} class represents a handler of given
 * user commands.
 *
 * @author Lukas Zaoral
 */
public class Main {

    /**
     * Enum representing the allowed actions.
     */
    enum Action {
        GENERATE,
        SIGN,
        RESET,
        UNKNOWN
    }

    /**
     * Prints out the usage message.
     */
    private static void printUsage() {
        System.err.println("Unknown parameters.\n" +
                "USAGE: [executable] [mode] [action]\n" +
                "    Modes: client-sign, client-full, server\n" +
                "    Actions:\n" +
                "        generate - Set the [client-sign] keys or\n" +
                "                   Generate the [client-full|server] keys\n" +
                "        sign - Sign the message\n" +
                "        reset - Reset the applet"
        );
    }

    /**
     * Parses the given string to corresponding Action.
     *
     * @return Action corresponding to the argument
     */
    private static Action parseAction(String action) {
        if (action.equals("generate")) {
            return Action.GENERATE;
        }

        if (action.equals("sign")) {
            return Action.SIGN;
        }

        if (action.equals("reset")) {
            return Action.RESET;
        }

        return Action.UNKNOWN;
    }

    /**
     * Prints out the header with selected {@code mode}.
     *
     * @param mode name of the mode
     */
    private static void printHeader(String mode) {
        System.out.println("\u001B[1;33m*** SMPC RSA " + mode + " PROXY ***\u001B[0m");
    }

    /**
     * Creates an instance of creates an instance of client-sign, client-full
     * or server proxy depending on the {@code mode} parameter.
     *
     * @param mode selected mode
     * @return selected mode proxy or null if the mode does not exist
     * @throws CardException if the terminal or card are missing
     *                       or the selected applet is not installed
     */
    private static AbstractProxy getMode(String mode) throws CardException {
        if (mode.equals("client-sign")) {
            printHeader("CLIENT-SIGN");
            return new ClientSignProxy();
        }

        if (mode.equals("client-full")) {
            printHeader("CLIENT-FULL");
            return new ClientFullProxy();
        }

        if (mode.equals("server")) {
            printHeader("SERVER");
            return new ServerProxy();
        }

        return null;
    }

    /**
     * Main method of the SMPC RSA proxy application.
     *
     * @param args array of command-line arguments
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            printUsage();
            System.exit(1);
        }

        // check before connecting to the card
        Action action = parseAction(args[1]);
        if (action == Action.UNKNOWN) {
            printUsage();
            System.exit(1);
        }

        try {
            AbstractProxy smpcRSA = getMode(args[0]);
            if (smpcRSA == null) {
                printUsage();
                System.exit(1);
            }

            switch (action) {
                case GENERATE:
                    smpcRSA.generateKeys();
                    break;

                case SIGN:
                    smpcRSA.signMessage();
                    break;

                case RESET:
                    smpcRSA.reset();
                    break;
            }

            smpcRSA.disconnect();

        } catch (Exception e) {
            System.err.println(" \u001B[1;31mNOK\u001B[0m");
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

}
