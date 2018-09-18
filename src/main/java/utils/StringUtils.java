package utils;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * describe:
 * 字符串扩展工具类
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {

    private final static Pattern words_pattern = Pattern.compile("[,，]+");
    private final static Pattern words_pattern2 = Pattern.compile("[,， ]+");
    private final static Pattern symbol_pattern = Pattern.compile("(?i)[^a-zA-Z0-9\u4E00-\u9FA5]");//非中文、大小写英文、阿拉伯数字正则
    private final static Pattern chinese_pattern = Pattern.compile("^[\u4e00-\u9fa5]+$");//中文字符
    public static final String GIF = "gif";

    /**
     * 判断是否中文
     *
     * @param string
     * @return
     */
    public static boolean isChinese(String string) {
        if (org.apache.commons.lang3.StringUtils.isBlank(string)) {
            return false;
        }
        return chinese_pattern.matcher(string).matches();
    }

    /**
     * 获取html文本中的第一张图片地址
     *
     * @param htmlContent
     * @return
     */
    public static String getFirstImageUrl(String htmlContent) {
        if (StringUtils.isNotBlank(htmlContent)) {
            Document document = Jsoup.parse(htmlContent);
            Element element = document.select("img[src~=(?i)\\.(gif|png|bmp|svg|jpe?g)]").first();
            if (null != element) {
                return element.attr("src");
            }
        }
        return "";
    }

    public static String[] splitWords(String txt, boolean use_blank) {
        if (isBlank(txt)) {
            return null;
        }
        return (use_blank ? words_pattern2 : words_pattern).split(txt);
    }

    /**
     * 正则表达式特殊字符转义
     *
     * @param keyword 关键词
     * @return
     */
    public static String escapeExprSpecialWord(String keyword) {
        if (StringUtils.isNotBlank(keyword)) {
            String[] fbsArr = {"\\", "$", "(", ")", "*", "+", ".", "[", "]", "?", "^", "{", "}", "|"};
            for (String key : fbsArr) {
                if (keyword.contains(key)) {
                    keyword = keyword.replace(key, "\\" + key);
                }
            }
        }
        return keyword;
    }

    /**
     * 统计纯文本中词的数量
     *
     * @param text
     * @return
     */
    public static long countWordOfText(String text) {
        List<String> words = splitStringToWords(text);
        return words.parallelStream()
                .mapToLong(word -> StringUtils.isContainsChinese(word) ? word.length() : 1)
                .sum();
    }

    /**
     * 判断字符串中是否包含中文字符
     *
     * @param string
     * @return
     */
    public static boolean isContainsChinese(String string) {
        if (StringUtils.isNotBlank(string)) {
            for (char character : string.toCharArray()) {
                if (isChinese(String.valueOf(character))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 将字符串切分成词，并过滤“非中、英文、阿拉伯”字符
     *
     * @param text
     * @return
     */
    public static List<String> splitStringToWords(String text) {
        BreakIterator boundary = BreakIterator.getWordInstance();
        boundary.setText(text);
        List<String> resultList = new ArrayList<>();
        int start = boundary.first();
        for (int end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next()) {
            String str = text.substring(start, end);
            if (!isInvalidWord(str.replaceAll("(^\\h*)|(\\h*$)", ""))) {//trim no-break space,过滤U+0160
                resultList.add(str);
            }
        }
        return resultList;
    }

    /**
     * 是否是不合法字符
     *
     * @param word
     * @return
     */
    public static boolean isInvalidWord(String word) {
        if (StringUtils.isBlank(word)) {
            return true;
        }
        if (symbol_pattern.matcher(word).matches()) {
            return true;
        }
        return false;
    }

    /**
     * 从一段HTML中萃取纯文本
     *
     * @param html
     * @return
     */
    public static String getPlainText(String html) {
        if (StringUtils.isBlank(html)) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    /**
     * 从博客中获取用于微信分享的图片链接：大小要求300x300
     *
     * @param htmlContent
     * @param id          博客ID
     * @return
     */
    public static String getImageForWechatShare(String htmlContent, long id) {
        String originImageUrl = "http://ob2dt6f5q.bkt.clouddn.com/8c46d23f39154199af6d027da62f71cf/mmexport1521290618852.jpg";
        if (StringUtils.isNotBlank(htmlContent)) {
            Document document = Jsoup.parse(htmlContent);
            Elements elements = document.select("img[src~=(?i)\\.(gif|png|bmp|svg|jpe?g)]");
            if (null == elements) {
                return originImageUrl;
            }
            if (elements.size() == 0) {
                return originImageUrl;
            }
            try {
                for (int i = 0; i < elements.size(); i++) {
                    String imageUrl = elements.get(i).attr("src");
                    if (GIF.equalsIgnoreCase(ImageUtils.getSuffix(imageUrl))) {
                        return readGif(originImageUrl, imageUrl, id);
                    }
                    HttpClient httpClient = HttpClients.createDefault();
                    RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(1000).setConnectionRequestTimeout(1000).setSocketTimeout(1000).build();
                    HttpGet httpGet = new HttpGet(imageUrl);
                    httpGet.setConfig(requestConfig);
                    HttpResponse response = httpClient.execute(httpGet);
                    BufferedImage image = ImageIO.read(response.getEntity().getContent());
                    if (image != null && image.getWidth() > 300 && image.getHeight() > 300) {
                        //CacheMgr.set(Blog.CACHE_BLOGS_FIRST_IMAGE,String.valueOf(id),imageUrl);
                        return imageUrl;
                    } else {
                        return uploadToUp(originImageUrl, imageUrl, id);
                    }
                }
            } catch (Exception e) {
                return originImageUrl;
            }

        }
        return originImageUrl;
    }

    /**
     * 读取gif
     *
     * @param originImageUrl osc 300*300 LOGO
     * @param imageUrl       博客第一张图片
     * @param id             博客ID
     * @return
     */
    public static String readGif(String originImageUrl, String imageUrl, long id) {
        GifDecoder.GifImage gif = null;
        try {
            gif = GifDecoder.read(getImageFromNetByUrl(imageUrl));
        } catch (IOException e) {
            return originImageUrl;
        }
        if (gif.getWidth() > 300 && gif.getHeight() > 300) {
            //CacheMgr.set(Blog.CACHE_BLOGS_FIRST_IMAGE,String.valueOf(id),imageUrl);
            return imageUrl;
        } else {
            return uploadToUp(originImageUrl, imageUrl, id);
        }
    }

    /**
     * 上传图片至又拍云
     *
     * @param originImageUrl osc 300*300 LOGO
     * @param imageUrl       博客第一张图片
     * @param id             博客ID
     * @return
     */
    public static String uploadToUp(String originImageUrl, String imageUrl, long id) {
        try {
            String fileName = "wx-share-blog-" + id + ".jpg";
            // imageUrl = ImageUtils.ME.upload(fileName, getImageFromNetByUrl(imageUrl));
        } catch (Exception e) {
            return originImageUrl;
        }
        imageUrl += "!/both/300x300";
        //CacheMgr.set(Blog.CACHE_BLOGS_FIRST_IMAGE,String.valueOf(id),imageUrl);
        return imageUrl;
    }


    /**
     * 根据地址获得数据的字节流
     *
     * @param strUrl 网络连接地址
     * @return
     */
    public static byte[] getImageFromNetByUrl(String strUrl) {
        try {
            URL url = new URL(strUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5 * 1000);
            InputStream inStream = conn.getInputStream();
            byte[] btImg = readInputStream(inStream);
            return btImg;
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * 从输入流中获取数据
     *
     * @param inStream 输入流
     * @return
     * @throws Exception
     */
    public static byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        inStream.close();
        return outStream.toByteArray();
    }
}
