package mss.url.exception;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("No link found for code: " + code);
    }
}
