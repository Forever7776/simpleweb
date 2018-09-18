package common.db;

import net.oschina.j2cache.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 * 缓存管理器
 */
public class CacheMgr {

    private final static String CONFIG_FILE = "/j2cache.properties";
    private final static J2CacheBuilder builder;

    static {
        try {
            J2CacheConfig config = J2CacheConfig.initFromConfig(CONFIG_FILE);
            builder = J2CacheBuilder.init(config);
        } catch (IOException e) {
            throw new CacheException("Failed to load j2cache configuration " + CONFIG_FILE, e);
        }
    }

    private final static CacheChannel cache = builder.getChannel();

    public static Object get(String region, String key)  {
        return cache.get(region, key).getValue();
    }

    public static Object get(String region, String key, Function<String, Object> loader, boolean...cacheNullObject) {
        return cache.get(region, key, loader, cacheNullObject).getValue();
    }

    public static Map<String, CacheObject> get(String region, Collection<String> keys)  {
        return cache.get(region, keys);
    }

    public static Map<String, CacheObject> get(String region, Collection<String> keys, Function<String, Object> loader, boolean...cacheNullObject)  {
        return cache.get(region, keys, loader, cacheNullObject);
    }

    public static boolean exists(String region, String key)  {
        return cache.exists(region, key);
    }

    public static void set(String region, String key, Object value)  {
        cache.set(region, key, value);
    }

    public static void set(String region, Map<String, Object> elements)  {
        cache.set(region, elements);
    }

    public static void evict(String region, String... keys)  {
        cache.evict(region, keys);
    }

    public static void clear(String region)  {
        cache.clear(region);
    }

    public static Collection<String> keys(String region)  {
        return cache.keys(region);
    }

    public static void close(){
        cache.close();
    }

}
