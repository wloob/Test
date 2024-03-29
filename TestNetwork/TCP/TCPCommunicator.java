package TCP;

import java.io.IOException;
import java.net.*;
import java.util.HashSet;
import TCP.messaging.*;

/**
 * Represents a unit that is capable of sending and receiving messages using the
 * TCP protocol.
 */
public abstract class TCPCommunicator {

    protected InetAddress address;
    protected int port;

    private DatagramSocket socket;
    private byte[] buffer;

    /**
     * Contains TCPMessage-keys of pings that have been sent but not yet accepted by
     * the receiver. When a ping is accepted by it's receiver, it is removed from
     * this list.
     */
    private HashSet<Integer> pingedKeys;

    /**
     * Contains TCPMessage-keys that have been sent to this unit and accepted. If or
     * when a message is received with a key contained in this list, it is assumed
     * that the message is welcome and the message is then 'taken in'. The key is
     * then removed from the list.
     */
    private HashSet<Integer> acceptedKeys;

    private String lastPrint = "";

    /**
     * @return InetAddress of the socket.
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * @return Port of the socket.
     */
    public int getPort() {
        return port;
    }

    /**
     * @return a String containing the address and port of the socket.
     */
    public String getFullAddress() {
        return String.format("%s:%d", address.getHostAddress(), port);
    }

    protected TCPCommunicator(int port) {
        try {
            pingedKeys = new HashSet<>();
            acceptedKeys = new HashSet<>();

            address = InetAddress.getLocalHost();
            this.port = port;
            socket = new DatagramSocket(port);

            System.out.printf("TCPCommunicator initiated at %s.%n", getFullAddress());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /**
     * Tells the communicator to listen for socket input for a given amount of time.
     * If 0 is given, it listens forever, calling onListenTimeout() twice every
     * second. If the method was called with a timelimit, it is assumed that a
     * ping-response is expected. If one is received, the listening is cut short and
     * assumed to have fulfilled its purpose.
     * 
     * @param listenForMs time to listen.
     */
    protected void listen(int listenForMs) {
        while (true) {
            if (listenForMs != 0)
                setTimeout(listenForMs);
            else
                setTimeout(500);

            print(String.format("Listening at socket %s...%n", getFullAddress()));
            buffer = new byte[TCPMessage.BYTE_SIZE];

            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(receivePacket);
            } catch (IOException e) {
                if (listenForMs == 0) {
                    onListenTimeout();
                    continue;
                } else {
                    break;
                }
            }

            boolean finishedHandshake = processMessage(receivePacket.getData());
            if (listenForMs != 0 && finishedHandshake)
                break;
        }
    }

    /**
     * Tells the communicator to listen for socket input forever, calling
     * onListenTimeout() twice every second.
     */
    protected void listen() {
        listen(0);
    }

    /**
     * Pings another TCPCommunicator, expecting a response within 100ms. If a key
     * (not 0) is provided, the PingMessage is configured as a handshake for an
     * upcoming message.
     * 
     * @param toAddress InetAddress of the targeted socket.
     * @param toPort    Port of the targeted socket.
     * @param key       Key of message to be requested with ping.
     * @return true if a response was received. Otherwise false.
     */
    protected boolean ping(InetAddress toAddress, int toPort, int key) {
        PingMessage message = new PingMessage();
        message.setSender(address, port);
        message.setReceiver(toAddress, toPort);
        
        if (key != 0) {
            message.setHandshakeKey(key);
            message.setIsHandshake(true);
        }

        if (toAddress.equals(address) && toPort == port) {
            // Self ping
            return true;
        }

        boolean sent = sendMessage(message, toAddress, toPort);

        if (!sent)
            return false;

        pingedKeys.add(message.getHandshakeKey());
        listen(100);
        boolean success = !pingedKeys.contains(message.getHandshakeKey());

        pingedKeys.clear();
        return success;
    }

    /**
     * Pings another TCPCommunicator, expecting a response within 100ms.
     * 
     * @param toAddress InetAddress of the targeted socket.
     * @param toPort    Port of the targeted socket.
     * @return true if a response was received. Otherwise false.
     */
    protected boolean ping(InetAddress toAddress, int toPort) {
        return ping(toAddress, toPort, 0);
    }

    /**
     * Sends a TCP message to a target socket. The process starts by blindly sending
     * the message to the target. If a response is received from the target, the
     * message is sent again and it is assumed that it was well-received.
     * 
     * @param message   to be sent to the target.
     * @param toAddress InetAddress of the targeted socket.
     * @param toPort    Port of the targeted socket.
     * @return true if the handshake was answered and the message sent afterwards.
     */
    protected boolean sendTCPMessage(TCPMessage message, InetAddress toAddress, int toPort) {
        message.setSender(address, port);
        message.setReceiver(toAddress, toPort);
        message.generateHandshakeKey();

        if (toAddress.equals(address) && toPort == port) {
            // Message sent to self is transfered internally.
            print(String.format("Took in %s from self.", message.toString()));
            takeInMessage(message);
            return true;
        }

        if (!ping(toAddress, toPort, message.getHandshakeKey())) {
            print(String.format("Handshake for %s with %s:%d failed.", message.toString(),
                    message.getReceiverAddress().getHostAddress(), message.getReceiverPort()));
            return false;
        }

        print(String.format("Handshake approved for %s. Sending to %s:%d.", message.toString(),
                message.getReceiverAddress().getHostAddress(), message.getReceiverPort()));

        return sendMessage(message, toAddress, toPort);
    }

    /**
     * Called when a handshake-approved message is received.
     * 
     * @param message the approved TCPMessage to take in.
     */
    protected abstract void takeInMessage(TCPMessage message);

    /**
     * Called twice every second between listening intervals allowing periodic
     * activities.
     */
    protected abstract void onListenTimeout();

    /**
     * Prints a status to the console. Will not duplicate the last print.
     * 
     * @param toPrint string to be printed.
     */
    protected void print(String toPrint) {
        if (!lastPrint.equals(toPrint)) {
            System.out.println(toPrint);
            lastPrint = toPrint;
        }
    }

    /**
     * Sets socket timeout.
     * 
     * @param ms amount of time (in milliseconds) until the socket times out.
     */
    private void setTimeout(int ms) {
        try {
            socket.setSoTimeout(ms);
        } catch (SocketException e) {
            System.out.printf("Failed to set socket timeout %d for %s.%n", ms, getFullAddress());
            e.printStackTrace();
        }
    }

    /**
     * Sends a TCPMessage to a given socket.
     * 
     * @param message   to be sent to the socket.
     * @param toAddress InetAddress of targeted socket.
     * @param toPort    Port of the targeted socket.
     * @return
     */
    private boolean sendMessage(TCPMessage message, InetAddress toAddress, int toPort) {
        byte[] messageBytes = message.serialize();
        DatagramPacket datagramPacket = new DatagramPacket(messageBytes, messageBytes.length, toAddress, toPort);

        try {
            socket.send(datagramPacket);
            return true;
        } catch (IOException e) {
            System.out.printf("Exception thrown when sending %s to %s:%d.%n", message, toAddress, toPort);
            return false;
        }
    }

    /**
     * Takes in and treats a given byte[] as a TCPMessage. If parsing of the bytes
     * fails, nothing is done.
     * 
     * @param messageBytes to be parsed into a TCPMessage.
     * @return true if the given message is a response to a ping.
     */
    private boolean processMessage(byte[] messageBytes) {
        TCPMessage message = TCPMessage.deserialize(messageBytes);

        // Deserialize returns null if the given byte array couldn't be deserialized.
        if (message == null)
            return false;

        if (message instanceof PingMessage) {
            PingMessage pingMessage = (PingMessage) message;
            if (pingMessage.getSenderAddress().equals(address) && pingMessage.getSenderPort() == port) {
                // The ping was sent from here and was approved by it's receiver.
                pingedKeys.remove(pingMessage.getHandshakeKey());
                return true;
            } else {
                // The ping was sent to this node. Reply.
                if (pingMessage.isHandshake())
                    acceptedKeys.add(pingMessage.getHandshakeKey());
                sendMessage(message, pingMessage.getSenderAddress(), pingMessage.getSenderPort());
                return false;
            }
        }

        if (acceptedKeys.contains(message.getHandshakeKey())) {
            // Approved message received
            acceptedKeys.remove(message.getHandshakeKey());
            print(String.format("Took in %s from %s:%d.", message.toString(),
                    message.getSenderAddress().getHostAddress(), message.getSenderPort()));
            takeInMessage(message);
        }

        return false;
    }
}