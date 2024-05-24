package sky.ikaros.logger;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;

public class WebSocketServer extends NanoWSD {
    private static final String TAG = "Websocket";
    private Class<? extends WebSocket> webSocketClass;
    private Context context;

    public WebSocketServer(){
        this(8080);
    }
    public WebSocketServer(int port) {
        this(null, port);
    }

    public WebSocketServer(String hostname, int port) {
        super(hostname, port);
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        } catch (IOException e) {
            Log.e(TAG, "the Websocket start error", e);
        }
    }

    /**
     * 注册websocket实例，该实例继承自fi.iki.elonen.NanoWSD.WebSocket
     * @param webSocketClass fi.iki.elonen.NanoWSD.WebSocket的子类
     */
    public void registerWebSocket(Class<? extends WebSocket> webSocketClass){
        registerWebSocket(null, webSocketClass);
    }
    public void registerWebSocket(Context context, Class<? extends WebSocket> webSocketClass){
        this.webSocketClass = webSocketClass;
        this.context = context;
    }
    public void closeAllConnections(){
        super.closeAllConnections();
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        try {
            if(context == null){
                Constructor<? extends WebSocket> constructor = webSocketClass.getConstructor(IHTTPSession.class);
                return constructor.newInstance(handshake);
            }
            Constructor<? extends WebSocket> constructor = webSocketClass.getConstructor(Context.class, IHTTPSession.class);
            return constructor.newInstance(context, handshake);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException |
                 InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
