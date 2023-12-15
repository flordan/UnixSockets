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

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.LinkedList;

import ipc.Server;
import ipc.ConnectionHandler;

public class EchoServer extends Server {

    private EchoServer(ProtocolFamily family, SocketAddress address) {
        super(family, address);
    }

    public ConnectionHandler onNewClient() {
        return new EchoConnection();
    }

    public static class EchoConnection extends ConnectionHandler {
        public void onEstablish() {
        }

        public void onMessageReception(ByteBuffer msg) {
            try {
                sendMessage(msg);
            } catch (IOException e) {
                try {
                    this.close();
                } catch (IOException ioe) {
                    System.err.println("Could not close");
                }
            }
        }

        public void onClose() {
        }
    }

    public static void main(String[] args) throws Exception {

        List<EchoServer> servers = new LinkedList<>();

        for (String arg : args) {
            UnixDomainSocketAddress address;
            if (arg != null && !arg.isEmpty()) {
                address = UnixDomainSocketAddress.of(arg);
                EchoServer s = new EchoServer(StandardProtocolFamily.UNIX, address);
                servers.add(s);
            }
        }

        for (EchoServer s : servers) {
            try {
                s.start();
                System.out.println("Started " + s);
            } catch (IOException e) {
                System.err.println("Could not start the " + s + " because " + e.getMessage());
            }
        }

        java.lang.Thread.sleep(100_000);

        for (EchoServer s : servers) {
            s.stop();
            System.out.println("Server: Stopped " + s);
        }

    }

}