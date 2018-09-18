package config;


import beans.User;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.StringUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 全局过滤器
 *
 */
public class HomeFilter extends OSChinaUserFilter {

    private final static Logger log = LoggerFactory.getLogger(HomeFilter.class);

    private static final int MAX_ERROR_COUNT = 100;    //每小时最多允许的错误数

    final static ConcurrentHashMap<Integer, ConcurrentHashMap<String, Integer>> error_ips = new ConcurrentHashMap<Integer, ConcurrentHashMap<String, Integer>>();

    @Override
    protected boolean beforeFilter(RequestContext ctx) throws IllegalAccessException, IOException {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        ConcurrentHashMap<String, Integer> ips = error_ips.get(hour);
        if (ips != null) {
            String ip = ctx.ip();
            Integer err_count = ips.get(ip);
            if (err_count != null && err_count >= MAX_ERROR_COUNT) {
                throw new IllegalAccessException();
            }
        }
//		Thread ct = Thread.currentThread();
//		ct.setName("HTTP Request From : " + ctx.uri() + "(" + ctx.ip() + ")");
        return super.beforeFilter(ctx);
    }

    @Override
    protected void afterFilter(RequestContext ctx) throws IllegalAccessException, IOException {
        int status = ctx.response().getStatus();
        if (status == HttpServletResponse.SC_NOT_FOUND && !ctx.isRobot()) {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            ConcurrentHashMap<String, Integer> ips = error_ips.get(hour);
            if (ips == null) {
                if (error_ips.size() > 0) {
                    error_ips.clear();
                }
                ips = new ConcurrentHashMap<String, Integer>();
            }
            String ip = ctx.ip();
            Integer err_count = ips.get(ip);
            err_count = (err_count == null) ? 1 : err_count + 1;
            ips.put(ip, err_count);
            error_ips.put(hour, ips);
        }
        if (ctx.param("CLEAN_ERROR_IP", 0) == 1) {
            User user = ctx.user();
            if (user != null && user.getRole() == User.ROLE_SYSTEM_MANAGER) {
                error_ips.clear();
            }
        }
        super.afterFilter(ctx);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;


        String req_uri = request.getRequestURI();
        getRequestURL(request);
        //过滤URL前缀
        for (String ignoreURI : super.ignoreURIs) {
            if (req_uri.startsWith(ignoreURI)) {
                chain.doFilter(req, res);
                return;
            }
        }
        //过滤URL后缀
        for (String ignoreExt : super.ignoreExts) {
            if (req_uri.endsWith(ignoreExt)) {
                chain.doFilter(req, res);
                return;
            }
        }

        //重定向 带有个性域名的url
        String[] uris;
        User user = null;
        uris = Stream.of(request.getRequestURI().split("/")).filter(p -> p.length() > 0).toArray(String[]::new);

        if (uris.length >= 1) {
            if (uris.length > 1 && uris[0].equals("u")) {
                try {
                    user = User.ME.get(new Long(uris[1]));
                    if (user == null || user.getStatus() != 1) {
                        response.sendError(404);
                        return;
                    } else {
                        if (StringUtils.isNotBlank(user.getIdent())) {
                            String[] buildurl;
                            buildurl = ArrayUtils.remove(uris, 0);
                            buildurl = ArrayUtils.remove(buildurl, 0);
                            ArrayUtils.reverse(buildurl);
                            buildurl = ArrayUtils.add(buildurl, user.getIdent());
                            ArrayUtils.reverse(buildurl);
                            response.sendRedirect("/" + Arrays.stream(buildurl).collect(Collectors.joining("/")));
                            return;
                        }
                    }
                } catch (Exception err) {
                    log.error(err.getMessage());
                    response.sendError(500);
                    return;
                }
            } else {
                try {
                    // user = UserDAO.ME.getByIdent(uris[0]);
                    if (user == null || user.getStatus() != 1) {
                        response.sendError(404);
                        return;
                    }
                } catch (IOException e) {
                    log.error(e.getMessage());
                    response.sendError(500);
                    return;
                }
            }
        }

        super.doFilter(new HomeRequest(request), response, chain);
    }

    public String getContextURL(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        StringBuffer out = new StringBuffer();
        out.append(request.getScheme());
        out.append("://");
        out.append(request.getServerName());
        if (scheme.equals("http") && port != 80 || scheme.equals("https") && port != 443) {
            out.append(':');
            out.append(port);
        }

        out.append(request.getContextPath());
        return out.toString();
    }

    /**
     * 获取页面完整路径，包含参数
     *
     * @param request
     */
    public void getRequestURL(HttpServletRequest request) {
        String requestURI = (String) request.getRequestURI();
        String requestQueryString = (String) request.getQueryString();
        String url = getContextURL(request) + requestURI + (StringUtils.isNotBlank(requestQueryString) ? "?" + requestQueryString : "");
        request.setAttribute("requestURL", url);
    }

    /**
     * 个人空间 URL 的第一位是个性地址，该封装类就是为了处理这个个性地址
     */
    private static class HomeRequest extends HttpServletRequestWrapper {
        private String[] uris;
        private final static Logger log = LoggerFactory.getLogger(HomeRequest.class);
        private User user;

        public HomeRequest(HttpServletRequest request) {
            super(request);

            try {
                this.uris = Stream.of(request.getRequestURI().split("/")).filter(p -> p.length() > 0).toArray(String[]::new);
            } catch (Exception e) {
                log.error(e.getMessage());
            }

            if (uris.length >= 1) {
                if (uris.length > 1 && uris[0].equals("u")) {
                    try {
                        user = User.ME.get(new Long(uris[1]));
                        if (user != null) {
                            request.setAttribute("currentSpaceUser", user);
                        }
                    } catch (Exception err) {
                        log.error(err.getMessage());
                    }
                } else {
                    // user = UserDAO.ME.getByIdent(uris[0]);
                    if (user != null) {
                        request.setAttribute("currentSpaceUser", user);
                    }
                }
            }
        }

        @Override
        public String getRequestURI() {
            if (uris.length == 0) {
                return "/";
            }

            //ie 浏览器提示
            if ("ie_compatibility".equals(uris[0])) {
                return "/ie_compatibility";
            }

            if ("u".equals(uris[0])) {
                try {
                    return Arrays.stream(uris).skip(2).collect(Collectors.joining("/"));
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
                return null;
            } else {
                try {
                    return Arrays.stream(uris).skip(1).collect(Collectors.joining("/"));
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
                return null;
            }
        }
    }


}
