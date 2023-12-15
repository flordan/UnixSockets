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
import java.util.LinkedList;
import java.util.List;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

public class IPC {

    private static List<Server> activeServers = new LinkedList<>();
    private static List<ConnectionHandler> activeClients = new LinkedList<>();

    private static boolean listenerRunning;
    private static final NIOListener listener;

    static {
        NIOListener l;
        try {
            l = new NIOListener();
        } catch (Exception e) {
            System.out.println("Error starting IPC listener");
            System.exit(1);
            l = null;
        }
        listener = l;
    }

    public static final void registerServer(ServerSocketChannel channel, Server s) throws IOException {
        listener.registerServer(channel, s);
        checkListenerUp();
        activeServers.add(s);
    }

    public static final void stopServer(Server s) {
        listener.deregisterServer(s);
        activeServers.remove(s);
        if (activeServers.size() + activeClients.size() == 0){
            checkListenerDown();
        }
    }

    public static final void registerClient(SocketChannel channel, Client c, boolean handleConnect) throws IOException {
        checkListenerUp();
        listener.registerClient(channel, c, handleConnect);
        activeClients.add(c);
    }    
    public static final void deregisterClient(Client c) {
        activeClients.remove(c);
        if (activeServers.size() + activeClients.size() == 0){
            checkListenerDown();
        }
    }    

    private static final void checkListenerUp(){
        synchronized(listener){
            if (!listenerRunning){
                listenerRunning = true;
                new Thread(){
                    public void run(){
                        listener.listen();
                        listenerRunning = false;
                    }
                }.start();
            }
        }
    }

    private static final void checkListenerDown(){
        synchronized(listener){
            if (listenerRunning){
                listener.stop();
            }
        }
    }

}
