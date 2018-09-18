package config;

import beans.User;
import com.google.gson.Gson;
import com.oreilly.servlet.MultipartRequest;
import common.constant.ApiResult;
import common.framework.FormatTool;
import exception.ActionException;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.*;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 此过滤器负责对编码进行自动处理，以及执行URL映射规则
 * 集中action方法的定义：
 * 1. void xxx()
 * 2. void xxx(RequestContext ctx)
 * 3. void xxx(RequestContext ctx, String...args)
 * 支持任意类型的返回值
 * 1. void - 默认不继续执行模板
 * 2. boolean - 根据返回值来决定是否继续执行模板
 * 3. other - 返回的对象直接输出到response，然后不再执行模板
 */
public class OnlyOneFilter implements Filter {

    private final static Logger log = LoggerFactory.getLogger(OnlyOneFilter.class);

    public final static String REQUEST_URI = "request_uri";    //{/index}
    private final static String VM_EXT = ".vm";
    private final static String VM_INDEX = "/index" + VM_EXT;
    private final static String DEFAULT_CLASS = "Index"; //默认的Action类名
    private final static String DEFAULT_METHOD = "index";//Action默认的方法名

    private ServletContext context;
    private String action_path;
    private String template_path;

    protected List<String> ignoreURIs = new ArrayList<>();
    protected List<String> ignoreExts = new ArrayList<>();


    //请求白名单，这些不需要判断 UA、IP 等，直接放行
    private static List<String> WHITE_LIST = new ArrayList<String>() {

        {
            add("/action/pay/umppay_notify"); // 联动优势的支付回调接口
            add("/action/pay/umppay_refund_notify"); // 联动优势的退款回调接口
            add("/action/pay/umppay_tansfer_notify"); // 联动优势的付款回调接口
            add("/action/SMSFeedback/reply"); // 云片短信回调接口
            add("/remote/service"); // 远程服务
        }
    };

    private final static HashMap<String, Object> INITIAL_ACTIONS = new HashMap<String, Object>();

    /**
     * 日志输出
     *
     * @param msg
     * @param args
     */
    private void debug(String msg, Object... args) {
        try {
            log.debug(String.format(msg, args));
        } catch (Exception e) {
        }
    }

    @Override
    public void init(FilterConfig cfg) throws ServletException {
        this.context = cfg.getServletContext();

        //模板存放路径
        this.action_path = cfg.getInitParameter("action-path");
        this.template_path = cfg.getInitParameter("template-path");

        //某些URL前缀不予处理（例如 /img/***）
        String ignores = cfg.getInitParameter("ignore");
        if (ignores != null) {
            for (String ig : ignores.split(",")) {
                ignoreURIs.add(ig.trim());
            }
        }

        //某些URL扩展名不予处理（例如 *.jpg）
        ignores = cfg.getInitParameter("ignore_exts");
        if (ignores != null) {
            for (String ig : ignores.split(",")) {
                ignoreExts.add('.' + ig.trim());
            }
        }


        //初始化 action 加载
        String initialActions = cfg.getInitParameter("initial_actions");
        if (initialActions != null) {
            for (String action : initialActions.split(",")) {
                try {
                    loadInitActions(action);
                } catch (Exception e) {
                    log.error("Failed to initial action : " + action, e);
                }
            }
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        //自动编码和文件上传处理
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        RequestContext rc = RequestContext.begin(this.context, request, response);
        rc.closeCache();

        try {
            if (!checkAccess(rc)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String req_uri = rc.uri();

            //过滤URL前缀
            for (String ignoreURI : ignoreURIs) {
                if (req_uri.startsWith(ignoreURI)) {
                    chain.doFilter(rc.request(), rc.response());
                    return;
                }
            }
            //过滤URL后缀
            for (String ignoreExt : ignoreExts) {
                if (req_uri.endsWith(ignoreExt)) {
                    chain.doFilter(rc.request(), rc.response());
                    return;
                }
            }

            debug(rc.request.getMethod() + " " + req_uri + "\n");

            rc.attr(REQUEST_URI, req_uri);
            String[] paths = parseUri(req_uri);

            //开始处理请求
            if (beforeFilter(rc)) {
                //加载并执行Action类
                if (callAction(rc, paths)) {
                    //加载并执行网页模板
                    callTemplate(rc, paths);
                }
            }
            //请求正常处理结束
            afterFilter(rc);
        } catch (ActionMethodException e) {
            rc.error(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } catch (IllegalAccessException e) {
            rc.error(HttpServletResponse.SC_FORBIDDEN);
        } finally {
            try {
                finalFilter(rc);
            } catch (IllegalAccessException e) {
                rc.error(HttpServletResponse.SC_FORBIDDEN);
            }
            if (rc != null) {
                rc.end();
                rc = null;
            }
        }
    }

    /**
     * 加载指定的 action 并返回该 action 对象
     *
     * @param actionName
     * @return
     * @throws Exception
     */
    private Object loadInitActions(String actionName) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        Object action = Class.forName(actionName).newInstance();
        Method initMethod = action.getClass().getMethod("init", ServletContext.class);
        if (initMethod != null) {
            initMethod.invoke(action, this.context);
            if (!INITIAL_ACTIONS.containsKey(actionName)) {
                synchronized (INITIAL_ACTIONS) {
                    INITIAL_ACTIONS.put(actionName, action);
                }
            }
            log.debug("!!!!!!!!! " + action.getClass().getSimpleName() + " initialized !!!!!!!!!");
        }
        return action;
    }

    /**
     * 判断 UA 、IP 等是否合法
     *
     * @param ctx
     * @return
     */
    private boolean checkAccess(RequestContext ctx) {

        String requestURI = ctx.request().getRequestURI();
        if (WHITE_LIST.contains(requestURI)) {
            return true; //某些URL设置到白名单列表，不做UA跟IP控制。比如说联动优势的支付回调接口，由于联动优势没有设置UA，会导致该回调被系统拦截，故作白名单处理。
        }

        String ua = ctx.user_agent();
		/*if (!BadUserAgentDao.ME.Check(ua)) {
			return false;
		}*/

        String ip = ctx.ip();
//		if (!BlockIPDAO.ME.canAccess(ip)) {
//			return false;
//		}
        return true;
    }

    private String[] parseUri(String uri) {
        return Stream.of(uri.split("/")).filter(p -> p.length() > 0).toArray(String[]::new);
    }

    /**
     * 请求前的回调方法
     *
     * @param ctx
     * @return 返回 true 表示继续执行下面逻辑，否则终止
     * @throws IOException
     */
    protected boolean beforeFilter(RequestContext ctx) throws IllegalAccessException, IOException {
        return true;
    }//filter执行之前

    protected void afterFilter(RequestContext ctx) throws IllegalAccessException, IOException {
    }//filter执行之后

    protected void finalFilter(RequestContext ctx) throws IllegalAccessException, IOException {
    }//请求执行完毕，用来释放资源

    @Override
    public void destroy() {
        for (Object action : INITIAL_ACTIONS.values()) {
            try {
                Method destoryMethod = action.getClass().getMethod("destroy");
                if (destoryMethod != null) {
                    destoryMethod.invoke(action);
                    log.debug("!!!!!!!!! " + action.getClass().getSimpleName() + " destroy !!!!!!!!!");
                }
            } catch (Exception e) {
                log.error("Unabled to destroy action: " + action.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 调用Action类
     * /user/xxxx/xxxx -> User.xxxx(RequestContext ctx, String[] args)
     *
     * @return 返回调用Action方法的结果
     */
    private boolean callAction(RequestContext ctx, String[] paths) throws ServletException {
        boolean[] results;
        String[] emptyParams = new String[0];
        switch (paths.length) {
            case 0:
                return invokeMethod(ctx, DEFAULT_CLASS, DEFAULT_METHOD, emptyParams)[1];
            case 1:
                results = invokeMethod(ctx, paths[0], DEFAULT_METHOD, emptyParams);
                if (!results[0]) {
                    return invokeMethod(ctx, DEFAULT_CLASS, paths[0], emptyParams)[1];
                }
                return results[1];
            default:
                results = invokeMethod(ctx, paths[0], paths[1], shiftParams(paths, 2));
                if (!results[0]) {
                    String[] sparams = shiftParams(paths, 1);
                    results = invokeMethod(ctx, paths[0], DEFAULT_METHOD, sparams);
                    if (!results[0]) {
                        return invokeMethod(ctx, DEFAULT_CLASS, paths[0], sparams)[1];
                    }
                }
                return results[1];
        }
    }

    /**
     * 调用指定的Action方法
     *
     * @param ctx
     * @param cls
     * @param method
     * @param args
     * @return 返回两个boolean，第一个是是否调用了方法，第二个是action方法的返回值(true -> 继续执行模板，false -> 请求结束，不再执行模板）
     */
    private boolean[] invokeMethod(RequestContext ctx, String cls, String method, String[] args) throws ServletException {

        cls = Character.toUpperCase(cls.charAt(0)) + cls.substring(1) + "Action";

        debug("Trying invoke method [%s.%s]\n", cls, method);

        String cls_name = this.action_path + "." + cls;
        try {
            Object action = Class.forName(cls_name).newInstance();
            //检查方法注解
            Method m = getActionMethod(action.getClass(), method);//.getMethod(method, RequestContext.class, String[].class);
            if (m != null) {
                HttpMethod httpMethod = m.getAnnotation(HttpMethod.class);
                if (httpMethod != null) {
                    switch (httpMethod.value()) {
                        case HttpMethod.POST:
                            if (!"POST".equalsIgnoreCase(ctx.request.getMethod())) {
                                return new boolean[]{false, true};//方法不匹配不执行，而不是抛出异常
                            }
                            break;
                        case HttpMethod.GET:
                            if (!"GET".equalsIgnoreCase(ctx.request.getMethod())) {
                                return new boolean[]{false, true};//方法不匹配不执行，而不是抛出异常
                            }
                            break;
                    }
                }
                Object res;
                switch (m.getParameterCount()) {
                    case 0:
                        res = Modifier.isStatic(m.getModifiers()) ? m.invoke(action.getClass()) : m.invoke(action);
                        break;
                    case 1:
                        res = Modifier.isStatic(m.getModifiers()) ? m.invoke(action.getClass(), ctx) : m.invoke(action, ctx);
                        break;
                    case 2:
                        res = Modifier.isStatic(m.getModifiers()) ? m.invoke(action.getClass(), ctx, args) : m.invoke(action, ctx, args);
                        break;
                    default:
                        throw new IllegalArgumentException(cls_name + "（" + method + ")");//无效的action方法定义
                }
                if (m.getReturnType().equals(boolean.class)) {
                    return new boolean[]{true, (Boolean) res};
                } else if (m.getReturnType().equals(void.class)) {
                    return new boolean[]{true, false};
                } else {
                    ctx.output(res);
                    return new boolean[]{true, false};
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof ActionMethodException) {
                throw (ActionMethodException) t;
            }
            log.error("Failed!!!", t);
            throw new ServletException("invokeMethod(" + cls_name + "." + method + ")", t);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable t) {
            throw new ServletException("invokeMethod(" + cls_name + "." + method + ")", t);
        }
        return new boolean[]{false, true};
    }

    /**
     * 根据方法名返回方法的实例（不允许action方法重名）
     *
     * @param cls
     * @param method
     * @param <T>
     * @return
     */
    private static <T> Method getActionMethod(Class<T> cls, String method) {
        for (Method m : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && m.getName().equals(method)) {
                return m;
            }
        }
        return null;
    }

    /**
     * 对路径数据进行偏移操作
     *
     * @param paths
     * @param shiftLeftCount
     * @return
     */
    private String[] shiftParams(String[] paths, int shiftLeftCount) {
        return Stream.of(paths).skip(shiftLeftCount).toArray(String[]::new);
    }

    /**
     * 调用 URI 对应的模板文件
     *
     * @param ctx
     * @param paths
     * @throws ServletException
     * @throws IOException
     */
    private void callTemplate(RequestContext ctx, String[] paths) throws ServletException, IOException {
        String vm = getTemplate(paths, paths.length);
        debug("callTemplate " + vm);
        //context.getRequestDispatcher(vm).forward(ctx.request(), ctx.response());
        ctx.forward(vm);
    }

    /**
     * 获取请求的目标模板
     *
     * @param paths
     * @param idx_base
     * @return
     */
    private String getTemplate(String[] paths, int idx_base) {
        StringBuilder vm = new StringBuilder(template_path);

        if (idx_base == 0) {
            return vm.toString() + VM_INDEX + makeQueryString(paths, idx_base);
        }

        for (int i = 0; i < idx_base; i++) {
            vm.append('/');
            vm.append(paths[i]);
        }
        String vms = vm.toString();
        String the_path = vms;

        the_path += VM_INDEX;

        if (checkVmExists(the_path)) {
            return the_path + makeQueryString(paths, idx_base);
        }

        vms += VM_EXT;
        if (checkVmExists(vms)) {
            return vms + makeQueryString(paths, idx_base);
        }

        return getTemplate(paths, idx_base - 1);
    }

    private String makeQueryString(String[] paths, int idx_base) {
        StringBuilder params = new StringBuilder();
        int idx = 1;
        for (int i = idx_base; i < paths.length; i++) {
            if (params.length() == 0) {
                params.append('?');
            }
            if (i > idx_base) {
                params.append('&');
            }
            params.append("p");
            params.append(idx++);
            params.append('=');
            params.append(paths[i]);
        }
        return params.toString();
    }

    private final static List<String> vm_cache = new Vector<>();

    /**
     * 判断某个页面是否存在，如果存在则缓存此结果
     *
     * @param path
     * @return
     */
    private boolean checkVmExists(String path) {
        debug("checkVmExists path=%s\n", path);
        if (vm_cache.contains(path)) {
            return true;
        }
        boolean isVM = false;
        if (context != null) {
            String cpath = context.getRealPath(path);
            if (cpath != null) {
                File testFile = new File(cpath);
                isVM = testFile.exists() && testFile.isFile();
                if (isVM) {
                    vm_cache.add(path);
                }
            }
        }
        return isVM;
    }

    /**
     * action 方法的 HTTP METHOD 限制
     */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HttpMethod {
        int ALL = 0; //支持所有的 HTTP Method
        int GET = 1; //只允许通过 GET 方式调用
        int POST = 2; //只允许通过 POST 方式调用

        int value() default ALL;
    }

    /**
     * 用户权限注释
     *
     * @author liudong
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface UserRoleRequired {

        /**
         * 用户的角色
         *
         * @return
         */
        public int role() default User.ROLE_GENERAL;

    }

    /**
     * 输出JSON格式的提示信息
     *
     * @author liudong
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface JSONOutputEnabled {

    }


    private static class ActionMethodException extends RuntimeException {
    }

    /**
     * 请求上下文
     */
    public static class RequestContext {

        public final static String TOKEN_LOGIN_USER = "g_user";
        private final static int MAX_FILE_SIZE = 10 * 1024 * 1024;
        private final static String UTF_8 = "UTF-8";
        private final static String COOKIE_LOGIN = "oscid";
        private final static int MAX_AGE = 86400 * 365;
        private final static byte[] E_KEY = new byte[]{'.', 'O', 'S', 'C', 'H', 'I', 'N', 'A'};
        private final static int COOKIE_LENGTH_START = 5;//一开始cookie中存的字段信息长度
        private final static int COOKIE_LENGTH_END = 8;  //现在扩展的cookie中存的字段信息长度

        public final static int ERROR_CODE_COMMON = 101; //通用错误码
        public final static int ERROR_CODE_CHECK = 401; //校验失败错误码
        public final static int ERROR_CODE_PERMISSION = 501; //权限异常码

        private final static ThreadLocal<RequestContext> contexts = new ThreadLocal<>();
        private final static boolean isTomcat;
        private final static String upload_tmp_path;
        private final static String TEMP_UPLOAD_PATH_ATTR_NAME = "$OOF_TEMP_UPLOAD_PATH$";

        private static String webroot = null;
        private static ServletContext context = null;

        private HttpServletRequest request;
        private HttpServletResponse response;
        private Map<String, Cookie> cookies;
        private String requestURI;
        private long beginTime = 0L;


        private final static Converter dt_converter = new Converter() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            @Override
            @SuppressWarnings("rawtypes")
            public Object convert(Class type, Object value) {
                if (value == null) {
                    return null;
                }
                if (value.getClass().equals(type)) {
                    return value;
                }
                Date d = null;
                try {
                    d = sdf_time.parse(value.toString());
                } catch (ParseException e) {
                    try {
                        d = sdf.parse(value.toString());
                    } catch (ParseException e1) {
                        return null;
                    }
                }
                if (type.equals(Date.class)) {
                    return d;
                }
                if (type.equals(java.sql.Date.class)) {
                    return new java.sql.Date(d.getTime());
                }
                if (type.equals(java.sql.Timestamp.class)) {
                    return new java.sql.Timestamp(d.getTime());
                }
                return null;
            }
        };

        static {
            webroot = getWebrootPath();
            isTomcat = _CheckTomcatVersion();
            //上传的临时目录
            try {
                File tmpFile = File.createTempFile(RequestContext.class.getName(), Long.toString(System.nanoTime()));
                if (!(tmpFile.delete())) {
                    throw new IOException("Could not delete temp file: " + tmpFile.getAbsolutePath());
                }
                if (!(tmpFile.mkdir())) {
                    throw new IOException("Could not create temp directory: " + tmpFile.getAbsolutePath());
                }
                upload_tmp_path = tmpFile.getPath();
            } catch (IOException e) {
                throw new RuntimeException("Could not init RequestContext", e);
            }
            //BeanUtils对时间转换的初始化设置
            ConvertUtils.register(dt_converter, java.sql.Date.class);
            ConvertUtils.register(dt_converter, java.sql.Timestamp.class);
            ConvertUtils.register(dt_converter, Date.class);
        }

        /**
         * 返回接收到该请求的时间
         *
         * @return
         */
        public long getBeginTime() {
            return this.beginTime;
        }

        private final static String getWebrootPath() {
            String root = RequestContext.class.getResource("/").getFile();
            try {
                if (root.endsWith(".svn/")) {
                    root = new File(root).getParentFile().getParentFile().getParentFile().getCanonicalPath();
                } else {
                    root = new File(root).getParentFile().getParentFile().getCanonicalPath();
                }
                root += File.separator;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return root;
        }

        public String user_agent() {
            return header("user-agent");
        }

        public String header(String name) {
            return request.getHeader(name);
        }

        public void header(String name, String value) {
            response.setHeader(name, value);
        }

        public long id() {
            return param("id", 0L);
        }

        /**
         * 初始化请求上下文
         *
         * @param ctx
         * @param req
         * @param res
         */
        public static RequestContext begin(ServletContext ctx, HttpServletRequest req, HttpServletResponse res) {
            RequestContext rc = new RequestContext();
            context = ctx;
            rc.context = ctx;
            rc.request = _AutoUploadRequest(_AutoEncodingRequest(ctx, req));
            rc.response = res;
            rc.response.setCharacterEncoding(UTF_8);
            rc.beginTime = System.currentTimeMillis();
            rc.requestURI = req.getRequestURI();
            if (req.getCookies() != null) {
                rc.cookies = new HashMap<>();
                for (Cookie cookie : req.getCookies()) {
                    rc.cookies.put(cookie.getName(), cookie);
                }
            }

            contexts.set(rc);
            req.setAttribute("req", rc);
            return rc;
        }

        public void output_json(String key, Object value) throws IOException {
            output_json(new String[]{key}, new Object[]{value});
        }

        public void json_msg(String msgkey, Object... args) throws IOException {
            output_json(
                    new String[]{"error", "msg"},
                    new Object[]{0, ResourceUtils.getString("error", msgkey, args)}
            );
        }

        public void output_json(String[] key, Object[] value) throws IOException {
            print(new Gson().toJson(JsonUtils.output_json(key, value)));
        }

        public byte param(String name, byte def_value) {
            return (byte) NumberUtils.toInt(param(name), def_value);
        }

        /**
         * 输出信息到浏览器
         *
         * @param msg
         * @throws IOException
         */
        public void print(Object msg) throws IOException {
            if (!UTF_8.equalsIgnoreCase(response.getCharacterEncoding())) {
                response.setCharacterEncoding(UTF_8);
            }
            response.getWriter().print(msg);
        }

        /**
         * 返回Web应用的路径
         *
         * @return
         */
        public static String root() {
            return webroot;
        }

        /**
         * 获取当前请求的上下文
         *
         * @return
         */
        public static RequestContext get() {
            return contexts.get();
        }

        /**
         * 结束请求
         */
        public void end() {
            String tmpPath = (String) request.getAttribute(TEMP_UPLOAD_PATH_ATTR_NAME);
            if (tmpPath != null) {
                try {
                    FileUtils.forceDelete(new File(tmpPath));
                } catch (IOException excp) {
                }
            }
            //this.context = null;
            //this.request = null;
            //this.response = null;
            contexts.remove();
        }

        public Locale locale() {
            return request.getLocale();
        }

        public boolean isPost() {
            return "POST".equalsIgnoreCase(request.getMethod());
        }

        /**
         * 验证会话信息
         *
         * @return
         */
        public boolean validateSession() {
            return sessionId().equals(param("session"));
        }

        public static String getContextPath() {
            return context.getContextPath();
        }

        /**
         * 读取客户端请求的 body
         *
         * @return
         * @throws IOException
         */
        public String getRequestBody() throws IOException {
            char[] buffer = new char[4096];
            StringWriter sw = new StringWriter();
            Reader reader = request.getReader();
            for (int n; (n = reader.read(buffer)) != -1; ) {
                sw.write(buffer, 0, n);
            }
            return sw.toString();
        }

        /**
         * 自动编码处理
         *
         * @param req
         * @return
         */
        private static HttpServletRequest _AutoEncodingRequest(ServletContext context, HttpServletRequest req) {
            if (req instanceof RequestProxy) {
                return req;
            }
            HttpServletRequest auto_encoding_req = req;
            if ("POST".equalsIgnoreCase(req.getMethod())) {
                try {
                    auto_encoding_req.setCharacterEncoding(UTF_8);
                } catch (UnsupportedEncodingException e) {
                }
            } else if (isTomcat) {
                auto_encoding_req = new RequestProxy(req, UTF_8);
            }
            return auto_encoding_req;
        }

        /**
         * 自动文件上传请求的封装
         *
         * @param req
         * @return
         */
        private static HttpServletRequest _AutoUploadRequest(HttpServletRequest req) {
            if (_IsMultipart(req)) {
                String path = upload_tmp_path + Math.abs(new Random().nextLong());
                File dir = new File(path);
                if (!dir.exists() && !dir.isDirectory()) {
                    dir.mkdirs();
                }
                try {
                    req.setAttribute(TEMP_UPLOAD_PATH_ATTR_NAME, path);
                    return new OOFMultipartRequest(req, dir.getCanonicalPath(), MAX_FILE_SIZE, UTF_8);
                } catch (NullPointerException e) {
                } catch (IOException e) {
                    throw new RuntimeException("Failed to save upload files into temp directory: " + path, e);
                }
            }
            return req;
        }

        public User user() {
            User user = (User) attr(TOKEN_LOGIN_USER);
            if (user == null) {
                long id = getUserIdFromCookie();
                user = User.ME.get(id);
            }
            return user;
        }

        public User user(User user) {
            attr(TOKEN_LOGIN_USER, user);
            return user;
        }

        /**
         * 从上下问获取用户，并检查用户权限（根据 cookie 判断权限）
         *
         * @return
         */
        public User validUser() {
            User user = (User) attr(TOKEN_LOGIN_USER);
            if (user == null) {
                user = getUserFromCookie();
            }
            return user;
        }

        public String ip() {
            String ip = getRemoteAddr(request);
            if (ip == null) {
                ip = "127.0.0.1";
            }
            return ip;
        }

        /**
         * 获取客户端IP地址，此方法用在proxy环境中
         *
         * @param req
         * @return
         */
        private static String getRemoteAddr(HttpServletRequest req) {
            String ip = req.getHeader("X-Forwarded-For");
            if (ip != null && ip.trim().length() > 0) {
                String[] ips = ip.split(",");
                if (ips != null) {
                    for (String tmpip : ips) {
                        if (tmpip == null || tmpip.trim().length() == 0) {
                            continue;
                        }
                        tmpip = tmpip.trim();
                        if (isIPAddr(tmpip) && !tmpip.startsWith("10.") && !tmpip.startsWith("192.168.") && !tmpip.startsWith("10.") && !"127.0.0.1".equals(tmpip)) {
                            return tmpip.trim();
                        }
                    }
                }
            }
            ip = req.getHeader("x-real-ip");
            if (ip != null && isIPAddr(ip)) {
                return ip;
            }
            ip = req.getRemoteAddr();
            if (ip.indexOf('.') == -1) {
                ip = "127.0.0.1";
            }
            return ip;
        }

        /**
         * 判断是否为搜索引擎
         *
         * @return
         */
        public boolean isRobot() {
            String ua = request.getHeader("user-agent");
            if (ua == null || ua.trim().isEmpty()) return false;
            ua = ua.toLowerCase();
            return (ua != null && (
                    ua.indexOf("baiduspider") != -1
                            || ua.indexOf("googlebot") != -1
                            || ua.indexOf("sogou") != -1
                            || ua.indexOf("sina") != -1
                            || ua.indexOf("iaskspider") != -1
                            || ua.indexOf("ia_archiver") != -1
                            || ua.indexOf("sosospider") != -1
                            || ua.indexOf("youdaobot") != -1
                            || ua.indexOf("yahoo") != -1
                            || ua.indexOf("yodao") != -1
                            || ua.indexOf("msnbot") != -1
                            || ua.indexOf("twiceler") != -1
                            || ua.indexOf("sosoimagespider") != -1
                            || ua.indexOf("naver.com/robots") != -1
                            || ua.indexOf("nutch") != -1
                            || ua.indexOf("bingbot") != -1
                            || ua.indexOf("spider") != -1
                            || ua.indexOf("360spider") != -1
                            || ua.indexOf("haosouspider") != -1
            ));
        }

        public void not_found() throws IOException {
            error(HttpServletResponse.SC_NOT_FOUND);
        }

        public void forbidden() throws IOException {
            error(HttpServletResponse.SC_FORBIDDEN);
        }

        /**
         * 判断字符串是否是一个IP地址
         *
         * @param addr
         * @return
         */
        private static boolean isIPAddr(String addr) {
            String regexp = "^(\\d{1,2}|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|1\\d\\d|2[0-4]\\d|25[0-5])\\.(\\d{1,2}|1\\d\\d|2[0-4]\\d|25[0-5])$";
            Pattern pattern = Pattern.compile(regexp);
            return pattern.matcher(addr).matches();
        }

        /**
         * 将HTTP请求参数映射到bean对象中
         *
         * @param beanClass
         * @return
         * @throws Exception
         */
        public <T> T form(Class<T> beanClass) {
            try {
                T bean = beanClass.newInstance();
                BeanUtils.populate(bean, request.getParameterMap());
                return bean;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        public String param(String name) {
            return request.getParameter(name);
        }

        public String param(String name, String def_value) {
            String v = request.getParameter(name);
            if (v == null) {
                v = def_value;
            }
            return v;
        }

        public int param(String name, int def_value) {
            return (int) param(name, (long) def_value);
        }

        public long param(String name, long def_value) {
            try {
                return Long.parseLong(request.getParameter(name));
            } catch (Exception e) {
            }
            return def_value;
        }

        public String[] params(String name) {
            return request.getParameterValues(name);
        }

        public int[] lparams(String name) {
            String[] values = params(name);
            if (values == null) {
                return null;
            }
            List<Integer> lvs = new ArrayList<>();
            for (String v : values) {
                try {
                    int lv = Integer.parseInt(v);
                    if (!lvs.contains(lv)) {
                        lvs.add(lv);
                    }
                } catch (Exception e) {
                }
            }
            int[] llvs = new int[lvs.size()];
            for (int i = 0; i < lvs.size(); i++) {
                llvs[i] = lvs.get(i);
            }
            return llvs;
        }

        public long[] Lparams(String name) {
            String[] values = params(name);
            if (values == null) {
                return null;
            }
            List<Long> lvs = new ArrayList<Long>();
            for (String v : values) {
                long lv = NumberUtils.toLong(v, Long.MIN_VALUE);
                if (lv != Long.MIN_VALUE && !lvs.contains(lv)) {
                    lvs.add(lv);
                }
            }
            long[] llvs = new long[lvs.size()];
            for (int i = 0; i < lvs.size(); i++) {
                llvs[i] = lvs.get(i);
            }
            return llvs;
        }

        public void dumpRequestParams(PrintStream out) {
            request.getParameterMap().forEach((key, value) -> {
                out.printf("%s=", key);
                out.println(String.join(",", (String[]) value));
            });
        }

        public void dumpRequestHeaders(PrintStream out) {
            Collections.list(request.getHeaderNames()).forEach(hn -> out.printf("%s=%s\n", hn, request.getHeader(hn)));
        }

        public String uri() {
            return this.requestURI;
        }

        public void redirect(String uri) throws IOException {
            response.sendRedirect(uri);
        }

        public void forward(String uri) throws ServletException, IOException {
            context.getRequestDispatcher(uri).forward(request, response);
        }

        public void include(String uri) throws ServletException, IOException {
            context.getRequestDispatcher(uri).include(request, response);
        }

        public File file(String fieldName) {
            if (request instanceof OOFMultipartRequest) {
                return ((OOFMultipartRequest) request).getFile(fieldName);
            }
            return null;
        }

        public byte[] image(File file) throws IOException {
            InputStream input = new FileInputStream(file);
            byte[] byt = new byte[input.available()];
            input.read(byt);
            input.close();
            return byt;
        }

        public File image(String fieldName) throws IOException {
            if (request instanceof OOFMultipartRequest) {
                return ((OOFMultipartRequest) request).getFile(fieldName);
            }
            return null;
        }

        public Object attr(String name) {
            return request.getAttribute(name);
        }

        public void attr(String name, Object value) {
            request.setAttribute(name, value);
        }

        public Object session(String name) {
            return request.getSession().getAttribute(name);
        }

        public void session(String name, Object obj) {
            if (obj == null) {
                request.getSession().removeAttribute(name);
            } else {
                request.getSession().setAttribute(name, obj);
            }
        }

        public String sessionId() {
            return request.getSession().getId();
        }

        public Cookie cookie(String name) {
            return cookies.get(name);
        }

        public void cookie(String name, String value, int max_age, boolean all_sub_domain) {
            Cookie cookie = new Cookie(name, value);
            cookie.setMaxAge(max_age);
            if (all_sub_domain) {
                String serverName = request.getServerName();
                String domain = RequestUtils.getDomainOfServerName(serverName);
                if (domain != null && domain.indexOf('.') != -1) {
                    cookie.setDomain('.' + domain);
                }
            }
            cookie.setPath("/");
            Boolean notHttpOnly = (Boolean) request.getAttribute(RequestUtils.NOT_USE_HTTP_ONLY_COOKIE);
            if (notHttpOnly == null || !notHttpOnly)
                cookie.setHttpOnly(true);
            response.addCookie(cookie);
        }

        public void cookie_new(String name, String value, int max_age, boolean all_sub_domain) {
            Cookie cookie = new Cookie(name, value);
            cookie.setMaxAge(max_age);
            if (all_sub_domain) {
                String serverName = request.getServerName();
                String domain = RequestUtils.getDomainOfServerName(serverName);
                if (domain != null && domain.indexOf('.') != -1) {
                    cookie.setDomain('.' + domain);
                }
               /* if (!(boolean) LinkTool.getEvent("dev")) {
                    cookie.setDomain(LinkTool.getHost("domain"));
                }*/

            }
            cookie.setPath("/");
            Boolean notHttpOnly = (Boolean) request.getAttribute(RequestUtils.NOT_USE_HTTP_ONLY_COOKIE);
            if (notHttpOnly == null || !notHttpOnly) {
                cookie.setHttpOnly(true);
            }
            response.addCookie(cookie);
        }

        public Cookie deleteCookieNew(String name, boolean all_sub_domain) {
            Cookie cookie = cookie(name);
            cookie_new(name, null, 0, all_sub_domain);
            return cookie;
        }

        public Cookie deleteCookie(String name, boolean all_sub_domain) {
            Cookie cookie = cookie(name);
            cookie(name, "", 0, all_sub_domain);
            return cookie;
        }

        public void error(int errorCode, String... msgs) throws IOException {
            String msg = (msgs != null && msgs.length > 0) ? msgs[0] : null;
            response.sendError(errorCode, msg);
        }

        public ActionException fromResource(String bundle, String key, Object... args) {
            String res = ResourceUtils.getStringForLocale(request.getLocale(), bundle, key, args);
            return new ActionException(res);
        }

        public ActionException error(String key, Object... args) {
            return fromResource("error", key, args);
        }

        public ApiResult error(String key, String field, Object... args) {
            String res = ResourceUtils.getStringForLocale(request.getLocale(), "error", key, args);
            return ApiResult.failWithMessageAndObject(res == null || res.equals("") ? key : res, field);
        }

        public ApiResult successResult(String key, Object... args) {
            String res = ResourceUtils.getStringForLocale(request.getLocale(), "error", key, args);
            return ApiResult.success(StringUtils.isBlank(res) ? key : res);
        }

        /**
         * 输出信息到浏览器
         *
         * @param msg
         * @throws IOException
         */
        public void output(Object msg) throws IOException {
            if (msg instanceof ApiResult) {
                outputJson(((ApiResult) msg).json());
            } else {
                if (response.getContentType() == null) {
                    response.setContentType("text/plain; charset=utf-8");
                }
                response.getWriter().print(msg);
            }
        }

        public void outputJson(String json) throws IOException {
            if (response.getContentType() == null) {
                response.setContentType("application/json; charset=utf-8");
            }
            output(json);
        }

        public ServletContext context() {
            return context;
        }

        public HttpServletRequest request() {
            return request;
        }

        public HttpServletResponse response() {
            return response;
        }

        /**
         * 设置public缓存，设置了此类型缓存要求此页面对任何人访问都是同样数据
         *
         * @param minutes 分钟
         * @return
         */
        public void setPublicCache(int minutes) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                int seconds = minutes * 60;
                response.setHeader("Cache-Control", "max-age=" + seconds);
                Calendar cal = Calendar.getInstance(request.getLocale());
                cal.add(Calendar.MINUTE, minutes);
                response.setDateHeader("Expires", cal.getTimeInMillis());
            }
        }

        /**
         * 设置私有缓存
         *
         * @param minutes
         * @return
         */
        public void setPrivateCache(int minutes) {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Cache-Control", "private");
                Calendar cal = Calendar.getInstance(request.getLocale());
                cal.add(Calendar.MINUTE, minutes);
                response.setDateHeader("Expires", cal.getTimeInMillis());
            }
        }

        /**
         * 关闭缓存
         */
        public void closeCache() {
            response.setHeader("Pragma", "must-revalidate, no-cache, private");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Expires", "Sun, 1 Jan 2000 01:00:00 GMT");
        }

        /**
         * 非 Tomcat 服务器无需对URL参数进行转码
         *
         * @return
         */
        private final static boolean _CheckTomcatVersion() {
            try {
                Class.forName("org.apache.catalina.startup.Tomcat");
                return true;
            } catch (Throwable t) {
            }
            return false;
        }

        /**
         * 自动解码
         *
         * @author liudong
         */
        private static class RequestProxy extends HttpServletRequestWrapper {
            private String uri_encoding;

            RequestProxy(HttpServletRequest request, String encoding) {
                super(request);
                this.uri_encoding = encoding;
            }

            /**
             * 重载getParameter
             */
            @Override
            public String getParameter(String paramName) {
                String value = super.getParameter(paramName);
                return _DecodeParamValue(value);
            }

            /**
             * 重载getParameterMap
             */
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Map getParameterMap() {
                Map params = super.getParameterMap();
                HashMap<String, Object> new_params = new HashMap<>();
                params.forEach((key, oValue) -> {
                    if (oValue.getClass().isArray()) {
                        String[] values = (String[]) params.get(key);
                        String[] new_values = new String[values.length];
                        for (int i = 0; i < values.length; i++) {
                            new_values[i] = _DecodeParamValue(values[i]);
                        }

                        new_params.put((String) key, new_values);
                    } else {
                        String value = (String) params.get(key);
                        String new_value = _DecodeParamValue(value);
                        if (new_value != null) {
                            new_params.put((String) key, new_value);
                        }
                    }
                });
                return new_params;
            }

            /**
             * 重载getParameterValues
             */
            @Override
            public String[] getParameterValues(String arg0) {
                String[] values = super.getParameterValues(arg0);
                for (int i = 0; values != null && i < values.length; i++) {
                    values[i] = _DecodeParamValue(values[i]);
                }
                return values;
            }

            /**
             * 参数转码
             *
             * @param value
             * @return
             */
            private String _DecodeParamValue(String value) {
                if (StringUtils.isBlank(value) || StringUtils.isBlank(uri_encoding)
                        || StringUtils.isNumeric(value)) {
                    return value;
                }
                try {
                    if (getEncoding(value).equals("GB2312")) {
                        return value;
                    }
                    if (getEncoding(value).equals("ISO-8859-1")) {
                        return new String(value.getBytes("8859_1"), uri_encoding);
                    }
                    if (getEncoding(value).equals("UTF-8")) {
                        return value;
                    }
                    if (getEncoding(value).equals("GBK")) {
                        return value;
                    }
                    return new String(value.getBytes("8859_1"), uri_encoding);

                } catch (Exception e) {
                }
                return value;
            }

            public String getEncoding(String str) {
                String encode = "GB2312";
                try {
                    if (str.equals(new String(str.getBytes(encode), encode))) {
                        String s = encode;
                        return s;
                    }
                } catch (Exception exception) {
                }
                encode = "ISO-8859-1";
                try {
                    if (str.equals(new String(str.getBytes(encode), encode))) {
                        String s1 = encode;
                        return s1;
                    }
                } catch (Exception exception1) {
                }
                encode = "UTF-8";
                try {
                    if (str.equals(new String(str.getBytes(encode), encode))) {
                        String s2 = encode;
                        return s2;
                    }
                } catch (Exception exception2) {
                }
                encode = "GBK";
                try {
                    if (str.equals(new String(str.getBytes(encode), encode))) {
                        String s3 = encode;
                        return s3;
                    }
                } catch (Exception exception3) {
                }
                return "";
            }

        }

        private static boolean _IsMultipart(HttpServletRequest req) {
            String rct = req.getContentType();
            return ((rct != null) && (rct.toLowerCase().startsWith("multipart")));
        }

        /**
         * 从cookie中读取保存的用户id信息
         *
         * @return
         */
        private long getUserIdFromCookie() {
            try {
                Cookie cookie = cookie(COOKIE_LOGIN);
                if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
                    String uuid = cookie.getValue();
                    String ck = decrypt(uuid, E_KEY);
                    final String[] items = StringUtils.split(ck, '|');
                    if (items != null && items.length >= COOKIE_LENGTH_START && items.length <= COOKIE_LENGTH_END) {
                        return NumberUtils.toLong(items[0], -1L);
                    }
                }
            } catch (Exception e) {
            }
            return -1L;
        }

        /**
         * 从 cookie 中获取用户信息（用于判断用户是否登录、权限验证）
         *
         * @return
         */
        private User getUserFromCookie() {
            User user = null;
            try {
                Cookie cookie = cookie(COOKIE_LOGIN);
                if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
                    String uuid = cookie.getValue();
                    String ck = decrypt(uuid, E_KEY);
                    final String[] items = StringUtils.split(ck, '|');
                    if (items != null && items.length >= COOKIE_LENGTH_START && items.length <= COOKIE_LENGTH_END) {
                        Long id = NumberUtils.toLong(items[0], -1L);
                        String pwd = items[1];
                        User foundUser = User.ME.get(id);
                        if (foundUser != null && StringUtils.equalsIgnoreCase(foundUser.getPwd(), pwd) && !foundUser.IsBlocked()) {
                            //TODO: post login
                            return foundUser;
                        }
                    }
                }
            } catch (Exception e) {
            }
            return user;
        }

        /**
         * 在 Cookie 中保存登录用户信息
         *
         * @param user
         */
        public void saveUserInCookie(User user) {
            String new_value = genLoginKey(user, ip(), request().getHeader("user-agent"));
            cookie(COOKIE_LOGIN, new_value, MAX_AGE, true);
        }

        /**
         * 删除 Cookie 中的登录信息
         */
        public void deleteUserInCookie() {
            deleteCookie(COOKIE_LOGIN, true);
        }

        /**
         * 删除 Cookie 中的登录信息
         */
        public void deleteUserInCookieNew() {
            deleteCookieNew(COOKIE_LOGIN, true);
        }


        /**
         * 生成用户登录标识字符串
         * 修改这里的拼接规则时，请注意同步修改 userFromUUID 这个方法中的解密规则
         *
         * @param user
         * @param ip
         * @param user_agent
         * @return
         */
        private static String genLoginKey(User user, String ip, String user_agent) {
            user_agent = clearUserAgent(user_agent);
            StringBuilder sb = new StringBuilder();
            sb.append(user.getId());
            sb.append('|');
            sb.append(user.getPwd());
            sb.append('|');
            sb.append(ip);
            sb.append('|');
            sb.append(user_agent.hashCode());
            sb.append('|');
            sb.append(System.currentTimeMillis());
            sb.append('|');
            if (StringUtils.isNotBlank(user.getEmail()) && FormatTool.is_email(user.getEmail())) {
                sb.append(user.getEmail());
            }
            sb.append('|');
            sb.append(user.getName());
            sb.append('|');
            sb.append(user.getIdent());
            return encrypt(sb.toString(), E_KEY);
        }

        /**
         * 解密
         *
         * @param value
         * @return
         * @throws Exception
         */
        private static String decrypt(String value, byte[] key) {
            try {
                value = URLDecoder.decode(value, UTF_8);
                if (StringUtils.isBlank(value)) {
                    return null;
                }
                byte[] data = Base64.getDecoder().decode(value.getBytes());
                return new String(CryptUtils.decrypt(data, key));
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * 加密
         *
         * @param value
         * @return
         * @throws Exception
         */
        private static String encrypt(String value, byte[] key) {
            byte[] data = CryptUtils.encrypt(value.getBytes(), key);
            try {
                return URLEncoder.encode(new String(Base64.getEncoder().encode(data)), UTF_8);
            } catch (Exception e) {
                return null;
            }
        }

        private static String clearUserAgent(String ua) {
            if (StringUtils.isBlank(ua)) {
                return "";
            }
            int idx = StringUtils.indexOf(ua, "staticlogin");
            return (idx > 0) ? StringUtils.substring(ua, 0, idx) : ua;
        }

        /**
         * 是否属于PJAX请求
         *
         * @return
         */
        public boolean isPJAXRequest() {
            Enumeration<String> headers = request.getHeaders("X-PJAX");
            if (null != headers && headers.hasMoreElements() && StringUtils.equals(headers.nextElement(), "true")) {
                return true;
            }
            return false;
        }

    }

    /**
     * 文件上传处理
     */
    private static class OOFMultipartRequest extends HttpServletRequestWrapper {

        private MultipartRequest multipartRequest;
        private Map<String, String> paramMap = new HashMap<>();
        private Map<String, File> files;//保存上传的文件

        public OOFMultipartRequest(HttpServletRequest req, String upload_tmp_path, int MAX_FILE_SIZE, String enc) throws IOException {
            super(req);
            this.multipartRequest = new MultipartRequest(req, upload_tmp_path, MAX_FILE_SIZE, enc);
        }

        public String getContentType(String name) {
            String ret = "";
            if (multipartRequest != null) {
                ret = multipartRequest.getContentType(name);
            }
            return ret;
        }

        public File getFile(String name) {
            if (multipartRequest != null) {
                return multipartRequest.getFile(name);
            } else if (files != null) {
                return files.get(name);
            }
            return null;
        }

        public Enumeration<String> getFileNames() {
            return multipartRequest.getFileNames();
        }

        public String getFilesystemName(String name) {
            return multipartRequest.getFilesystemName(name);
        }

        public String getOriginalFileName(String name) {
            return multipartRequest.getOriginalFileName(name);
        }

        @Override
        public String getParameter(String name) {
            String v = super.getParameter(name);
            if (v == null) {
                if (multipartRequest != null) {
                    v = multipartRequest.getParameter(name);
                } else if (paramMap != null) {
                    v = paramMap.get(name);
                }
            }
            return v;
        }

        @Override
        public Enumeration<String> getParameterNames() {

            return multipartRequest.getParameterNames();
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] v = super.getParameterValues(name);
            if (v == null) {
                v = multipartRequest.getParameterValues(name);
            }
            return v;
        }

        @Override
        public Map getParameterMap() {
            if (multipartRequest == null)
                return paramMap;
            Map<String, Object> map = new HashMap<>();
            Enumeration<String> names = getParameterNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                String[] values = getParameterValues(name);
                map.put(name, (values != null && values.length == 1) ? values[0] : values);
            }
            return map;
        }
    }

}
