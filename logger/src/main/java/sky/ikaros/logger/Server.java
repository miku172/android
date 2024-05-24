package sky.ikaros.logger;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public class Server extends NanoHTTPD {
    private static final String TAG = "Server";
    private final Context context;
    private final String[] units = {"B", "KB", "MB", "GB", "TB"};
    private final ActivityManager am;
    private final int width;
    private final int height;
    private final StatFs statFs;
    private final ActivityManager.MemoryInfo mi;

    public Server(Context context, int port) {
        this(context, null, port);
    }

    public Server(Context context, String hostname, int port) {
        super(hostname, port);
        this.context = context;
        am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        width = context.getResources().getDisplayMetrics().widthPixels;
        height = context.getResources().getDisplayMetrics().heightPixels;
        statFs = new StatFs(Environment.getExternalStorageDirectory().getPath());
        mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        try {
            start();
        } catch (IOException e) {
            Log.e(TAG, "the Http Server start error", e);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.startsWith("/Api")) {
            return getResponseData(session, uri);
        }
        return getHtmlString(uri);
    }

    // 获取静态资源文件
    private Response getHtmlString(String fileName) {
        if("/".equals(fileName)) fileName = "/index.html";
        try {
            InputStream in = context.getResources().getAssets().open("www" + fileName);
            return newChunkedResponse(Response.Status.OK, getHTMLType(fileName), in);
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
            return newChunkedResponse(Response.Status.NOT_FOUND, getHTMLType(fileName), null);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            return newChunkedResponse(Response.Status.INTERNAL_ERROR, getHTMLType(fileName), null);
        }
    }

    private String getHTMLType(String fileName) {
        String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        switch (suffix) {
            case "html":
                return "text/html";
            case "css":
                return "text/css";
            case "ico":
            case "png":
                return "text/png";
            case "jpg":
            case "jpeg":
                return "text/jpg";
            case "svg":
            case "xml":
                return "text/xml";
            case "js":
                return "application/x-javascript";
        }
        return "text/plain";
    }

    // 获取数据
    private Response getResponseData(IHTTPSession session, String url) {
        if ("/Api/System.Core/GetList".equals(url)) {
            return getSystemCoreGetListApi();
        } else if ("/Api/System.Core/GetListByDate".equals(url)) {
            return getSystemCoreGetListByDateApi(session);
        } else if ("/Api/System.Core/GetInfoByName".equals(url)) {
            return getSystemCoreGetInfoByNameApi(session);
        }else if("/Api/System.Core/DeleteDirectoryByDate".equals(url)){
            return deleteDirectoryByDateApi(session);
        } else if("/Api/System.Core/DeleteFileByName".equals(url)){
            return deleteFileByNameApi(session);
        }else if("/Api/System.Core/GetSystemInfo".equals(url)){
            return getSystemInfo(session);
        }else if("/Api/System.Core/GetAppCPUInfo".equals(url)){
            return getAppCpuInfo(session);
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/text", setResponseData(404, String.format("the url of %s does not implement", url), 0, null));
    }

    // 获取日志时段列表
    private Response getSystemCoreGetListApi() {
        File file = context.getExternalFilesDir("logs");
        if (file == null) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "", 0, new JSONArray()));
        }
        JSONArray list = new JSONArray();
        String[] list2 = file.list();
        for (String s : list2) {
            File f = new File(file, s);
            JSONObject map = new JSONObject();
            try {
                map.put("type", f.isDirectory() ? "directory" : "file");
                map.put("name", f.getName());
                map.put("size", getUnit(f.length()));
                list.put(map);
            } catch (JSONException e) {
                //
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "", list2.length, list));
    }

    // 根据日期获取日志列表
    private Response getSystemCoreGetListByDateApi(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String dirName = params.get("date");
        File file = context.getExternalFilesDir("logs");
        if (file == null) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "", 0, new JSONArray()));
        }
        if (dirName == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/text", setResponseData(400, "the request param of date requires an value", 0, null));
        }
        File f = new File(file, dirName);
        JSONArray arr = new JSONArray();
        String[] list = f.list();
        for (String s : list) {
            File f2 = new File(f, s);
            JSONObject json = new JSONObject();
            try {
                json.put("type", f2.isDirectory() ? "directory" : "file");
                json.put("name", f2.getName());
                json.put("size", getUnit(f2.length()));
                arr.put(json);
            } catch (JSONException e) {
                //
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "", list.length, arr));
    }

    // 根据文件名获取日志内容
    private Response getSystemCoreGetInfoByNameApi(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String fileName = params.get("name");
        String dirName = params.get("date");
        File file = context.getExternalFilesDir("logs");
        if (file == null) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "", 0, new JSONArray()));
        }
        if (dirName == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/text", setResponseData(400, "the request param of date requires an value", 0, null));
        }
        if (fileName == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/text", setResponseData(400, "the request param of name requires an value", 0, null));
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(String.format("%s/%s/%s", file.getAbsolutePath(), dirName, fileName))));
            String lineData = null;
            Pattern pattern = Pattern.compile("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\]\\s\\[(.+)\\]\\s\\[(.+)\\]\\s\\[(.+)\\]\\s(.*)$");
            JSONArray arr = new JSONArray();
            while ((lineData = br.readLine()) != null) {
                Matcher m = pattern.matcher(lineData);
                boolean matches = m.matches();
                if (!matches) continue;
                JSONObject json = new JSONObject();
                try{
                    String time = m.group(1);
                    if(time != null) time = time.replaceAll(" ", "&nbsp;&nbsp;&nbsp;&nbsp;");
                    String tag = m.group(2);
                    if(tag != null) tag = tag.replaceAll(" ", "&nbsp;&nbsp;&nbsp;&nbsp;");
                    String packageName = m.group(3);
                    if(packageName != null) packageName = packageName.replaceAll(" ", "&nbsp;&nbsp;&nbsp;&nbsp;");
                    String type = m.group(4);
                    if(type != null) type = type.replaceAll(" ", "&nbsp;&nbsp;&nbsp;&nbsp;");
                    String message = m.group(5);
                    if(message != null) message = message.replaceAll(" ", "&nbsp;&nbsp;&nbsp;&nbsp;");
                    json.put("time", time);
                    json.put("tag", tag);
                    json.put("package", packageName);
                    json.put("type", type);
                    json.put("value", message);
                    arr.put(json);
                }catch (JSONException e){
                    //
                }
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "", arr.length(), arr));
        } catch (FileNotFoundException e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/text", setResponseData(404, String.format("the file is not found, date=%s，name=%s", dirName, fileName), 0, null));
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/text", setResponseData(500, String.format("the file read fail, date=%s，name=%s", dirName, fileName), 0, null));
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }
    // 删除具体的日志文件
    private Response deleteFileByNameApi(IHTTPSession session){
        Map<String, String> params = session.getParms();
        String fileName = params.get("name");
        String dirName = params.get("date");
        File file = context.getExternalFilesDir("logs");
        if (file == null) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, fileName + "文件不存在", 0, null));
        }
        if (dirName == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/text", setResponseData(400, "the request param of date requires an value", 0, null));
        }
        if (fileName == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/text", setResponseData(400, "the request param of name requires an value", 0, null));
        }
        File file2 = new File(String.format("%s/%s/%s", file.getAbsolutePath(), dirName, fileName));
        boolean isSuccess = file2.delete();
        return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, isSuccess ? "已删除" : "删除失败", 0, null));
    }
    // 删除文件夹
    private Response deleteDirectoryByDateApi(IHTTPSession session){
        Map<String, String> params = session.getParms();
        String dirName = params.get("date");
        File file = context.getExternalFilesDir("logs");
        if (file == null) {
            return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "不存在日志文件", 0, null));
        }
        if (dirName == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/text", setResponseData(400, "the request param of date requires an value", 0, null));
        }
        File f = new File(file, dirName);
        for(String f2: f.list()){
            File f3 = new File(f, f2);
            f3.delete();
        }
        boolean isOK = f.delete();
        return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, isOK? "已删除" : "删除失败", 0, null));
    }
    // 获取系统信息
    private Response getSystemInfo(IHTTPSession session){
        JSONObject json = new JSONObject();
        try {
            json.put("model", Build.MODEL);
            json.put("screenWidth", width);
            json.put("screenHeight", height);
            //
            long totalSize = statFs.getTotalBytes();
            long availableSize = statFs.getAvailableBytes();
            json.put("totalStorage", getUnit(totalSize));
            json.put("availableStorage", getUnit(availableSize));
            //
            json.put("totalMemory", getUnit(mi.totalMem));
            json.put("availableMemory", getUnit(mi.availMem));
            //
            json.put("appTotalMemory", String.format(Locale.getDefault(), "%.2fMB", Runtime.getRuntime().totalMemory() / 1024f / 1024f));
            json.put("appMaxMemory", String.format(Locale.getDefault(), "%.2fMB", Runtime.getRuntime().maxMemory() / 1024f / 1024f));
            json.put("appFreeMemory", String.format(Locale.getDefault(), "%.2fMB", Runtime.getRuntime().freeMemory() / 1024f / 1024f));
            //
            json.put("time", Utils.datetimeFormat("YYYY-MM-dd HH:mm:ss"));
        } catch (JSONException e) {
            //
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", setResponseData(0, "", json));
    }

    /**
     * 获取app cpu信息
     * <p>先获取当前的进程名，在进程中获取所有的线程，在线程中获取cpu的使用率</p>
     * @param session IHTTPSession
     * @return Response
     */
    public Response getAppCpuInfo(IHTTPSession session) {
        int processId = android.os.Process.myPid();

        return null;
    }
    // 单位转换
    private String getUnit(float size) {
        int index = 0;
        while (size > 1024 && index < 4) {
            size = size / 1024;
            index++;
        }
        return String.format(Locale.getDefault(), "%.2f%s", size, units[index]);
    }
    // 返回数据结构
    private String setResponseData(int code, String msg, int count, JSONArray list) {
        JSONObject json = new JSONObject();
        try {
            json.put("code", code);
            json.put("msg", msg);
            json.put("count", count);
            json.put("data", list);
        } catch (JSONException e) {
            //
        }
        return json.toString();
    }
    private String setResponseData(int code, String msg, JSONObject obj) {
        JSONObject json = new JSONObject();
        try {
            json.put("code", code);
            json.put("msg", msg);
            json.put("data", obj);
        } catch (JSONException e) {
            //
        }
        return json.toString();
    }
}
