package common.framework;

import common.db.CacheMgr;
import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.runtime.directive.Directive;
import org.apache.velocity.runtime.parser.node.Node;
import org.apache.velocity.runtime.parser.node.SimpleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity模板上用于控制缓存的指令
 * 该类必须在 velocity.properties 中配置 userdirective=common.framework.CacheDirective
 */
public class CacheDirective extends Directive {

    private final static Logger log = LoggerFactory.getLogger(CacheDirective.class);

    private final static Map<String, String> body_templates = new ConcurrentHashMap<>();

    @Override
    public String getName() { return "cache"; }

    @Override
    public int getType() { return Directive.BLOCK; }

    /* (non-Javadoc)
     * @see Directive#render(InternalContextAdapter, java.io.Writer, Node)
     */
    @Override
    public boolean render(InternalContextAdapter context, Writer writer, Node node) throws IOException {
        //获得缓存信息
        SimpleNode sn_region = (SimpleNode) node.jjtGetChild(0);
        String region = sn_region.value(context).toString();
        SimpleNode sn_key = (SimpleNode) node.jjtGetChild(1);
        String key = sn_key.value(context).toString();

        Node body = node.jjtGetChild(2);
        //检查内容是否有变化
        String tpl_key = key+"@"+region;
        String body_tpl = body.literal();
        String old_body_tpl = body_templates.get(tpl_key);

        if(!StringUtils.equals(body_tpl, old_body_tpl)) {
            body_templates.put(tpl_key, body_tpl);
            CacheMgr.evict(region, key);
        }

        String cache_html = (String)CacheMgr.get(region, key, k -> {
            StringWriter sw = new StringWriter();
            try {
                body.render(context, sw);
            } catch (IOException e) {
                log.error(String.format("Failed to render velocity template (%s,%s).", region, key), e);
            }
            return sw.toString();
        });

        writer.write(cache_html);
        return true;
    }

}
