package network;

import other.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ForwardServerClientThread extends Thread
{
    private ForwardClient mForwardClient = null;
    private Socket mClientSocket = null;
    private Socket mServerSocket = null;
    private ServerSocket mListenSocket = null;
    private boolean mBothConnectionsAreAlive = false;
    private String mClientHostPort;
    private String mServerHostPort;
    private int mServerPort;
    private String mServerHost;

    private boolean vpnServer = false;
    private final int ENCRYPTION_MODE = 0;
    private final int DECRYPTION_MODE = 1;

    /**
     * Creates a client thread for handling clients of NakovForwardServer.
     * A client socket should be connected and passed to this constructor.
     * A server socket is created later by run() method.
     */
    public ForwardServerClientThread(Socket aClientSocket, String serverhost, int serverport)
    {
        mClientSocket = aClientSocket;
        mServerPort = serverport;
        mServerHost = serverhost;
    }
 
    /**
     * Creates a client thread for handling clients of NakovForwardServer.
     * Wait for client to connect on client listening socket.
     * A server socket is created later by run() method.
     */
    public ForwardServerClientThread(ServerSocket listensocket, String serverhost, int serverport) throws IOException
    {
        mListenSocket = listensocket;
        mServerPort = serverport;
        mServerHost = serverhost;
    }

    public ServerSocket getListenSocket() {
        return mListenSocket;
    }

    /**
     * Obtains a destination server socket to some of the servers in the list.
     * Starts two threads for forwarding : "client in <--> dest server out" and
     * "dest server in <--> client out", waits until one of these threads stop
     * due to read/write failure or connection closure. Closes opened connections.
     * 
     * If there is a listen socket, first wait for incoming connection
     * on the listen socket.
     */
    public void run()
    {
        try {
 
            // Wait for incoming connection on listen socket, if there is one 
           if (mListenSocket != null) { // I am in vpn server
               mClientSocket = mListenSocket.accept();
               mClientHostPort = mClientSocket.getInetAddress().getHostAddress() + ":" + mClientSocket.getPort();
               Logger.log("Accepted from  " + mServerPort + " <--> " + mClientHostPort + "  started.");
               vpnServer = true;
           }
           else { // I am in vpn client
               mClientHostPort = mClientSocket.getInetAddress().getHostAddress() + ":" + mClientSocket.getPort();
           }

           try {
               mServerSocket = new Socket(mServerHost, mServerPort);
           } catch (Exception e) {
               System.out.println("Connection failed to " + mServerHost + ":" + mServerPort);
               e.printStackTrace();
               // Prints what exception has been thrown 
               System.out.println(e); 
           }

           // Obtain input and output streams of server and client
            System.out.println("STREAMS");
           InputStream clientIn = mClientSocket.getInputStream();
           OutputStream clientOut = mClientSocket.getOutputStream();
           InputStream serverIn = mServerSocket.getInputStream();
           OutputStream serverOut = mServerSocket.getOutputStream();

            System.out.println("clientin" + clientIn);

           mServerHostPort = mServerHost + ":" + mServerPort;
           Logger.log("TCP Forwarding  " + mClientHostPort + " <--> " + mServerHostPort + "  started.");

            ForwardThread clientForward;
            ForwardThread serverForward;
           // Start forwarding of socket data between server and client
            if(vpnServer) {
                clientForward = new ForwardThread(this, clientIn, serverOut, DECRYPTION_MODE);
                serverForward = new ForwardThread(this, serverIn, clientOut, ENCRYPTION_MODE);
            } else {
                clientForward = new ForwardThread(this, clientIn, serverOut, ENCRYPTION_MODE);
                serverForward = new ForwardThread(this, serverIn, clientOut, DECRYPTION_MODE);
            }

           mBothConnectionsAreAlive = true;
           clientForward.start();
           serverForward.start();
 
        } catch (Exception ioe) {
           ioe.printStackTrace();
        }
    }
 
    /**
     * connectionBroken() method is called by forwarding child threads to notify
     * this thread (their parent thread) that one of the connections (server or client)
     * is broken (a read/write failure occured). This method disconnects both server
     * and client sockets causing both threads to stop forwarding.
     */
    public synchronized void connectionBroken()
    {
        if (mBothConnectionsAreAlive) {
           // One of the connections is broken. Close the other connection and stop forwarding
           // Closing these socket connections will close their input/output streams
           // and that way will stop the threads that read from these streams
           try { mServerSocket.close(); } catch (IOException e) {}
           try { mClientSocket.close(); } catch (IOException e) {}
 
           mBothConnectionsAreAlive = false;
 
           Logger.log("TCP Forwarding  " + mClientHostPort + " <--> " + mServerHostPort + "  stopped.");
        }
    }
 
}
