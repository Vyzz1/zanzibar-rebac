package zanzibar.huynhvy.namespace.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zanzibar.huynhvy.namespace.exception.InvalidNamespaceConfigException;
import zanzibar.huynhvy.namespace.exception.NamespaceNotFoundException;

@RestControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(InvalidNamespaceConfigException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse onInvalidConfig(InvalidNamespaceConfigException e) {
    return new ErrorResponse("INVALID_CONFIG", e.getMessage());
  }

  @ExceptionHandler(NamespaceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse onNotFound(NamespaceNotFoundException e) {
    return new ErrorResponse("NOT_FOUND", e.getMessage());
  }

  public record ErrorResponse(String code, String message) {}
}
