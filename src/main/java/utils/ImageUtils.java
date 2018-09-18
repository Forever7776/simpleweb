package utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageUtils {


    public static final ImageUtils ME = new ImageUtils();

    private static final String SUFFIXES = "jpeg|gif|jpg|png|bmp|svg|webp";


    public static String getSuffix(String name) {
        Pattern pat = Pattern.compile("[\\w]+[\\.](" + SUFFIXES + ")");// 正则判断
        Matcher mc = pat.matcher(name.toLowerCase());// 条件匹配
        String fileName = null;
        while (mc.find()) {
            fileName = mc.group();// 截取文件名后缀名
        }
        if (fileName != null) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return null;
    }
}
