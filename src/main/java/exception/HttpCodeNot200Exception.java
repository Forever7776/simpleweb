package exception;

public class HttpCodeNot200Exception extends Exception {
	public HttpCodeNot200Exception(String msg) {
		super(msg);
	}
}