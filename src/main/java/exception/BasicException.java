package exception;



import utils.ResourceUtils;

import java.util.Locale;

/**
 * Basic异常
 * key对应error.properties里的key.
 */
public class BasicException extends RuntimeException {


    private String key;
    private Object[] args;


    public BasicException(String key, Object... args) {
        super(ResourceUtils.errorForLocal(Locale.getDefault(), key, args));
        this.key = key;
        this.args = args;
    }


    public String getKey() {
        return this.key;
    }

    public Object[] getArgs() {
        return args;
    }

    public String getI18NMessage(Locale locale) {
        if (null != locale) {
            return ResourceUtils.errorForLocal(locale, key, args);
        } else {
            return ResourceUtils.errorForLocal(Locale.getDefault(), key, args);
        }
    }

}
