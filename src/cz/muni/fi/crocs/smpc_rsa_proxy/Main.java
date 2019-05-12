package cz.muni.fi.crocs.smpc_rsa_proxy;

import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.AbstractAPDU;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ClientFullAPDU;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ClientSignAPDU;
import cz.muni.fi.crocs.smpc_rsa_proxy.proxies.ServerAPDU;

import javax.smartcardio.CardException;

public class Main {

    //TODO: napoveda

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        try {
            AbstractAPDU smpcRSA = getMode(args[0]);
            if (smpcRSA == null) {
                // print header
                System.exit(1);
            }

            // TODO: generate keys
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
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    /**
     *
     * @param arg
     * @return
     * @throws CardException
     */
    private static AbstractAPDU getMode(String arg) throws CardException {
        if (arg.equals("client-sign"))
            return new ClientSignAPDU();

        if (arg.equals("client-full"))
            return new ClientFullAPDU();

        if (arg.equals("server"))
            return new ServerAPDU();

        return null;
    }
}
