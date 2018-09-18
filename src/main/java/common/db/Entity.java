package common.db;

import common.framework.Inflector;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.StringUtils;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 数据库对象的基类
 *
 * @author winterlau
 */
public abstract class Entity implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(Entity.class);

    private static final int CACHE_OBJ = 1000;//缓存的数量

    public final static transient byte CONTENT_MARKDOWN = 0x01; // Markdown
    public final static transient byte CONTENT_UEDITOR = 0x02; // UEDITOR
    public final static transient byte CONTENT_THINKERMD = 0x03; // Thinker－md
    public final static transient byte CONTENT_CKEDITOR = 0x04; // CKEDITOR

    public final static transient byte DEFAULT_CONTENT_MARKDOWN = CONTENT_THINKERMD;//默认的markdown编辑器
    public final static transient byte DEFAULT_CONTENT_RICHTXT = CONTENT_CKEDITOR;//默认的富文本编辑器

    /**
     * 冻结：-1 正常:0 封号：1  手动注销：2  系统销毁：3
     */
    public final static transient int STATUS_FROZEN = -1;
    public final static transient int STATUS_NORMAL = 0;
    public final static transient int STATUS_CLOSE = 1;
    public final static transient int STATUS_CANCEL_ME = 2;
    public final static transient int STATUS_CANCEL_SYSTEM = 3;

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Cache {
        String region();

        boolean cacheNull() default true;
    }

    protected int getView_count() {
        return 0;
    }

    protected void setView_count(int view_count) {
    }

    /**
     * 对象的类型
     *
     * @return
     */
    public byte obj_type() {
        return 0;
    }

    public static void setCache(String cache, String key, Serializable value) {
        CacheMgr.set(cache, key, value);
    }

    protected final static transient String OBJ_COUNT_CACHE_KEY = "#";
    private long ___key_id;

    public long getId() {
        return ___key_id;
    }

    public void setId(long id) {
        this.___key_id = id;
    }

    /**
     * 返回对象对应的缓存区域名
     *
     * @return
     */
    public String CacheRegion() {
        return this.getClass().getSimpleName();
    }

    /**
     * 分页列出所有对象
     *
     * @param page
     * @param size
     * @return
     */
    public List<? extends Entity> list(int page, int size) {
        String sql = "SELECT * FROM " + rawTableName() + " ORDER BY id DESC";
        return DBQuery.get(databaseName()).query_slice(getClass(), sql, page, size);
    }

    /**
     * 查询所有对象
     *
     * @return
     */
    public List<? extends Entity> list() {
        String sql = "SELECT * FROM " + rawTableName();
        return DBQuery.get(databaseName()).query_cache(getClass(), cacheNullObject(), cacheRegion(), "all", sql);
    }

    public List<? extends Entity> filter(String filter, int page, int size) {
        String sql = "SELECT * FROM " + rawTableName() + " WHERE " + filter + " ORDER BY id DESC";
        return DBQuery.get(databaseName()).query_slice(getClass(), sql, page, size);
    }

    public List<? extends Entity> listOfSpace(long space) {
        String sql = "SELECT * FROM " + tableName() + " WHERE space=? ORDER BY sort_order";
        return DBQuery.get(databaseName()).query_cache(getClass(), false, CacheRegion(), "LIST#" + space, sql, space);
    }

    public static Object getCache(String cache, Serializable key) {
        return CacheMgr.get(cache, key + "");
    }

    /**
     * 统计此对象的总记录数
     *
     * @return
     */
    public int totalCount(String filter) {
        return DBQuery.get(databaseName()).stat("SELECT COUNT(*) FROM " + rawTableName() + " WHERE " + filter);
    }

    public static void evictCache(String cache, String key) {
        CacheMgr.evict(cache, key);
    }


    /**
     * 返回默认的对象对应的表名
     *
     * @return
     */
    public final String rawTableName() {
        String schemaName = schemaName();
        return (schemaName != null) ? "\"" + schemaName + "\"." + tableName() : tableName();
    }

    protected String tableName() {
        return "osc_" + Inflector.getInstance().tableize(getClass());
    }

    protected String schemaName() {
        return null;
    }

    protected String databaseName() {
        return null;
    }

    /**
     * 插入对象到数据库表中
     *
     * @return
     */
    public long save() {
        if (getId() > 0) {
            _InsertObject(this);
        } else {
            setId(_InsertObject(this));
        }

        if (this.cachedByID()) {
            CacheMgr.evict(cacheRegion(), OBJ_COUNT_CACHE_KEY);
            if (cacheNullObject()) {
                CacheMgr.evict(cacheRegion(), String.valueOf(getId()));
            }
        }
        return getId();
    }

    /**
     * 根据id主键删除对象
     *
     * @return
     */
    public boolean delete() {
        boolean dr = evict(DBQuery.get(databaseName()).update("DELETE FROM " + rawTableName() + " WHERE id=?", getId()) == 1);
        if (dr && cachedByID()) {
            CacheMgr.evict(cacheRegion(), OBJ_COUNT_CACHE_KEY);
        }
        return dr;
    }

    /**
     * 根据条件决定是否清除对象缓存
     *
     * @param er
     * @return
     */
    public boolean evict(boolean er) {
        if (er && cachedByID()) {
            CacheMgr.evict(cacheRegion(), String.valueOf(getId()));
        }
        return er;
    }

    /**
     * 清除指定主键的对象缓存
     *
     * @param obj_id
     */
    public void evict(long obj_id) {
        CacheMgr.evict(cacheRegion(), String.valueOf(obj_id));
    }

    /**
     * 根据主键读取对象详细资料，根据预设方法自动判别是否需要缓存
     *
     * @param id
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> T get(long id) {
        if (id <= 0) {
            return null;
        }

        String sql = "SELECT * FROM " + rawTableName() + " WHERE id = ?";
        Cache cache = getClass().getAnnotation(Cache.class);
        return (cache != null) ?
                (T) DBQuery.get(databaseName()).read_cache(getClass(), cache.cacheNull(), cache.region(), String.valueOf(id), sql, id) :
                (T) DBQuery.get(databaseName()).read(getClass(), sql, id);
    }

    /**
     * 根据主键读取对象详细资料，可以排除某些字段，不缓存
     *
     * @param id
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> T get(int id, String[] exclusiveFields) {
        if (id <= 0) {
            return null;
        }
        Map<String, Object> pojo_bean = this.listInsertableFields();
        pojo_bean.put("id", getId());
        StringBuilder sql = new StringBuilder("SELECT ");
        int i = 0;
        for (String field : pojo_bean.keySet()) {
            if (ArrayUtils.contains(exclusiveFields, field)) {
                continue;
            }
            if (i > 0) {
                sql.append(',');
            }
            sql.append("\"");
            sql.append(field);
            sql.append("\"");
            i++;
        }
        sql.append(" FROM ");
        sql.append(rawTableName());
        sql.append(" WHERE id = ?");
        return (T) DBQuery.get(databaseName()).read(getClass(), sql.toString(), id);
    }

    public List<? extends Entity> get(List<Long> ids) {
        if (ids == null || ids.size() == 0) {
            return null;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM " + rawTableName() + " WHERE id IN (");
        for (int i = 1; i <= ids.size(); i++) {
            sql.append('?');
            if (i < ids.size()) {
                sql.append(',');
            }
        }
        sql.append(')');
        List<? extends Entity> beans = DBQuery.get(databaseName()).query(getClass(), sql.toString(), ids.toArray(new Object[ids.size()]));
        if (cachedByID()) {
            for (Object bean : beans) {
                CacheMgr.set(cacheRegion(), String.valueOf(((Entity) bean).getId()), bean);
            }
        }
        return beans;
    }

    /**
     * 统计此对象的总记录数
     *
     * @return
     */
    public int totalCount() {
        if (this.cachedByID()) {
            return DBQuery.get(databaseName()).stat_cache(cacheRegion(), OBJ_COUNT_CACHE_KEY, "SELECT COUNT(*) FROM " + rawTableName());
        }

        return DBQuery.get(databaseName()).stat("SELECT COUNT(*) FROM " + rawTableName());
    }

    /**
     * 批量加载项目
     *
     * @param p_pids
     * @return
     */
    @SuppressWarnings({"rawtypes"})
    public List loadList(List<Long> p_pids) {
        if (CollectionUtils.isEmpty(p_pids)) {
            return null;
        }

        if (p_pids.size() == 0) {
            return new ArrayList();
        }

        final List<Long> pids = new ArrayList<Long>(p_pids.size());
        for (Long obj : p_pids) {
            pids.add(obj);
        }
        List<Entity> prjs = new ArrayList<Entity>(pids.size()) {
            {
                for (int i = 0; i < pids.size(); i++) {
                    add(null);
                }
            }
        };
        List<Long> no_cache_ids = new ArrayList<>();
        if (this.cachedByID()) {
            String cache = this.cacheRegion();
            for (int i = 0; i < pids.size(); i++) {
                long pid = pids.get(i);
                Entity obj = (Entity) CacheMgr.get(cache, String.valueOf(pid));

                if (obj != null) {
                    prjs.set(i, obj);
                } else {
                    no_cache_ids.add(pid);
                }
            }
        } else {
            no_cache_ids.addAll(p_pids);
        }

        if (no_cache_ids.size() > 0) {
            List<? extends Entity> no_cache_prjs = get(no_cache_ids);
            if (no_cache_prjs != null) {
                for (Entity obj : no_cache_prjs) {
                    prjs.set(pids.indexOf(obj.getId()), obj);
                }
            }
        }

        return prjs;
    }

    /**
     * 更新某个字段值
     *
     * @param field
     * @param value
     * @return
     */
    public boolean updateField(String field, Object value) {
        String sql = "UPDATE " + rawTableName() + " SET " + field + " = ? WHERE id=?";
        return evict(DBQuery.get(databaseName()).update(sql, value, getId()) == 1);
    }

    /**
     * 执行 INSERT ... ON DUPLICATE KEY UPDATE 并返回 LAST_INSERT_ID
     *
     * @param sql
     * @param params
     * @return
     */
    public int executeInsertOrUpdateSQLAndReturnId(String sql, Object... params) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = DBQuery.get(databaseName()).conn().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            throw new DBException(e);
        } finally {
            DbUtils.closeQuietly(rs);
            DbUtils.closeQuietly(ps);
        }
    }

    /**
     * 插入对象
     *
     * @param obj
     * @return 返回插入对象的主键
     */
    private long _InsertObject(Entity obj) {
        Map<String, Object> pojo_bean = obj.listInsertableFields();
        if (this.getId() > 0) {
            pojo_bean.put("id", this.getId());
        }
        String[] fields = pojo_bean.keySet().stream().toArray(String[]::new);
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(obj.rawTableName());
        //双引号导致  形如 INSERT INTO osc_blogs("origin_url","abstracts","catalog","project", 语法异常，此处修改剔除双引号
        //修改为反单引号
        //sql.append("(\"");
        sql.append("(`");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sql.append("`,`");
            }
            sql.append(fields[i]);
        }
        //sql.append("\") VALUES(");
        sql.append("`) VALUES(");
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');
        try (PreparedStatement ps = DBQuery.get(databaseName()).conn().prepareStatement(sql.toString(),
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < fields.length; i++) {
                ps.setObject(i + 1, pojo_bean.get(fields[i]));
            }

            ps.executeUpdate();
            if (getId() > 0) {
                return getId();
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new DBException(e);
        }
    }

    /**
     * 列出要插入到数据库的字段集合，子类可以覆盖此方法
     *
     * @return
     */
    protected Map<String, Object> listInsertableFields() {
        Map<String, Object> props = new HashMap<>();
        try {
            PropertyDescriptor[] fields = Introspector.getBeanInfo(getClass()).getPropertyDescriptors();
            for (PropertyDescriptor field : fields) {
                if ("class".equals(field.getName())) {
                    continue;
                }
                if (getId() == 0 && "id".equals(field.getName())) {
                    continue;
                }
                Object fv = field.getReadMethod().invoke(this);
                if (fv == null) {
                    continue;
                }
                props.put(field.getName(), fv);
            }

            return props;

        } catch (IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("ListInsertableFields Failed", e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        // 不同的子类尽管ID是相同也是不相等的
        if (!getClass().equals(obj.getClass())) {
            return false;
        }
        Entity wb = (Entity) obj;
        return wb.getId() == getId();
    }

    /**
     * 返回对象对应的缓存区域名
     *
     * @return
     */
    public String cacheRegion() {
        Cache cache = this.getClass().getAnnotation(Cache.class);
        return (cache != null) ? cache.region() : null;
    }

    /**
     * 是否根据ID缓存对象，此方法对Get(long id)有效
     *
     * @return
     */
    private boolean cachedByID() {
        Cache cache = this.getClass().getAnnotation(Cache.class);
        return cache != null;
    }

    private boolean cacheNullObject() {
        Cache cache = this.getClass().getAnnotation(Cache.class);
        return cache != null && cache.cacheNull();
    }

    //更新对象
    public boolean doUpdate() {
        Map<String, Object> map = listInsertableFields();
        Object id = map.remove("id");
        Set<Map.Entry<String, Object>> entrys = map.entrySet();
        int size = entrys.size();
        Object[] params = new Object[entrys.size()];
        StringBuilder sql = new StringBuilder("update ").append(tableName()).append(" set ");
        int index = 0;
        for (Map.Entry<String, Object> entry : entrys) {
            sql.append("`" + entry.getKey() + "`").append("=?,");
            params[index] = entry.getValue();
            index++;
        }
        sql.replace(sql.length() - 1, sql.length(), " where id=");
        sql.append(id);
        return DBQuery.get(databaseName()).update(sql.toString(), params) > 0;
    }

    /**
     * 更新浏览数
     *
     * @param datas
     * @param update_cache
     * @return
     */
    public void UpdateViewCount(Map<Long, Integer> datas, boolean update_cache) {
        String sql = "UPDATE " + tableName() + " SET view_count=view_count+? WHERE id=?";
        int i = 0;
        Object[][] args = new Object[datas.size()][2];
        for (long id : datas.keySet()) {
            int count = datas.get(id);
            args[i][1] = id;
            args[i][0] = count;
            if (update_cache) {
                Entity obj = (Entity) CacheMgr.get(CacheRegion(), String.valueOf(id));
                if (obj != null) {
                    obj.setView_count(obj.getView_count() + count);
                    CacheMgr.set(CacheRegion(), String.valueOf(id), obj);
                }
            }
            i++;
        }
        DBQuery.get(databaseName()).batch(sql, args);
    }

    public List<? extends Entity> Filter(String filter, int page, int size, Object... params) {
        String sql = "SELECT * FROM " + tableName();
        if (StringUtils.isNotBlank(filter)) {
            if (filter.toLowerCase().contains("where")) {
                sql += filter;
            } else {
                sql += " WHERE " + filter;
            }
        }
        if (StringUtils.isNotBlank(filter) && !filter.toLowerCase().contains("order by")) {
            sql += " order by id desc";
        }
        return DBQuery.get(databaseName()).query_slice(getClass(), sql, page, size, params);
    }
}
