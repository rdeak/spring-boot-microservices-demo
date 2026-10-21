package hr.bertlab.orderservice.model;

public class InvalidOrderException extends IllegalArgumentException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
