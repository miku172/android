package sky.ikaros.logger;

public class LogType {
    public String time;
    public String tag;
    public String packageName;
    public String type;
    public String message;

    public LogType(String time, String tag, String packageName, String type, String message) {
        this.time = time;
        this.tag = tag;
        this.packageName = packageName;
        this.type = type;
        this.message = message;
    }
}
