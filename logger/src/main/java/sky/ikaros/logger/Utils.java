package sky.ikaros.logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {
    public static String datetimeFormat(final String reg, final Date date){
        SimpleDateFormat dateFormat = new SimpleDateFormat(reg, Locale.CHINA);
        return dateFormat.format(date);
    }
    public static String datetimeFormat(final String reg){
        return datetimeFormat(reg, new Date());
    }
}
