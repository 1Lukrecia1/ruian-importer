package cz.trixi.ruian.xml;

public class RuianParseException extends RuntimeException {

    public RuianParseException(String message) {
        super(message);
    }

    public RuianParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
