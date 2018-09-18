package utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JsonUtils {
    /**
     * 输出JSON Str
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    public static String output_json(String[] key, Object[] value) throws IOException {
        JsonObject jo = new JsonObject();
        for(int i=0;i<key.length;i++){
            if(value[i] instanceof Number)
                jo.addProperty(key[i], (Number)value[i]);
            else if(value[i] instanceof Boolean)
                jo.addProperty(key[i], (Boolean)value[i]);
            else
                jo.addProperty(key[i], (String)value[i]);
        }
        return new Gson().toJson(jo);
    }

    /**
     * 输出JSON Str
     * @param key
     * @param value
     * @throws IOException
     */
    public static String output_json(String key, Object value) throws IOException {
       return output_json(new String[]{key}, new Object[]{value});
    }

    /**
     *
     * @param key
     * @param value
     * @return
     * @throws IOException
     */
    public static Map<String,Object> getJsonMap(String[] key, Object[] value){
        Map<String, Object> mapJson = new HashMap<String,Object>();
        for(int i=0;i<key.length;i++){
            if(value[i] instanceof Number)
                mapJson.put(key[i], (Number)value[i]);
            else if(value[i] instanceof Boolean)
                mapJson.put(key[i], (Boolean)value[i]);
            else
                mapJson.put(key[i], (String)value[i]);
        }
        return mapJson;
    }

    public static Map<String,Object> getJsonMap(String key, Object value) throws IOException {
        return getJsonMap(new String[]{key}, new Object[]{value});
    }
}
