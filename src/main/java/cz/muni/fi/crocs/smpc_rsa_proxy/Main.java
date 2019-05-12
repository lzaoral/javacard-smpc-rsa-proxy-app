package cz.muni.fi.crocs.smpc_rsa_proxy;

import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.AbstractProxy;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ClientFullProxy;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ClientSignProxy;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ServerProxy;

import javax.smartcardio.CardException;

public class Main {

    /**
     *
     */
    private static void printUsage() {
        System.err.println(
            "Unknown parameters.\n" +
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
     *
     * @param mode
     */
    private static void printHeader(String mode) {
        System.out.println("\u001B[1;33m*** SMPC RSA " + mode + " PROXY ***\u001B[0m");
    }

    /**
     *
     * @param arg
     * @return
     * @throws CardException
     */
    private static AbstractProxy getMode(String arg) throws CardException {
        if (arg.equals("client-sign")) {
            printHeader("CLIENT-SIGN");
            return new ClientSignProxy();
        }

        if (arg.equals("client-full")) {
            printHeader("CLIENT-FULL");
            return new ClientFullProxy();
        }

        if (arg.equals("server")) {
            printHeader("SERVER");
            return new ServerProxy();
        }

        return null;
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            printUsage();
            System.exit(1);
        }

        try {
            AbstractProxy smpcRSA = getMode(args[0]);
            if (smpcRSA == null) {
                printUsage();
                System.exit(1);
            }

            if (args[1].equals("generate")) {
                smpcRSA.generateKeys();
                smpcRSA.disconnect();
                return;
            }

            if (args[1].equals("sign")) {
                smpcRSA.signMessage();
                smpcRSA.disconnect();
                return;
            }

            if (args[1].equals("reset")) {
                smpcRSA.reset();
                smpcRSA.disconnect();
                return;
            }

            smpcRSA.disconnect();
            printUsage();
            System.exit(1);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
