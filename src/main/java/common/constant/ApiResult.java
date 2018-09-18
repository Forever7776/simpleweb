package common.constant;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;

import java.util.Date;

public class ApiResult<T> {

	private int code;
	private String message;
	private T result;

	@JSONField(format = "yyyy-MM-dd HH:mm:ss")
	private Date time;

	public ApiResult() {
		this(0,"其他未知异常",null);
	}

	public ApiResult(int code, String message, T result) {
		this.code = code;
		this.message = message;
		this.result = result;
		this.time = new Date();
	}

	public static ApiResult success() {
		return new ApiResult(1, "success", null);
	}
	public static ApiResult success(String message) {
		return new ApiResult(1, message, "");
	}

	public static ApiResult successWithObject(Object object, String message) { return new ApiResult(1, message, object);}
	
	public static ApiResult successWithObject(Object object) { return new ApiResult(1, "success", object);}

	public static ApiResult failWithMessage(String message) {return new ApiResult(0, message, null);}

	public static ApiResult failWithMessageAndObject(String message,Object object) {return new ApiResult(0, message, object);}

	public String json() {
		return JSON.toJSONString(this);
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Date getTime() {
		return time;
	}

	public void setTime(Date time) {
		this.time = time;
	}

	public T getResult() {
		return result;
	}

	public void setResult(T result) {
		this.result = result;
	}
}
