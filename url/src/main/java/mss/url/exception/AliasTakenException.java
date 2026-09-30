package mss.url.exception;

public class AliasTakenException extends RuntimeException {

    public AliasTakenException(String alias) {
        super("Alias already in use: " + alias);
    }
}
