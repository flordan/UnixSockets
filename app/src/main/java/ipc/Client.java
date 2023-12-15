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
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public abstract class Client extends ConnectionHandler {

    private final ProtocolFamily family;
    private final SocketAddress address;

    public Client(ProtocolFamily family, SocketAddress address) {
        super();
        this.family = family;
        this.address = address;
    }


    public final void establish() throws IOException {
        SocketChannel channel = SocketChannel.open(family);
        channel.configureBlocking(false);
        channel.connect(address);
        IPC.registerClient(channel, this, false);
    }

    public final void onClose(){
        IPC.deregisterClient(this);
        onConnectionClose();
    }

    public abstract void onConnectionClose();
}
