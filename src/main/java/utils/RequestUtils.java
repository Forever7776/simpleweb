package utils;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 用于处理HTTP请求的工具类
 */
public class RequestUtils {
    public final static String NOT_USE_HTTP_ONLY_COOKIE = "NOT_USE_HTTP_ONLY_COOKIE";
    public final static String USER_AGENT = "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)";

    /**
     * 获取浏览器提交的整形参数
     *
     * @param param
     * @param defaultValue
     * @return
     */
    public static int getParam(String param, int defaultValue) {
        return NumberUtils.toInt(param, defaultValue);
    }

    /**
     * 获取浏览器提交的整形参数
     *
     * @param param
     * @return
     */
    public static int getParam(String param, HttpServletRequest req) {
        return NumberUtils.toInt(req.getParameter(param));
    }

    /**
     * 获取浏览器提交的整形参数
     *
     * @param param
     * @param defaultValue
     * @return
     */
    public static long getParam(String param, long defaultValue, HttpServletRequest req) {
        return NumberUtils.toLong(req.getParameter(param), defaultValue);
    }

    /**
     * 获取浏览器提交的整形参数
     *
     * @param param
     * @param defaultValue
     * @return
     */
    public static int getParam(String param, int defaultValue, HttpServletRequest req) {
        return NumberUtils.toInt(req.getParameter(param), defaultValue);
    }

    public static long[] getParamValues(String[] values) {
        if (values == null) return null;
        return (long[]) ConvertUtils.convert(values, long.class);
    }

    /**
     * 获取浏览器提交的字符串参�?
     *
     * @param param
     * @param defaultValue
     * @return
     */
    public static String getParam(String param, String defaultValue) {
        return (StringUtils.isEmpty(param)) ? defaultValue : param;
    }

    /**
     * 获取浏览器提交的字符串参�?
     *
     * @param param
     * @param defaultValue
     * @return
     */
    public static String getParam(HttpServletRequest req, String param, String defaultValue) {
        String value = req.getParameter(param);
        return (StringUtils.isEmpty(value)) ? defaultValue : value;
    }

    /**
     * 获取客户端IP地址，此方法用在proxy环境中
     *
     * @param req
     * @return
     */
    public static String getRemoteAddr(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(ip)) {
            String[] ips = StringUtils.split(ip, ',');
            if (ips != null) {
                for (String tmpip : ips) {
                    if (StringUtils.isBlank(tmpip))
                        continue;
                    tmpip = tmpip.trim();
                    if (isIPAddr(tmpip) && !tmpip.startsWith("10.") && !tmpip.startsWith("192.168.") && !"127.0.0.1".equals(tmpip)) {
                        return tmpip.trim();
                    }
                }
            }
        }
        ip = req.getHeader("x-real-ip");
        if (isIPAddr(ip))
            return ip;
        ip = req.getRemoteAddr();
        if (ip.indexOf('.') == -1)
            ip = "127.0.0.1";
        return ip;
    }

    /**
     * 判断字符串是否是一个IP地址
     *
     * @param addr
     * @return
     */
    public static boolean isIPAddr(String addr) {
        if (StringUtils.isEmpty(addr))
            return false;
        String[] ips = StringUtils.split(addr, '.');
        if (ips.length != 4)
            return false;
        try {
            int ipa = Integer.parseInt(ips[0]);
            int ipb = Integer.parseInt(ips[1]);
            int ipc = Integer.parseInt(ips[2]);
            int ipd = Integer.parseInt(ips[3]);
            return ipa >= 0 && ipa <= 255 && ipb >= 0 && ipb <= 255 && ipc >= 0
                    && ipc <= 255 && ipd >= 0 && ipd <= 255;
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * 获取用户访问URL中的根域名
     * 例如: www.dlog.cn -> dlog.cn
     *
     * @return
     */
    public static String getDomainOfServerName(String host) {
        if (isIPAddr(host))
            return null;
        String[] names = StringUtils.split(host, '.');
        int len = names.length;
        if (len == 1) return null;
        if (len == 3) {
            return makeup(names[len - 2], names[len - 1]);
        }
        if (len > 3) {
            String dp = names[len - 2];
            if (dp.equalsIgnoreCase("com") || dp.equalsIgnoreCase("gov") || dp.equalsIgnoreCase("net") || dp.equalsIgnoreCase("edu") || dp.equalsIgnoreCase("org"))
                return makeup(names[len - 3], names[len - 2], names[len - 1]);
            else
                return makeup(names[len - 2], names[len - 1]);
        }
        return host;
    }

    private static String makeup(String... ps) {
        StringBuilder s = new StringBuilder();
        for (int idx = 0; idx < ps.length; idx++) {
            if (idx > 0)
                s.append('.');
            s.append(ps[idx]);
        }
        return s.toString();
    }
}
