
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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class ConnectionHandler {

    private SocketChannel sc;
    private NIOListener listener;

    private final LinkedBlockingQueue<ByteBuffer> pendingMsg = new LinkedBlockingQueue<>();

    public final void established(SocketChannel sc, NIOListener listener) {
        this.sc = sc;
        this.listener = listener;
        this.onEstablish();
    }

    public final SocketChannel getSocketChannel() {
        return this.sc;
    }

    public abstract void onEstablish();

    public abstract void onMessageReception(ByteBuffer msg);

    public final void sendMessage(ByteBuffer msg) throws IOException {
        this.pendingMsg.offer(msg);
        listener.writeConnection(this);
    }

    public final ByteBuffer getMessageToSend() {
        return this.pendingMsg.poll();
    }

    public final void close() throws IOException {
        this.listener.deregisterConnection(this);
    }

    public final void closed(){
        onClose();
    }

    public abstract void onClose();
}
