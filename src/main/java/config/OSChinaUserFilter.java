package config;

import beans.User;
import common.db.CacheMgr;
import common.db.DBManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * OSChina 扩展过滤时间数据库连接管理以及用户登录管理
 */
public class OSChinaUserFilter extends OnlyOneFilter {

	private final static Logger log = LoggerFactory.getLogger(OnlyOneFilter.class);

	/**
	 * 获取当前登录用户信息
	 *
	 * @param ctx
	 * @return
	 * @throws IOException
	 */
	@Override
	protected boolean beforeFilter(RequestContext ctx) throws IllegalAccessException, IOException {
		User user = ctx.validUser();
		if (user != null && !user.IsBlocked()) {
			ctx.attr(RequestContext.TOKEN_LOGIN_USER, user);
		}
		return true;
	}

	/**
	 * 请求结束释放数据库连接
	 *
	 * @param ctx
	 * @throws IOException
	 */
	@Override
	protected void finalFilter(RequestContext ctx) throws IllegalAccessException, IOException {
		DBManager.closeConnection();
	}

	@Override
	public void destroy() {
		CacheMgr.close();
		log.warn("Cache Manager closed!!!");
		DBManager.close();
		log.warn("Database Manager closed!!!");
	}

}
