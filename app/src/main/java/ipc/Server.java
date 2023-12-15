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
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.net.SocketAddress;

public abstract class Server {
    private final ProtocolFamily family;
    private final SocketAddress address;

    public Server(ProtocolFamily family, SocketAddress address) {
        this.family = family;
        this.address = address;
    }

    public final void start() throws IOException{
        ServerSocketChannel channel = ServerSocketChannel.open(family);
        channel.bind(address);
        channel.configureBlocking(false);
        if (family == StandardProtocolFamily.UNIX) {
            ((UnixDomainSocketAddress) address).getPath().toFile().deleteOnExit();
        }
        IPC.registerServer(channel, this);
    }

    public final void stop() throws IOException{

        IPC.stopServer(this);
        if (family == StandardProtocolFamily.UNIX) {
            ((UnixDomainSocketAddress) address).getPath().toFile().delete();
        }
    }


    public abstract ConnectionHandler onNewClient();

    public String toString(){
        return "Server on " + address;
    }
}