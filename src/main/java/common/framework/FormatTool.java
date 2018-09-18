package common.framework;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import utils.HTML_Utils;
import utils.StringUtils;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 格式化工具
 *
 */
public class FormatTool {

    private final static Pattern mobiler = Pattern.compile("^(((13[0-9])|(14[5,7])|(15([0-3]|[5-9]))|(17[0,6-8])|(18[0-9]))\\d{8})|(0\\d{2}-\\d{8})|(0\\d{3}-\\d{7})$");

    public static String preview(String content, int count) {
        return HTML_Utils.preview(content, count);
    }

    public static String removeImagesLink(String html) {
        if (StringUtils.isBlank(html)) {
            return html;
        }
        Element elems = Jsoup.parseBodyFragment(html).body();
        Elements imgs = elems.select("img");
        for (Element img : imgs) {
            img.removeAttr("data-cke-saved-src");
            List<Node> tmps = new ArrayList<Node>();
            Node n = img;
            do {
                if (n == null) {
                    break;
                }
                tmps.add(n);
                n = n.nextSibling();
            } while (true);
            Element parent = img.parent();
            parent.replaceWith(new Element(Tag.valueOf("span"), "").insertChildren(0, tmps));
        }
        return elems.html();
    }

    /**
     * 格式化评论输出
     *
     * @param text
     * @return
     */
    public static String comment(String text) {
        //内容清洗
        text = HTML_Utils.autoMakeLink(FormatTool.html(text), true, false);
        text = StringUtils.replace(text, "\r\n", "<br/>");
        text = StringUtils.replace(text, "\r", "<br/>");
        text = StringUtils.replace(text, "\n", "<br/>");
        return text;
    }

    public int i(Object obj, int def) {
        if (obj != null && obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return def;
    }

    public static List<Integer> splite_strings_to_int(String str, String c) {
        if (StringUtils.isBlank(str)) {
            return null;
        }
        Integer[] nums = (Integer[]) ConvertUtils.convert(StringUtils.split(str, c), Integer.class);
        return Arrays.asList(nums);
    }

    public static long to_long(Object str) {
        if (str instanceof Number) {
            return ((Number) str).longValue();
        }
        return NumberUtils.toLong((String) str, -1L);
    }

    public static String price(int price) {
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        nf.setGroupingUsed(false);
        return nf.format(price / 100.0);
    }

    /**
     * 删除html中多余的空白
     *
     * @param html
     * @return
     */
    public static String trim_html(String html) {
        return StringUtils.replace(StringUtils.replace(html, "\r\n", ""), "\t", "");
    }

    /**
     * 格式化HTML文本
     *
     * @param content
     * @return
     */
    public static String text(String content) {
        if (content == null) {
            return "";
        }
        String html = StringUtils.replace(content, "<", "&lt;");
        return StringUtils.replace(html, ">", "&gt;");
    }

    /**
     * 格式化HTML文本
     *
     * @param content
     * @return
     */
    public static String html(String content) {
        if (content == null) {
            return "";
        }
        String html = content;
        html = StringUtils.replace(html, "'", "&apos;");
        html = StringUtils.replace(html, "\"", "&quot;");
        html = StringUtils.replace(html, "\t", "&nbsp;&nbsp;");// 替换跳格
        //html = StringUtils.replace(html, " ", "&nbsp;");// 替换空格
        html = StringUtils.replace(html, "<", "&lt;");
        html = StringUtils.replace(html, ">", "&gt;");
        return html;
    }

    /**
     * 还原html文本
     *
     * @param content
     * @return
     */
    public static String reHtml(String content) {
        if (content == null) {
            return "";
        }
        String html = content;
        html = StringUtils.replace(html, "&apos;", "'");
        html = StringUtils.replace(html, "&quot;", "\"");
        html = StringUtils.replace(html, "&nbsp;&nbsp;", "\t");// 替换跳格
        html = StringUtils.replace(html, "&lt;", "<");
        html = StringUtils.replace(html, "&gt;", ">");
        return html;
    }

    /**
     * html内容中pre标签中增加code标签包裹，如：<pre>123</pre>，修改为<pre><code>123</code></pre>
     *
     * @param htmlText
     * @return
     */
    public static String addCodeTagInnterToPreTag(String htmlText) {
        Document htmlDocument = Jsoup.parse(htmlText);
        htmlDocument.getElementsByTag("pre")
                .stream()
                .filter(element -> element.getElementsByTag("code").size() <= 0)
                .forEach(element -> {
                    element.html("<code>" + element.html() + "</code>");
                });
        return htmlDocument.body().html();
    }

    /**
     * 字符串截取
     *
     * @param str
     * @param maxLength
     * @return
     */
    public static String abbr_plaintext(String str, int maxLength) {
        return StringUtils.trim(abbr(plain_text(str), maxLength));
    }

    /**
     * 格式化HTML文本
     *
     * @param content
     * @return
     */
    public static String rhtml(String content) {
        if (StringUtils.isBlank(content)) {
            return content;
        }
        String html = content;
		/*
		html = StringUtils.replace(html, "&", "&amp;");
		html = StringUtils.replace(html, "'", "&apos;");
		html = StringUtils.replace(html, "\"", "&quot;");
		*/
        html = StringUtils.replace(html, "&", "&amp;");
        html = StringUtils.replace(html, "<", "&lt;");
        html = StringUtils.replace(html, ">", "&gt;");
        return html;
    }


    /**
     * 字符串智能截断（保留最后句子的完整性），最后结果长度可能超过maxWidth
     *
     * @param str
     * @param maxWidth
     * @return
     */
    // TODO 这方法截取有bug，不智能
    public static String abbreviate_plaintext(String str, int maxWidth) {
        return StringUtils.trim(abbreviate(plain_text(str), maxWidth));
    }

    /**
     * 从一段HTML中萃取纯文本
     *
     * @param html
     * @return
     */
    public static String plain_text(String html) {
        return StringUtils.getPlainText(html);
    }

    public static String wml(String content) {
        return html(content);
    }

    /**
     * 字符串智能截断
     *
     * @param str
     * @param maxWidth
     * @return
     */
    public static String abbreviate(String str, int maxWidth) {
        return _Abbr(str, maxWidth);
        //if(str==null) return null;
        //return StringUtils.abbreviate(str,maxWidth);
    }

    public static String abbr(String str, int maxWidth) {
        return StringUtils.abbreviate(str, maxWidth);
    }

    private static String _Abbr(String str, int count) {
        if (str == null) {
            return null;
        }
        if (str.length() <= count) {
            return str;
        }
        StringBuilder buf = new StringBuilder();
        int len = str.length();
        int wc = 0;
        int ncount = 2 * count - 3;
        for (int i = 0; i < len; ) {
            if (wc >= ncount) break;
            char ch = str.charAt(i++);
            buf.append(ch);
            wc += 2;
            if (wc >= ncount) break;
            if (CharUtils.isAscii(ch)) {
                wc -= 1;
                //read next char
                if (i >= len) break;
                char nch = str.charAt(i++);
                buf.append(nch);
                if (!CharUtils.isAscii(nch))
                    wc += 2;
                else
                    wc += 1;
            }
        }
        buf.append("...");
        return buf.toString();
    }

    public static String Abbr(String str, int count) {
        if (str == null) {
            return null;
        }
        if (str.length() <= count) {
            return str;
        }
        StringBuilder buf = new StringBuilder();
        int len = str.length();
        int wc = 0;
        int ncount = 2 * count - 3;
        for (int i = 0; i < len; ) {
            if (wc >= ncount) break;
            char ch = str.charAt(i++);
            buf.append(ch);
            wc += 2;
            if (wc >= ncount) break;
            if (CharUtils.isAscii(ch)) {
                wc -= 1;
                //read next char
                if (i >= len) break;
                char nch = str.charAt(i++);
                buf.append(nch);
                if (!CharUtils.isAscii(nch))
                    wc += 2;
                else
                    wc += 1;
            }
        }
        return buf.toString();
    }

    public static boolean is_empty(String str) {
        return str == null || str.trim().length() == 0;
    }

    public static boolean not_empty(String str) {
        return !is_empty(str);
    }

    public static boolean is_number(String str) {
        return StringUtils.isNotBlank(str) && StringUtils.isNumeric(str);
    }

    private final static Pattern emailer = Pattern.compile("\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*");
    private final static Pattern link = Pattern.compile("[a-zA-z]+://[^\\s]*");

    /**
     * 判断是不是一个合法的电子邮件地址
     *
     * @param email
     * @return
     */
    public static boolean is_email(String email) {
        if (StringUtils.isBlank(email)) {
            return false;
        }
        email = email.toLowerCase();
        if (email.endsWith("nepwk.com")) {
            return false;
        }
        if (email.endsWith(".con")) {
            return false;
        }
        if (email.endsWith(".cm")) {
            return false;
        }
        if (email.endsWith("@gmial.com")) {
            return false;
        }
        if (email.endsWith("@gamil.com")) {
            return false;
        }
        if (email.endsWith("@gmai.com")) {
            return false;
        }
        return emailer.matcher(email).matches();
    }

    public static boolean is_link(String slink) {
        if (StringUtils.isBlank(slink)) {
            return false;
        }
        return link.matcher(slink).matches();
    }


    public static String replace(String text, String repl, String with) {
        return StringUtils.replace(text, repl, with);
    }

    /**
     * 按给定的模式格式化数字
     * 如：$format.number($0.2345,'##.##%')返回23.45%
     *
     * @param number
     * @param pattern @see DecimalFormat.applyPattern()
     * @return
     */
    public static String number(double number, String pattern) {
        DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance();
        df.applyPattern(pattern);

        return df.format(number);
    }

    public static long toLong(String str) {
        return Long.parseLong(str);
    }

    public static int toInt(String str) {
        return Integer.parseInt(str);
    }

    private final static Pattern phonePattern = Pattern.compile("^1[3|4|5|7|8][0-9]\\d{8}$");
    private final static Pattern qqPattern = Pattern.compile("^\\d{5,11}$");

    /**
     * 判断手机号码
     *
     * @param qq
     * @return
     */
    public static boolean isQQ(String qq) {
        if (StringUtils.isBlank(qq)) {
            return false;
        }
        return qqPattern.matcher(qq).matches();
    }

    /**
     * 判断手机号码
     *
     * @param number
     * @return
     */
    public static boolean isPhoneNumber(String number) {
        if (StringUtils.isBlank(number)) {
            return false;
        }
        return phonePattern.matcher(number).matches();
    }

    public static boolean isMobile(String number) {
        if (StringUtils.isBlank(number)) {
            return false;
        }
        return mobiler.matcher(number).matches();
    }

    /**
     * 过滤ascii码不可见字符(如果数据存在ascii码不可见字符，则客户端接口解析异常)
     * 其实属于ascii码中的控制字符，它们是0到31、以及127
     * 排除0、7-13（转义字符）
     *
     * @param content
     * @return
     */
    public static String invisibleAsciiFilter(String content) {
        if (content != null && content.length() > 0) {
            char[] contentCharArr = content.toCharArray();
            for (int i = 0; i < contentCharArr.length; i++) {
                if (contentCharArr[i] < 0x20) {
                    if (contentCharArr[i] != 0x00 && (contentCharArr[i] < 0x07 || contentCharArr[i] > 0x0D)) {
                        contentCharArr[i] = 0x20;
                    }
                } else if (contentCharArr[i] == 0x7F) {
                    contentCharArr[i] = 0x20;
                }
            }
            return new String(contentCharArr);
        }

        return "";
    }

    /**
     * 根据记录数和每页现实文章数确定页数
     *
     * @param recordCount
     * @param perPage
     * @return
     */
    public static int page_count(long recordCount, int perPage) {
        int pc = (int) Math.ceil(recordCount / (double) perPage);
        return (pc == 0) ? 1 : pc;
    }

    /**
     * 时间规则如下：
     * now和date做比对：
     * 1.now - date < 1分钟 return 刚刚
     * 2.now - date < 1个小时 return XX分钟前
     * 3.now - date < 12小时  return XX小时前
     * 4.day = 0 天 return 今天
     * 5.day = 1 天 return 昨天
     * 6.day = 2 天 return 前天
     * 7.其他 return yyyy/MM/dd 时间格式
     *
     * @param date
     * @return
     */
    public static String format_intell_time(Date date) {
        return date != null ? format_intell_timestamp(new Timestamp(date.getTime())) : null;
    }

    public static String format_intell_timestamp(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        Date date = new Date(ts.getTime());
        Date now = new Date();
        long ys = DateUtils.truncate(now, Calendar.YEAR).getTime();
        long today = DateUtils.truncate(now, Calendar.DAY_OF_MONTH).getTime();
        long yesterday = today - 24L * 60 * 60 * 1000;
        long beforeYesterday = yesterday - 24L * 60 * 60 * 1000;
        long n = now.getTime();

        long e = date.getTime();

        if (e < ys) {
            return new SimpleDateFormat("yyyy/MM/dd HH:mm").format(date);
        }
        if (e < beforeYesterday) {
            return new SimpleDateFormat("MM/dd HH:mm").format(date);
        }
        if (e < yesterday) {
            return new SimpleDateFormat("前天 HH:mm").format(date);
        }
        if (e < today) {
            return new SimpleDateFormat("昨天 HH:mm").format(date);
        }
        if (n - e > 60L * 60 * 1000) {
            return new SimpleDateFormat("今天 HH:mm").format(date);
        }
        if (n - e > 60 * 1000) {
            return (long) Math.floor((n - e) * 1d / 60000) + "分钟前";
        }
        if (n - e >= 0) {
            return "刚刚";
        }
        return new SimpleDateFormat("yyyy/MM/dd HH:mm").format(date);
    }

    public static String format_intell_time_4blog(Date date) {
        return date != null ? format_intell_time_4blog(new Timestamp(date.getTime())) : null;
    }

    public static String format_intell_time_4blog(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        Date date = new Date(ts.getTime());
        Date now = new Date();
        long ys = DateUtils.truncate(now, Calendar.YEAR).getTime();
        long today = DateUtils.truncate(now, Calendar.DAY_OF_MONTH).getTime();
        long yesterday = today - 24L * 60 * 60 * 1000;
        long beforeYesterday = yesterday - 24L * 60 * 60 * 1000;
        long n = now.getTime();

        long e = date.getTime();

        if (e < ys) {
            return new SimpleDateFormat("yyyy/MM/dd").format(date);
        }
        if (e < beforeYesterday) {
            return new SimpleDateFormat("MM/dd").format(date);
        }
        if (e < yesterday) {
            return new SimpleDateFormat("前天").format(date);
        }
        if (e < today) {
            return new SimpleDateFormat("昨天").format(date);
        }
        if (n - e > 60L * 60 * 1000) {
            return new SimpleDateFormat("今天").format(date);
        }
        if (n - e > 60 * 1000) {
            return (long) Math.floor((n - e) * 1d / 60000) + "分钟前";
        }
        if (n - e >= 0) {
            return "刚刚";
        }
        return new SimpleDateFormat("yyyy/MM/dd").format(date);
    }


    private static int sub(Date now, Date date, int TYPE) {
        Calendar dateCalendar = Calendar.getInstance();
        dateCalendar.setTime(date);
        Calendar nowCalendar = Calendar.getInstance();
        nowCalendar.setTime(now);
        return nowCalendar.get(TYPE) - dateCalendar.get(TYPE);
    }

    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat formatter = new SimpleDateFormat(pattern);
        return formatter.format(date);
    }

    private final static Pattern phonePatternSimple = Pattern.compile("^\\d{11}$");

    /**
     * 判断手机号码
     *
     * @param number
     * @return
     */
    public static boolean isPhoneNumberSimple(String number) {
        if (StringUtils.isBlank(number)) {
            return false;
        }
        return phonePatternSimple.matcher(number).matches();
    }

    public static boolean isPhone(String number) {
        if (StringUtils.isBlank(number)) {
            return false;
        }
        return mobiler.matcher(number).matches();
    }

    private static long subTime(Date now, Date date) {
        long nowTime = now.getTime();
        long dateTime = date.getTime();
        return nowTime - dateTime;
    }

    public static String filterNumberK(Object obj) {
        long size = 0;
        if (obj != null) {
            if (obj instanceof Integer) {
                size = Long.valueOf((int) obj);
            }
            if (obj instanceof Long) {
                size = (long) obj;
            }
            if (size < 1000) {
                return size + "";
            }
            if (size / 1000 == 1) {
                return (size / 1000) + "K";
            }
            if (size / 1000 > 1) {
                Double sized = Double.valueOf(size) / 1000;
                Double result = Double.valueOf(String.format("%.1f", sized));
                if (result.intValue() - result == 0) {
                    return result.intValue() + "K";
                }
                return result + "K";
            }
            return "0";
        }
        return "0";
    }

    /**
     * 搜索关键字高亮显示
     *
     * @param key
     * @param text TODO 搜索高亮
     * @return
     */
    public static String highlight(String key, String text) throws Exception {
        return text;
    }

    /**
     * 将手机号码格式化为137****4567
     *
     * @param phoneNumber
     * @return
     */
    public static String formatPhoneNumber(String phoneNumber) {
        if (StringUtils.isBlank(phoneNumber)) {
            return "";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(7, phoneNumber.length());
    }

    /**
     * 截图评论内容的非引用内容（博客，翻译，新闻）
     *
     * @param html 原评论内容
     * @return 非引用内容
     */
    public static String interceptCommentContent(String html) {
        html = "<div class='detail'>" + html + "</div>";
        Document doc = Jsoup.parse(html);
        Elements refs = doc.getElementsByClass("ref");
        if (refs == null || refs.size() == 0) {
            return html;
        }
        refs.remove();
        Elements detail = doc.getElementsByClass("detail");
        return detail.html();
    }

    /**
     * 对一些特殊的字符进行替换
     *
     * @param text 字符串
     * @return String
     */
    public static String specialCharReplace(String text) {
        return StringUtils.replace(text, "&nbsp;", "&amp;nbsp;");
    }


    /**
     * 检查输入的最大长度
     *
     * @param input
     * @param high
     * @param include
     * @return
     */
    public static boolean checkInputMaxLength(String input, int high, boolean include) {
        int length = StringUtils.length(input);
        if (length < high || (include && length == high)) {
            return true;
        }
        return false;
    }

    /**
     * 自动为url生成链接
     *
     * @param text
     * @param only_oschina
     * @return
     */
    public static String auto_url(String text, boolean only_oschina) {
        return HTML_Utils.autoMakeLink(text, only_oschina, false);
    }

    public static byte toByte(int number) {
        return (byte) number;
    }

    /**
     * 检查所有输入是否有空值
     *
     * @param inputList
     * @return true:有空值；false:无空值
     */
    public static boolean isInputBlank(String... inputList) {
        for (String input : inputList) {
            if (StringUtils.isBlank(input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查输入值，是否介于指定范围内
     *
     * @param input
     * @param small
     * @param big
     * @param includeSmall
     * @param includeBig
     * @return
     */
    public static boolean checkInputValue(int input, int small, int big, boolean includeSmall, boolean includeBig) {
        if ((input > small && input < big) || (includeSmall && (input == small)) || (includeBig && (input == big))) {
            return true;
        }
        return false;
    }
}
