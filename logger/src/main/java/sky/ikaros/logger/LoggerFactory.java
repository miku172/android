package sky.ikaros.logger;

import android.content.Context;

public class LoggerFactory {
     private static Logger instance;
     private static Server server;

     public static void register(Context context){
         instance = new Logger(context);
         instance.listen();
     }

    public static Logger getLogger(){
        if(instance == null){
            throw new RuntimeException("please register this context first");
        }
        return instance;
    }
    public static void unregister(){
         if(instance != null) {
             instance.close();
             instance.unlisten();
         }
    }

    public static void startServer(Context context, int port){
         if(server == null) server = new Server(context, port);
    }
    public static void stopServer(){
         if(server != null) {
             server.stop();
             server = null;
         }
    }

}
