package common.db;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.digester.Digester;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库管理
 *
 */
public class DBManager {

    private final static Logger log = LoggerFactory.getLogger(DBManager.class);

    private final static ConcurrentHashMap<String, ThreadLocal<Connection>> connections = new ConcurrentHashMap<>();
    private final static String SECTION_GLOBAL = "global";
    private static Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    private static boolean show_sql = false;
    private static String defaultdb;

    static {
        initDataSources();
    }

    /**
     * 初始化连接池
     */
    private final static void initDataSources() {
        try (InputStream stream = DBManager.class.getResourceAsStream("/database.ini")) {
            Ini ini = new Ini(stream);
            Class dsName = Class.forName(ini.get(SECTION_GLOBAL, "datasource"));
            show_sql = Boolean.valueOf(ini.get(SECTION_GLOBAL, "show_sql"));
            defaultdb = ini.get(SECTION_GLOBAL, "defaultdb");

            ini.forEach((key, section) -> {
                if (!SECTION_GLOBAL.equalsIgnoreCase(key)) {
                    try {
                        DataSource dsSource = (DataSource) dsName.newInstance();
                        BeanUtils.populate(dsSource, section);
                        dataSources.put(key, dsSource);

                        try (Connection conn = dsSource.getConnection()) {
                            DatabaseMetaData mdm = conn.getMetaData();
                            log.info(String.format("Connected to %s : %s %s (%s)", key, mdm.getDatabaseProductName(), mdm.getDatabaseProductVersion(), mdm.getURL()));

                            connections.put(key, new ThreadLocal<>());
                        }
                    } catch (IllegalAccessException | InstantiationException | InvocationTargetException | SQLException e) {
                        log.error("Failed in initializing data source [" + key + "]", e);
                    }
                }
            });
        } catch (ClassNotFoundException | IOException e) {
            log.error("Failed in initializing data source", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 断开连接池
     */
    public final static void close() {
        dataSources.forEach((name, source) -> {
            try {
                source.getClass().getMethod("close").invoke(source);
            } catch (NoSuchMethodException e) {
            } catch (Exception e) {
                log.error("Failed to destroy DataSource!!! ", e);
            }
        });
    }

    public static Set<String> dataSources() {
        return dataSources.keySet();
    }

    public static PoolStatus getPoolStatus(String dataSourceName) {
        DataSource dataSource = dataSources.get(dataSourceName);
        if (dataSource == null) {
            return null;
        }

        PoolStatus pool = new PoolStatus();
        try {
            if (dataSource.getClass().getName().indexOf("c3p0") > 0) {
                pool.total = (Integer) PropertyUtils.getProperty(dataSource, "numConnectionsDefaultUser");
                pool.busy = (Integer) PropertyUtils.getProperty(dataSource, "numBusyConnectionsDefaultUser");
                pool.idle = (Integer) PropertyUtils.getProperty(dataSource, "numIdleConnectionsDefaultUser");
            } else if (dataSource.getClass().getName().indexOf("bonecp") > 0) {
                pool.total = (Integer) PropertyUtils.getProperty(dataSource, "maxConnectionsPerPartition") * (Integer) PropertyUtils.getProperty(dataSource, "partitionCount");
                //pool.busy = (Integer)PropertyUtils.getProperty(dataSource, "numBusyConnectionsDefaultUser");
                //pool.idle = (Integer)PropertyUtils.getProperty(dataSource, "numIdleConnectionsDefaultUser");
            } else if (dataSource.getClass().getName().indexOf("druid") > 0) {
                pool.total = (Integer) PropertyUtils.getProperty(dataSource, "maxActive");
                pool.busy = (Integer) PropertyUtils.getProperty(dataSource, "activeCount");
                pool.idle = (Integer) PropertyUtils.getProperty(dataSource, "poolingCount");
            }
        } catch (Exception e) {
        }
        return pool;
    }

    public final static Connection getConnection() {
        return getConnection(defaultdb);
    }

    public final static Connection getConnection(String database) {
        try {
            Connection conn = connections.get(database).get();
            if (conn == null || conn.isClosed()) {
                conn = dataSources.get(defaultdb).getConnection();
                if (conn == null) {
                    throw new DBException("Failed to get [" + database + "] connection.");
                }
                connections.get(database).set(conn);
            }
            return (show_sql && !Proxy.isProxyClass(conn.getClass())) ? new _DebugConnection(conn).getConnection() : conn;
        } catch (SQLException e) {
            throw new DBException(e);
        }
    }

    /**
     * 关闭连接
     */
    public final static void closeConnection() {
        connections.forEach((key, conns) -> {
            Connection conn = conns.get();
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                log.error("[" + key + "] Failed to close connection!!! ", e);
            }
            conns.set(null);
        });
    }

    public final static void closeConnection(String database) {
        Connection conn = connections.get(database).get();
        try {
            if (conn != null && !conn.isClosed()) {
                conn.setAutoCommit(true);
                conn.close();
            }
        } catch (SQLException e) {
            log.error("[" + database + "] Failed to close connection!!! ", e);
        }
        connections.get(database).set(null);
    }

    /**
     * 用于跟踪执行的SQL语句
     *
     * @author liudong
     */
    private static class _DebugConnection implements InvocationHandler {

        private Connection conn;

        public _DebugConnection(Connection conn) {
            this.conn = conn;
        }

        /**
         * Returns the conn.
         *
         * @return Connection
         */
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(conn.getClass().getClassLoader(), conn.getClass().getInterfaces(), this);
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            long ct = System.currentTimeMillis();
            try {
                Object obj = m.invoke(conn, args);
                String method = m.getName();
                if ("prepareStatement".equals(method) || "createStatement".equals(method)) {
                    return new _DebugStatement((String) args[0], (Statement) obj).getStatement();
                }
                return obj;
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    /**
     * 用于跟踪 SQL 的执行结果
     */
    private static class _DebugStatement implements InvocationHandler {

        private List<String> methods = Arrays.asList("execute", "executeLargeUpdate", "executeQuery", "executeUpdate");

        private String sql;
        private Statement statement;

        public _DebugStatement(String sql, Statement stat) {
            this.sql = sql;
            this.statement = stat;
        }

        /**
         * 返回 SQL 执行语句实例
         *
         * @return
         */
        public Statement getStatement() {
            return (Statement) Proxy.newProxyInstance(statement.getClass().getClassLoader(), statement.getClass().getInterfaces(), this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long ct = System.currentTimeMillis();
            try {
                return method.invoke(statement, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            } finally {
                String methodName = method.getName();
                if (methods.contains(methodName)) {
                    String logStr = String.format("[SQL -> %dms] >>> %s", (System.currentTimeMillis() - ct), sql);
                    log.info(logStr);
                }
            }
        }
    }

    public static class PoolStatus {
        public int total = -1;    //总连接数
        public int busy = -1;    //活动连接
        public int idle = -1;    //空闲连接

        public int getTotal() {
            return total;
        }

        public int getBusy() {
            return busy;
        }

        public int getIdle() {
            return idle;
        }
    }
}