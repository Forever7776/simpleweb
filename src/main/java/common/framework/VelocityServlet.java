package common.framework;

import com.googlecode.htmlcompressor.compressor.HtmlCompressor;
import config.OnlyOneFilter;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.tools.view.servlet.ServletToolboxManager;
import org.apache.velocity.tools.view.servlet.VelocityLayoutServlet;
import org.mozilla.javascript.EvaluatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * 自定义velocity的错误处理以及压缩内容输出
 */
public final class VelocityServlet extends VelocityLayoutServlet {

    private final static Logger LOG = LoggerFactory.getLogger(VelocityServlet.class);
    private boolean compress = false;
    private boolean output_execute_time = false;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        this.compress = "true".equalsIgnoreCase(config.getInitParameter("compress"));
        this.output_execute_time = "true".equalsIgnoreCase(config.getInitParameter("output-execute-time"));
    }

    @Override
    protected void error(HttpServletRequest req, HttpServletResponse res, Exception excp) throws ServletException {

        Throwable t = excp;
        if (excp instanceof MethodInvocationException) {
            t = ((MethodInvocationException) excp).getWrappedThrowable();
        }

        try {
            if (t instanceof ResourceNotFoundException) {
                //LOG.error(t.getMessage() + "(" + req.getRequestURL().toString() + ")");
                if (!res.isCommitted()) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
            } else {
                StringBuilder log = new StringBuilder("ERROR：Unknown Velocity Error，url=");
                log.append(req.getRequestURL());
                if (req.getQueryString() != null) {
                    log.append('?');
                    log.append(req.getQueryString());
                }
                log.append('(');
                log.append(new Date());
                log.append(')');
                LOG.error(log.toString(), t);
                req.setAttribute("javax.servlet.jsp.jspException", t);
            }
        } catch (IOException e) {
            LOG.error("Exception occured in VelocityServlet.error", e);
            throw new ServletException(e);
        } catch (IllegalStateException e) {
            LOG.error("==============<<IllegalStateException>>==============", e.getCause());
            throw new ServletException(e);
        }
        return;
    }

    @Override
    protected ExtendedProperties loadConfiguration(ServletConfig config) throws IOException {
        String propsFile = this.findInitParameter(config, "org.apache.velocity.properties");
        ExtendedProperties p = new ExtendedProperties();
        InputStream is = null;
        try {
            is = this.getClass().getResourceAsStream(propsFile);
            if (is == null) {
                is = config.getServletContext().getResourceAsStream(propsFile);
            }
            p.load(is);
            LOG.info("Using custom properties at '" + propsFile + "'");
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return p;
    }

    @Override
    protected void initToolbox(ServletConfig config) {
        String file = this.findInitParameter(config, "org.apache.velocity.toolbox");
        if (file != null) {
            //重写 ServletContext 的 getResourceAsStream 方法
            ServletContext context = new ServletContextWrapper(this.getServletContext());
            this.toolboxManager = ServletToolboxManager.getInstance(context, file);
        }
    }

    @Override
    protected void doRequest(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        long beginTime = System.currentTimeMillis();

        if (this.compress && req.getAttribute("disableCompress") == null) {
            BufferedResponse bres = new BufferedResponse(res);
            super.doRequest(req, bres);
            try {
                res.getWriter().print(compressor.compress(bres.html()));
            } catch (EvaluatorException e) {
                res.getWriter().print(bres.html());
            }
        } else {
            super.doRequest(req, res);
        }

        if (res.getStatus() == HttpServletResponse.SC_OK) {
            if (!"false".equalsIgnoreCase((String) req.getAttribute("debug")) && this.output_execute_time) {
                OnlyOneFilter.RequestContext ctx = OnlyOneFilter.RequestContext.get();
                Date cur_time = Calendar.getInstance(req.getLocale()).getTime();
                PrintWriter pw = res.getWriter();
                pw.printf("\r\n<!-- Generated by oschina (init:%s[ms],page:%s[ms],ip:%s) //-->",
                        beginTime - ctx.getBeginTime(),
                        cur_time.getTime() - beginTime,
                        ctx.ip());
            }
        }
        res.flushBuffer();
    }

    static class BufferedResponse extends HttpServletResponseWrapper {

        private StringWriter sout;
        private PrintWriter pout;

        public BufferedResponse(HttpServletResponse res) {
            super(res);
            sout = new StringWriter();
            pout = new PrintWriter(sout);
        }

        @Override
        public PrintWriter getWriter() {
            return pout;
        }

        protected String html() {
            return sout.toString();
        }
    }

    private final static HtmlCompressor compressor = new HtmlCompressor();

    static {
        compressor.setEnabled(true);
        compressor.setRemoveMultiSpaces(true);
        compressor.setRemoveIntertagSpaces(true);
        compressor.setRemoveComments(true);
        compressor.setCompressCss(true);               //compress inline css
        compressor.setCompressJavaScript(true);        //compress inline javascript
		
		/*
		compressor.setEnabled(true);                   //if false all compression is off (default is true)
		compressor.setRemoveComments(true);            //if false keeps HTML comments (default is true)
		compressor.setRemoveMultiSpaces(true);         //if false keeps multiple whitespace characters (default is true)
		compressor.setRemoveIntertagSpaces(true);      //removes iter-tag whitespace characters
		compressor.setRemoveQuotes(true);              //removes unnecessary tag attribute quotes
		compressor.setSimpleDoctype(true);             //simplify existing doctype
		compressor.setRemoveScriptAttributes(true);    //remove optional attributes from script tags
		compressor.setRemoveStyleAttributes(true);     //remove optional attributes from style tags
		compressor.setRemoveLinkAttributes(true);      //remove optional attributes from link tags
		compressor.setRemoveFormAttributes(true);      //remove optional attributes from form tags
		compressor.setRemoveInputAttributes(true);     //remove optional attributes from input tags
		compressor.setSimpleBooleanAttributes(true);   //remove values from boolean tag attributes
		compressor.setRemoveJavaScriptProtocol(true);  //remove "javascript:" from inline event handlers
		compressor.setRemoveHttpProtocol(true);        //replace "http://" with "//" inside tag attributes
		compressor.setRemoveHttpsProtocol(true);       //replace "https://" with "//" inside tag attributes
		compressor.setPreserveLineBreaks(true);        //preserves original line breaks
		compressor.setRemoveSurroundingSpaces("br,p"); //remove spaces around provided tags
		
		compressor.setCompressCss(true);               //compress inline css 
		compressor.setCompressJavaScript(true);        //compress inline javascript
		compressor.setYuiCssLineBreak(80);             //--line-break param for Yahoo YUI Compressor 
		compressor.setYuiJsDisableOptimizations(true); //--disable-optimizations param for Yahoo YUI Compressor 
		compressor.setYuiJsLineBreak(-1);              //--line-break param for Yahoo YUI Compressor 
		compressor.setYuiJsNoMunge(true);              //--nomunge param for Yahoo YUI Compressor 
		compressor.setYuiJsPreserveAllSemiColons(true);//--preserve-semi param for Yahoo YUI Compressor 
		
		//use Google Closure Compiler for javascript compression
		compressor.setJavaScriptCompressor(new ClosureJavaScriptCompressor(CompilationLevel.SIMPLE_OPTIMIZATIONS));
		
		//use your own implementation of css comressor
		compressor.setCssCompressor(new MyOwnCssCompressor());
		*/
    }


}

/**
 * 用于修改配置文件的加载方式和优先级
 */
class ServletContextWrapper implements ServletContext {

    private ServletContext context;

    public ServletContextWrapper(ServletContext context) {
        this.context = context;
    }

    /**
     * 此方法该为类路径加载模式，替换 web 环境的加载方式，优先加载类路径中的配置
     *
     * @param s
     * @return
     */
    @Override
    public InputStream getResourceAsStream(String s) {
        InputStream stream = getClass().getResourceAsStream(s);
        if (stream == null) {
            stream = context.getResourceAsStream(s);
        }
        return stream;
    }

    @Override
    public String getContextPath() {
        return context.getContextPath();
    }

    @Override
    public ServletContext getContext(String s) {
        return context.getContext(s);
    }

    @Override
    public int getMajorVersion() {
        return context.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return context.getMinorVersion();
    }

    @Override
    public int getEffectiveMajorVersion() {
        return context.getEffectiveMajorVersion();
    }

    @Override
    public int getEffectiveMinorVersion() {
        return context.getEffectiveMinorVersion();
    }

    @Override
    public String getMimeType(String s) {
        return context.getMimeType(s);
    }

    @Override
    public Set<String> getResourcePaths(String s) {
        return context.getResourcePaths(s);
    }

    @Override
    public URL getResource(String s) throws MalformedURLException {
        return context.getResource(s);
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        return context.getRequestDispatcher(s);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String s) {
        return context.getNamedDispatcher(s);
    }

    @Override
    public Servlet getServlet(String s) throws ServletException {
        return context.getServlet(s);
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        return context.getServlets();
    }

    @Override
    public Enumeration<String> getServletNames() {
        return context.getServletNames();
    }

    @Override
    public void log(String s) {
        context.log(s);
    }

    @Override
    public void log(Exception e, String s) {
        context.log(e, s);
    }

    @Override
    public void log(String s, Throwable throwable) {
        context.log(s, throwable);
    }

    @Override
    public String getRealPath(String s) {
        return context.getRealPath(s);
    }

    @Override
    public String getServerInfo() {
        return context.getServerInfo();
    }

    @Override
    public String getInitParameter(String s) {
        return context.getInitParameter(s);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return context.getInitParameterNames();
    }

    @Override
    public boolean setInitParameter(String s, String s1) {
        return context.setInitParameter(s, s1);
    }

    @Override
    public Object getAttribute(String s) {
        return context.getAttribute(s);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return context.getAttributeNames();
    }

    @Override
    public void setAttribute(String s, Object o) {
        context.setAttribute(s, o);
    }

    @Override
    public void removeAttribute(String s) {
        context.removeAttribute(s);
    }

    @Override
    public String getServletContextName() {
        return context.getServletContextName();
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String s, String s1) {
        return context.addServlet(s, s1);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String s, Servlet servlet) {
        return context.addServlet(s, servlet);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String s, Class<? extends Servlet> aClass) {
        return context.addServlet(s, aClass);
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String s, String s1) {
        return null;
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> aClass) throws ServletException {
        return context.createServlet(aClass);
    }

    @Override
    public ServletRegistration getServletRegistration(String s) {
        return context.getServletRegistration(s);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return context.getServletRegistrations();
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String s, String s1) {
        return context.addFilter(s, s1);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String s, Filter filter) {
        return context.addFilter(s, filter);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String s, Class<? extends Filter> aClass) {
        return context.addFilter(s, aClass);
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> aClass) throws ServletException {
        return context.createFilter(aClass);
    }

    @Override
    public FilterRegistration getFilterRegistration(String s) {
        return context.getFilterRegistration(s);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return context.getFilterRegistrations();
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return context.getSessionCookieConfig();
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> set) {
        context.setSessionTrackingModes(set);
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return context.getDefaultSessionTrackingModes();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return context.getEffectiveSessionTrackingModes();
    }

    @Override
    public void addListener(String s) {
        context.addListener(s);
    }

    @Override
    public <T extends EventListener> void addListener(T t) {
        context.addListener(t);
    }

    @Override
    public void addListener(Class<? extends EventListener> aClass) {
        context.addListener(aClass);
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> aClass) throws ServletException {
        return context.createListener(aClass);
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return context.getJspConfigDescriptor();
    }

    @Override
    public ClassLoader getClassLoader() {
        return context.getClassLoader();
    }

    @Override
    public void declareRoles(String... strings) {
        context.declareRoles(strings);
    }

    @Override
    public String getVirtualServerName() {
        return context.getVirtualServerName();
    }

    @Override
    public int getSessionTimeout() {
        return 0;
    }

    @Override
    public void setSessionTimeout(int i) {

    }

    @Override
    public String getRequestCharacterEncoding() {
        return null;
    }

    @Override
    public void setRequestCharacterEncoding(String s) {

    }

    @Override
    public String getResponseCharacterEncoding() {
        return null;
    }

    @Override
    public void setResponseCharacterEncoding(String s) {

    }
}