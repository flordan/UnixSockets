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
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import ipc.Client;
import ipc.IPC;

public class EchoClient extends Client {

    public static Charset charset = Charset.forName("UTF-8");
    public static CharsetEncoder encoder = charset.newEncoder();

    private final String msg;

    public EchoClient(ProtocolFamily family, SocketAddress address, String msg) {
        super(family, address);
        this.msg = msg;
    }

    public void onEstablish() {
        try{
        ByteBuffer bb =encoder.encode(CharBuffer.wrap(this.msg));
        this.sendMessage(bb);
        }catch(Exception e){}
    }

    public void onMessageReception(ByteBuffer msg) {
        String newContent = new String(msg.array());
        System.out.println("Received message for handler " + this + ": " + newContent);
        try {
            this.close();
        } catch (IOException e) {
            System.err.println("Could not close");
        }
    }

    public void onConnectionClose() {
    }


    public static void main(String[] args) throws Exception {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(args[0]);
        for (int i =0; i<100;i++){
            EchoClient c = new EchoClient(StandardProtocolFamily.UNIX, address, "Test"+i);
            c.establish();
        }
    }
}