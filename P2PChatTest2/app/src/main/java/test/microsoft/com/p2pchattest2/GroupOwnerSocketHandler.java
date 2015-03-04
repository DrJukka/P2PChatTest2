
package test.microsoft.com.p2pchattest2;

import android.content.Context;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The implementation of a ServerSocket handler. This is used by the wifi p2p
 * group owner.
 */
public class GroupOwnerSocketHandler extends Thread {

    LocalBroadcastManager broadcaster;
    ServerSocket socket = null;
    private Handler handler;
    private static final String TAG = "GroupOwnerSocketHandler";
    private ChatManager chat;

    public GroupOwnerSocketHandler(Handler handler, int port,Context context) throws IOException {
        try {
            this.broadcaster = LocalBroadcastManager.getInstance(context);
            socket = new ServerSocket(port);
            this.handler = handler;
            Log.d("GroupOwnerSocketHandler", "Socket Started");
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

    }

    /**
     * A ThreadPool for client sockets.

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
            THREAD_COUNT, THREAD_COUNT, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

     */
    @Override
    public void run() {
        while (true) {
            try {
                // A blocking operation. Initiate a ChatManager instance when
                // there is a new connection
                Socket  s = socket.accept();
                Log.d(TAG, "Launching the Group I/O handler");
                chat = new ChatManager(s, handler);
                new Thread(chat).start();

            } catch (IOException e) {
                try {
                    if (socket != null && !socket.isClosed())
                        socket.close();
                } catch (IOException ioe) {

                }
                e.printStackTrace();

                break;
            }
        }
    }

    public ChatManager getChat() {
        return chat;
    }

}
