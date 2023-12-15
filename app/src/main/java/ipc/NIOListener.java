/*
 *  Copyright 2023 Francesc Lordan Gomis
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package ipc;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class NIOListener {

    private static final int PACKET_SIZE = 10_240;
    private static final int NETWORK_BUFFER_SIZE = 150 * PACKET_SIZE;

    private final Selector selector;
    private boolean active;

    private Map<Server, SelectionKey> servers; // Items never removed, no volatile
    private Set<SocketChannel> openChannels; // Private to thread, no volatile
    private BlockingQueue<ChangeRequest> pendingChanges;
    private Map<SocketChannel, ChangeRequest> pendingInterests; // Private to thread, no volatile

    public NIOListener() throws IOException {
        this.selector = Selector.open();
        this.active = true;
        this.servers = new HashMap<>(); // Items never removed, no volatile
        this.openChannels = new HashSet<>();
        this.pendingChanges = new LinkedBlockingQueue<>();
        this.pendingInterests = new HashMap<>();

    }

    public final void start() {
        listen();
    }

    public final void stop() {
        this.active = false;
        this.selector.wakeup();
    }

    public void registerServer(ServerSocketChannel channel, Server s) throws IOException {
        addChangeRequest(new AcceptCR(channel, s));
    }

    public void deregisterServer(Server s) {
        SelectionKey key = this.servers.remove(s);
        key.cancel();
        this.selector.wakeup();
    }

    public void registerClient(SocketChannel channel, Client c, boolean handleConnection) throws IOException {
        if (handleConnection) {
            addChangeRequest(new ConnectCR(channel, c));
        } else {
            acceptedConnection(channel, c);
        }
    }

    public void writeConnection(ConnectionHandler handler) throws IOException {
        addChangeRequest(new WriteCR(handler.getSocketChannel(), handler));
    }

    public void deregisterConnection(ConnectionHandler handler) throws IOException {
        addChangeRequest(new CloseCR(handler.getSocketChannel(), handler));
    }

    public void listen() {
        while (active) {
            try {
                // Do any pending changes
                // NOTE: All modifications to selector should be done in the same thread
                applyInterestChanges();
                // Timeout necessary in case selector.wakeup() is done after synchronized
                // (pendingChanges)
                // and before select()
                int keys = NIOListener.this.selector.select();
                if (keys != 0) {
                    Iterator<SelectionKey> selectedKeys = NIOListener.this.selector.selectedKeys().iterator();
                    // Loop through the ready channels
                    processKeys(selectedKeys);
                }
            } catch (Exception e) {
                System.err.println("ERROR: Exception listening on connection changes");
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Process received keys
     *
     * @param selectedKeys
     */
    private void processKeys(Iterator<SelectionKey> selectedKeys) throws Exception {
        while (selectedKeys.hasNext()) {
            SelectionKey key = selectedKeys.next();
            selectedKeys.remove();
            if (!key.isValid()) {
                System.err.println("WARN: Invalid Key for " + key.channel().hashCode());
                key.cancel();
                continue;
            }

            if (key.isAcceptable()) {
                accept(key);
            } else if (key.isConnectable()) {
                connect(key);
            } else if (key.isReadable()) {
                read(key);
            } else if (key.isWritable()) {
                write(key);
            } else {
                System.err.println(" WARN: Undefined key type for " + key.channel().hashCode());
            }
        }
    }

    private final void accept(SelectionKey key) {
        SocketChannel sc = null;
        try {
            var server = (ServerSocketChannel) key.channel();
            sc = server.accept();
            sc.configureBlocking(false);
            this.openChannels.add(sc);
            ConnectionHandler handler = null;
            Server s = (Server) key.attachment();
            if (s != null) {
                handler = s.onNewClient();
                handler.established(sc, this);
            }
            addChangeRequest(new ReadCR(sc, handler));

        } catch (Exception e) {
            if (sc != null) {
                try {
                    sc.close();
                } catch (IOException e1) {
                    // Nothing to do
                }
            }
            key.cancel();
        }

    }

    /**
     * Confirms that the connection has been established client -> server. Performs
     * the 3rd step of the handshake
     *
     * @param key
     */
    private void connect(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        this.openChannels.add(sc);
        Client client = (Client) key.attachment();
        try {
            try {
                if (sc.finishConnect()) {
                    acceptedConnection(sc, client);
                } else {
                    System.err
                            .println("ERROR: Not finished connection to node " + sc.getRemoteAddress().toString() + "."
                                    + " Cancelling key for channel " + sc.hashCode());
                    refusedConnection(sc, null);
                    key.cancel();
                }
            } catch (NoConnectionPendingException npe) {
                // Eventually will be connected
            }
        } catch (Exception e) {
            System.err.println("ERROR: Exception processing connect through channel " + sc.hashCode());
            e.printStackTrace(System.err);
            refusedConnection(sc, e);
            key.cancel();
        }
    }

    /**
     * Accepted connection
     *
     * @param sc
     * @throws Exception
     */
    private void acceptedConnection(SocketChannel sc, Client c) throws IOException {
        addChangeRequest(new ReadCR(sc, c));
        c.established(sc, this);
    }

    /**
     * Refused connection
     *
     * @param sc
     * @param e
     */
    private void refusedConnection(SocketChannel sc, Exception e) {
        // Close socket
        try {
            System.err.println("Closing socket " + sc.hashCode() + " because connection was refused.");
            sc.close();
        } catch (Exception ioe) {

        }
    }

    /**
     * Read from a channel and send the data to the TransferManager
     *
     * @param key
     */
    private void read(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        try {
            // Read data from the socket
            ByteBuffer readBuffer = ByteBuffer.allocate(PACKET_SIZE);
            int size = sc.read(readBuffer);
            if (size == -1) {
                closeChannel(key, sc);
                return;
            }
            // Ask TransferManager to process the data
            readBuffer.flip();

            ConnectionHandler handler = (ConnectionHandler) key.attachment();
            if (handler != null) {
                handler.onMessageReception(readBuffer);
            }
        } catch (Exception ioe) {
            System.err.println("ERROR: Exception reading key in channel " + sc.hashCode());
            closeChannel(key, sc);
        }
    }

    /**
     * Write data to a channel
     *
     * @param key
     */
    private void write(SelectionKey key) throws Exception {
        SocketChannel sc = (SocketChannel) key.channel();
        ConnectionHandler h = (ConnectionHandler) key.attachment();
        ByteBuffer bb = h.getMessageToSend();
        if (bb == null){
            addChangeRequest(new ReadCR(sc, h));
        }else{
            sc.write(bb);
        }

    }

    /**
     * Closes an specific channel
     *
     * @param key
     * @param sc
     */
    private void closeChannel(SelectionKey key, SocketChannel sc) {
        // Cancel the key
        if (key != null) {
            key.cancel();
        }
        this.openChannels.remove(sc);

        // If the socket is open, request and notify the connection closure and close it
        if (sc.isOpen()) {

            // Request socket closure
            try {
                sc.close();
            } catch (IOException e) {
                System.err.println("Could not close channel " + sc);
            }

            ConnectionHandler handler = (ConnectionHandler) key.attachment();
            if (handler != null) {
                handler.closed();
            }
        }
    }

    /**
     * Adds a change request to the queue
     *
     * @param cr
     * @throws Exception
     */
    private void addChangeRequest(ChangeRequest cr) throws IOException {
        // This method can fail
        if (!this.pendingChanges.offer(cr)) {
            System.err
                    .println("ERROR in Comm " + cr + " has not been added to the pending request queue! Message Lost ");
        }
        this.selector.wakeup();
    }

    /**
     * Main run function to apply pending changes
     *
     * @throws IOException
     */
    private void applyInterestChanges() throws Exception {
        while (!this.pendingChanges.isEmpty()) {
            ChangeRequest change = this.pendingChanges.poll();
            change.apply();
        }
    }

    /**
     * ChangeRequest for NIOListener
     *
     */
    private abstract class ChangeRequest {

        private final Channel channel;

        public ChangeRequest(Channel socket) {
            this.channel = socket;
        }

        public abstract void apply() throws IOException;

    }

    private class AcceptCR extends ChangeRequest {

        private final Server server;

        private AcceptCR(Channel socket, Server s) {
            super(socket);
            this.server = s;
        }

        public void apply() throws IOException {
            ServerSocketChannel sc = (ServerSocketChannel) super.channel;
            SelectionKey key = ((SelectableChannel) sc).register(NIOListener.this.selector, SelectionKey.OP_ACCEPT);
            key.attach(server);
            if (!key.isValid()) {
                System.err.println(
                        "WARN: Key for Channel " + sc.hashCode() + " operation OP_ACCEPT not valid.");
            }
            NIOListener.this.servers.put(server, key);
        }

    }

    private class ConnectCR extends ChangeRequest {
        private final Client client;

        private ConnectCR(Channel socket, Client client) {
            super(socket);
            this.client = client;
        }

        public void apply() throws IOException {
            SocketChannel sc = (SocketChannel) super.channel;
            if (sc.isOpen()) {
                SelectionKey key = ((SelectableChannel) sc).register(NIOListener.this.selector,
                        SelectionKey.OP_CONNECT);
                if (!key.isValid()) {
                    System.err.println("WARN: Key for Channel " + sc.hashCode() + " operation OP_CONNECT not valid.");
                }
                key.attach(client);
            } else {
                System.err.println("WARN: Channel " + sc.hashCode() + " is not open!");
            }
        }

    }

    private class ReadCR extends ChangeRequest {

        private final ConnectionHandler handler;

        private ReadCR(Channel socket, ConnectionHandler handler) {
            super(socket);
            this.handler = handler;
        }

        public void apply() throws IOException {
            SocketChannel sc = (SocketChannel) super.channel;
            if (!sc.isConnected()) {
                NIOListener.this.pendingInterests.put(sc, this);
            } else {
                if (sc.isOpen()) {
                    SelectionKey key = ((SelectableChannel) sc).register(NIOListener.this.selector,
                            SelectionKey.OP_READ);
                    key.attach(this.handler);
                    if (!key.isValid()) {
                        System.err.println(
                                "WARN: Key for Channel " + sc.hashCode() + " operation OP_READ not valid.");
                    }
                } else {
                    System.err.println("WARN: Channel " + sc.hashCode() + " is not open!");
                }
            }
        }
    }

    private class WriteCR extends ChangeRequest {

        private final ConnectionHandler handler;

        private WriteCR(Channel socket, ConnectionHandler handler) {
            super(socket);
            this.handler = handler;
        }

        public void apply() throws IOException {
            SocketChannel sc = (SocketChannel) super.channel;
            if (!sc.isConnected()) {
                NIOListener.this.pendingInterests.put(sc, this);
            } else {
                if (sc.isOpen()) {
                    SelectionKey key = ((SelectableChannel) sc).register(NIOListener.this.selector,
                            SelectionKey.OP_WRITE);
                    key.attach(this.handler);
                    if (!key.isValid()) {
                        System.err.println(
                                "WARN: Key for Channel " + sc.hashCode() + " operation OP_WRITE not valid.");
                    }
                    sc.setOption(StandardSocketOptions.SO_SNDBUF, NETWORK_BUFFER_SIZE);
                } else {
                    System.err.println("WARN: Channel " + sc.hashCode() + " is not open!");
                }
            }
        }

    }

    private class CloseCR extends ChangeRequest {
        private final ConnectionHandler handler;

        private CloseCR(Channel socket, ConnectionHandler handler) {
            super(socket);
            this.handler = handler;
        }

        public void apply() {
            SocketChannel sc = (SocketChannel) super.channel;
            // Closure request
            SelectionKey key = sc.keyFor(NIOListener.this.selector);
            // Close Connection
            key.attach(this.handler);
            NIOListener.this.closeChannel(key, sc);
        }

    }
}
