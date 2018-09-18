package utils;

import org.apache.commons.lang3.math.NumberUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用于格式化HTML的工具类
 */
public class HTML_Utils {

    public static void main(String[] args) {
        String html = "你好，这里是视频 http://coding.net/";
        System.out.println(HTML_Utils.autoMakeLink(html, false, true));
        //System.out.println("<p>Hello</p>".replaceAll("<(.+?)>", "&lt;$1&gt;"));
    }

    private final static Pattern url_pattern = Pattern.compile("http(s)?://([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&amp;=]*)?");
    private final static List<String> bad_domains = Arrays.asList("coding.net", "coding.io", "gitcafe.com", "csdn.net");

    /**
     * 自动为文本中的url生成链接
     *
     * @param txt
     * @param only_oschina
     * @param markdown
     * @return
     */
    public static String autoMakeLink(String txt, boolean only_oschina, boolean markdown) {
        StringBuilder html = new StringBuilder();
        int lastIdx = 0;
        Matcher matchr = url_pattern.matcher(txt);
        while (matchr.find()) {
            String str = matchr.group();
            html.append(txt.substring(lastIdx, matchr.start()));
            if (!only_oschina || StringUtils.containsIgnoreCase(str, "oschina.net") || StringUtils.containsIgnoreCase(str, "osctools.net") || StringUtils.containsIgnoreCase(str, "ostools.net") || StringUtils.containsIgnoreCase(str, "runjs.cn") || StringUtils.containsIgnoreCase(str, "gitee.com")) {
                boolean bad_domain = false;
                for (String domain : bad_domains) {
                    if (StringUtils.containsIgnoreCase(str, domain)) {
                        bad_domain = true;
                    }
                }
                if (bad_domain) {
                    html.append(str);
                } else if (markdown) {
                    html.append("[" + str + "](" + str + ")");
                } else {
                    html.append("<a href='" + str + "' rel='nofollow' target='_blank'>" + str + "</a>");
                }
            } else {
                html.append(str);
            }
            lastIdx = matchr.end();
        }
        html.append(txt.substring(lastIdx));
        return html.toString();
    }

    public final static Whitelist user_content_filter = Whitelist.basicWithImages();

    static {
        //添加emoji标签到白名单
        user_content_filter.addTags("embed", "object", "param", "span", "pre", "div", "h1", "h2", "h3", "h4", "h5", "h6", "table", "tbody", "tr", "th", "td", "s", "hr", "emoji", "del");
        user_content_filter.addAttributes("span", "style");
        user_content_filter.addAttributes("pre", "class");
        user_content_filter.addAttributes("div", "class", "align");
        user_content_filter.addAttributes("a", "target");
        user_content_filter.addAttributes("table", "style", "border", "bordercolor", "cellpadding", "cellspacing", "align");
        user_content_filter.addAttributes("img", "style", "border", "align");
        user_content_filter.addAttributes("th", "style", "align", "rowspan", "colspan");
        user_content_filter.addAttributes("td", "style", "align", "rowspan", "colspan");
        user_content_filter.addAttributes("object", "width", "height", "classid", "codebase", "data", "type");
        user_content_filter.addAttributes("param", "name", "value");
        user_content_filter.addAttributes("embed", "src", "quality", "width", "height", "allowFullScreen", "allowScriptAccess", "flashvars", "name", "type", "pluginspage");
        user_content_filter.addAttributes("emoji", "class", "data-name", "data-emoji", "align");
    }

    /**
     * 对用户输入内容进行过滤
     *
     * @param html
     * @return
     */
    public static String filterUserInputContent(String html) {
        if (StringUtils.isBlank(html)) {
            return "";
        }
        Document document = Jsoup.parse(html);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
        //document.select("br").append("\\n");
        //document.select("p").prepend("\\n\\n");
        //String context = document.html().replaceAll("\\\\n", "\n");
        html = Jsoup.clean(html, "http://www.oschina.net", user_content_filter);
        return filterEmbedSrc(html);
    }

    /**
     * 对用户输入内容进行过滤, 域名可选 by ls
     *
     * @param html
     * @return
     */
    public static String filterUserInputContent(String domain, String html) {
        if (StringUtils.isBlank(html)) {
            return "";
        }
        Document document = Jsoup.parse(html);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
        //document.select("br").append("\\n");
        //document.select("p").prepend("\\n\\n");
        //String context = document.html().replaceAll("\\\\n", "\n");
        html = Jsoup.clean(html, domain, user_content_filter);
        return filterEmbedSrc(html);
    }

    public static String filterEmbedSrc(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings(new Document.OutputSettings().prettyPrint(false));//makes html() preserve linebreaks and spacing
        Elements elems = doc.select("embed");
        for (int i = 0; i < elems.size(); i++) {
            Element elem = elems.get(i);
            try {
                //String src = elem.attr("src");
                //if (!StringUtils.startsWith(src, "http://"))
                //    elem.remove();
                int w = NumberUtils.toInt(elem.attr("width"), 1);
                int h = NumberUtils.toInt(elem.attr("height"), 1);
                if (w == 0 || h == 0) {
                    elem.remove();
                }
            } catch (Exception e) {
            }
        }
        return doc.body().html();
    }

    /**
     * 判断内容里面是否包含特殊字符
     *
     * @param str
     * @return true 包含
     */
    public static Boolean isSpecialChar(String str) {
        String regEx = "[`~@#$%^&*()+=|{}\\[\\].<>/￥…（）——+|【】]"; //这些字符判断为不是话题
        boolean b = false;
        if (str.contains("#") || str.length() > 39) {
            b = true;
        }
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            b = true;
        }
        return b;
    }

    /**
     * 只过滤头和尾部的空格
     *
     * @param str
     * @return
     */
    public static String filterBlankChar(String str) {
        return str.trim();
    }

    public static String preview(String html, int max_count) {
        if (html == null || html.length() <= max_count * 1.1) {
            return html;
        }
        int len = 0;
        StringBuffer prvContent = new StringBuffer();
        Document doc = new Cleaner(user_content_filter).clean(Jsoup.parse(html));
        Element e = doc.body();
        for (Element child : e.getAllElements()) {
            String text = child.html().trim();
            len += text.length();
            if (len >= max_count) {
                child.html(text.substring(0, text.length() - len + max_count) + "...");
                prvContent.append(child.outerHtml());
                break;
            } else {
                prvContent.append(child.outerHtml());
            }
        }
        String res = StringUtils.remove(prvContent.toString(), "<body>");
        res = StringUtils.remove(res, "</body>");
        return res;
    }

}
