package sky.ikaros.logger;

import android.content.Context;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class Logger {
    private static final String TAG = "Logger";
    private static boolean interrupt = false;
    private OutputStream out;
    private final Context context;
    private final LinkedBlockingQueue<LogType> queue = new LinkedBlockingQueue<>();
    public static final long FILE_SIZE = 4 * 1024 * 1024;

    public Logger(Context context) {
        this.context = context;
    }

    private void rename(File f) {
        File f2 = f.getParentFile();
        assert f2 != null;
        int len = Objects.requireNonNull(f2.list()).length - 1;
        boolean isOk = f.renameTo(new File(f.getAbsoluteFile() + "." + len));
    }
    private void checkFile() throws Exception{
        File file = context.getExternalFilesDir("logs");
        if(file == null) return;
        if(!file.exists()) file.mkdir();
        String fileName = String.format("%s/%s/%s.log", file.getAbsolutePath(), Utils.datetimeFormat("YYYY-MM-dd"), "Android-log");
        File file2 = new File(fileName);
        if (!Objects.requireNonNull(file2.getParentFile()).exists()) {
            file2.getParentFile().mkdir();
        }
        if(!file2.exists()) file2.createNewFile();
        if (file2.length() >= FILE_SIZE) {
            rename(file2);
            if (out == null) {
                out.close();
                out = null;
            }
        }
        if (out == null) out = new FileOutputStream(file2, true);
    }

    // 监听文件大小及文件日期
    private void listenFileSize() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (interrupt) timer.cancel();
                else {
                    try {
                        checkFile();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            }
        }, 0,1000);
    }

    //
    private void listenMessageToFile() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (interrupt) {
                    timer.cancel();
                    return;
                }
                if(out == null) return;
                LogType data = queue.poll();
                if(data == null) return;
                try {
                    String str = String.format("[%s] [%s] [%s] [%s] %s\r\n", data.time, data.tag, data.packageName, data.type, data.message);
                    out.write(str.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }, 0,250);
    }

    public void listen() {
        interrupt = false;
        listenFileSize();
        listenMessageToFile();
    }

    // 取消监听
    public void unlisten() {
        interrupt = true;
    }

    public void d(@NotNull String tag, @NotNull String message) {
        queue.offer(new LogType(Utils.datetimeFormat("YYYY-MM-dd HH:ss:mm"), tag, context.getPackageName(), "DEBUG", message));
        Log.d(tag, message);
    }

    public void i(@NotNull String tag, @NotNull String message) {
        queue.offer(new LogType(Utils.datetimeFormat("YYYY-MM-dd HH:ss:mm"), tag, context.getPackageName(), "INFO", message));
        Log.i(tag, message);
    }

    public void w(@NotNull String tag, @NotNull String message) {
        w(tag, message, null);
    }

    public void w(@NotNull String tag, @NotNull String message, Throwable tr) {
        queue.offer(new LogType(Utils.datetimeFormat("YYYY-MM-dd HH:ss:mm"), tag, context.getPackageName(), "WARN", message));
        if (tr != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            tr.printStackTrace(pw);
            final String result = sw.toString();
            queue.offer(new LogType(Utils.datetimeFormat("YYYY-MM-dd HH:ss:mm"), tag, context.getPackageName(), "WARN", result));
            pw.close();
            try {
                sw.close();
            } catch (IOException e) {
                //
            }
        }
        Log.w(tag, message);
    }

    public void e(@NotNull String tag, @NotNull String message) {
        e(tag, message, null);
    }

    public void e(@NotNull String tag, @NotNull String message, Throwable tr) {
        queue.offer(new LogType(Utils.datetimeFormat("YYYY-MM-dd HH:ss:mm"), tag, context.getPackageName(), "ERROR", message));
        if (tr != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            tr.printStackTrace(pw);
            final String result = sw.toString();
            queue.offer(new LogType(Utils.datetimeFormat("YYYY-MM-dd HH:ss:mm"), tag, context.getPackageName(), "ERROR", result));
            pw.close();
            try {
                sw.close();
            } catch (IOException e) {
                //
            }
        }
        Log.e(tag, message, tr);
    }
    public void close(){
        if(out == null) return;
        try {
            out.close();
            out = null;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }
}
