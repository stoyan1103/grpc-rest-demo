package com.example.web;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.example.grpcdemo.rest.generated.model.ErrorResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import static io.grpc.Status.Code.ALREADY_EXISTS;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static io.grpc.Status.Code.INVALID_ARGUMENT;
import static io.grpc.Status.Code.PERMISSION_DENIED;
import static io.grpc.Status.Code.UNAUTHENTICATED;
import static io.grpc.Status.Code.UNAVAILABLE;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class GlobalExceptionHandlerTest {

    private static final String INVALID_REQUEST_MESSAGE = "Invalid request";
    private static final String MALFORMED_BODY_MESSAGE = "Request body is malformed or contains an unsupported value";
    private static final String PRIVATE_DETAIL = "private implementation detail";
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = INTERNAL_SERVER_ERROR.getReasonPhrase();
    private static final String VALIDATION_MESSAGE = "must not be blank";
    private static final String CROSS_PARAMETER_VALIDATION_MESSAGE = "must describe a valid route";
    private static final String REQUEST_CONTEXT = "request";
    private static final String CUSTOMER_NAME_FIELD = "customerName";
    private static final String ADDRESS_FIELD = "address";
    private static final String ORDER_ID_PARAMETER = "orderId";
    private static final String REQUEST_VALIDATION_METHOD_NAME = "prepareRequestValidationTarget";
    private static final String RETURN_VALUE_VALIDATION_METHOD_NAME = "prepareReturnValueValidationTarget";
    private static final String ADDRESS_CITY_FIELD = ADDRESS_FIELD + ".city";
    private static final String CUSTOMER_NAME_VALIDATION = CUSTOMER_NAME_FIELD + ": " + VALIDATION_MESSAGE;
    private static final String ADDRESS_VALIDATION = ADDRESS_FIELD + ": " + VALIDATION_MESSAGE;
    private static final String HANDLER_VALIDATION_MESSAGE = ADDRESS_CITY_FIELD + ": " + VALIDATION_MESSAGE
        + "; " + ORDER_ID_PARAMETER + ": " + VALIDATION_MESSAGE
        + "; " + REQUEST_CONTEXT + ": " + CROSS_PARAMETER_VALIDATION_MESSAGE;

    private final GlobalExceptionHandler classUnderTest = new GlobalExceptionHandler();

    @ParameterizedTest
    @MethodSource("requestValidationProvider")
    void givenRequestValidationException_whenHandling_thenBadRequestContainsUsefulMessage(Exception exception, String expectedMessage) {
        assertErrorResponse(
            classUnderTest.handleRequestValidationException(exception),
            BAD_REQUEST,
            expectedMessage
        );
    }

    private static Stream<Arguments> requestValidationProvider() {
        return Stream.of(
            Arguments.of(
                methodArgumentNotValidException(
                    fieldError(CUSTOMER_NAME_FIELD, VALIDATION_MESSAGE),
                    fieldError(ADDRESS_FIELD, VALIDATION_MESSAGE),
                    fieldError(CUSTOMER_NAME_FIELD, VALIDATION_MESSAGE)
                ),
                ADDRESS_VALIDATION + "; " + CUSTOMER_NAME_VALIDATION
            ),
            Arguments.of(
                prepareConstraintViolationException("createOrder." + CUSTOMER_NAME_FIELD, VALIDATION_MESSAGE),
                CUSTOMER_NAME_VALIDATION
            ),
            Arguments.of(
                prepareConstraintViolationException(CUSTOMER_NAME_FIELD, VALIDATION_MESSAGE),
                CUSTOMER_NAME_VALIDATION
            ),
            Arguments.of(methodArgumentNotValidException(), INVALID_REQUEST_MESSAGE),
            Arguments.of(new IllegalArgumentException(PRIVATE_DETAIL), INVALID_REQUEST_MESSAGE)
        );
    }

    @Test
    void givenHandlerMethodValidationErrors_whenHandling_thenParameterFieldAndCrossParameterMessagesAreCombined() {
        HandlerMethodValidationException exception = prepareHandlerMethodValidationException(
            List.of(
                prepareParameterValidationResult(0, prepareMessageSourceResolvable(VALIDATION_MESSAGE)),
                prepareParameterValidationResult(1, fieldError(ADDRESS_CITY_FIELD, VALIDATION_MESSAGE))
            ),
            List.of(prepareMessageSourceResolvable(CROSS_PARAMETER_VALIDATION_MESSAGE)),
            false
        );

        assertErrorResponse(
            classUnderTest.handleHandlerMethodValidationException(exception),
            BAD_REQUEST,
            HANDLER_VALIDATION_MESSAGE
        );
    }

    @Test
    void givenHandlerMethodValidationWithoutDisplayMessage_whenHandling_thenGenericValidationMessageIsReturned() {
        HandlerMethodValidationException exception = prepareHandlerMethodValidationException(
            List.of(prepareParameterValidationResult(0, prepareMessageSourceResolvable(null))),
            List.of(),
            false
        );

        assertErrorResponse(
            classUnderTest.handleHandlerMethodValidationException(exception),
            BAD_REQUEST,
            INVALID_REQUEST_MESSAGE
        );
    }

    @Test
    void givenReturnValueValidationFailure_whenHandling_thenInternalDetailsAreHidden() {
        HandlerMethodValidationException exception = prepareHandlerMethodValidationException(
            List.of(prepareParameterValidationResult(-1, prepareMessageSourceResolvable(PRIVATE_DETAIL))),
            List.of(),
            true
        );

        ResponseEntity<ErrorResponse> response = classUnderTest.handleHandlerMethodValidationException(exception);

        assertErrorResponse(response, INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
        assertDoesNotExposePrivateDetail(response);
    }

    @Test
    void givenUnreadableRequestBody_whenHandling_thenSafeBadRequestIsReturned() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

        assertErrorResponse(
            classUnderTest.handleUnreadableRequestBody(exception),
            BAD_REQUEST,
            MALFORMED_BODY_MESSAGE
        );
    }

    @ParameterizedTest
    @MethodSource("grpcStatusProvider")
    void givenGrpcStatus_whenHandling_thenMappedStatusAndSafeMessageAreReturned(
        Status.Code grpcCode,
        HttpStatus expectedStatus,
        String expectedMessage
    ) {
        StatusRuntimeException exception = Status.fromCode(grpcCode)
            .withDescription(PRIVATE_DETAIL)
            .asRuntimeException();

        ResponseEntity<ErrorResponse> response = classUnderTest.handleGrpcException(exception);

        assertErrorResponse(response, expectedStatus, expectedMessage);
        assertDoesNotExposePrivateDetail(response);
    }

    private static Stream<Arguments> grpcStatusProvider() {
        return Stream.of(
            Arguments.of(INVALID_ARGUMENT, BAD_REQUEST, "Invalid order request"),
            Arguments.of(Status.Code.NOT_FOUND, HttpStatus.NOT_FOUND, "Order not found"),
            Arguments.of(ALREADY_EXISTS, CONFLICT, "Order already exists"),
            Arguments.of(PERMISSION_DENIED, FORBIDDEN, "Permission denied"),
            Arguments.of(UNAUTHENTICATED, UNAUTHORIZED, "Authentication required"),
            Arguments.of(UNAVAILABLE, SERVICE_UNAVAILABLE, "Order service unavailable"),
            Arguments.of(DEADLINE_EXCEEDED, GATEWAY_TIMEOUT, "Order service timed out"),
            Arguments.of(Status.Code.UNKNOWN, INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE)
        );
    }

    @Test
    void givenIllegalStateException_whenHandling_thenInternalDetailsAreHidden() {
        ResponseEntity<ErrorResponse> response = classUnderTest.handleIllegalStateException(
            new IllegalStateException(PRIVATE_DETAIL)
        );

        assertErrorResponse(response, INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
        assertDoesNotExposePrivateDetail(response);
    }

    @Test
    void givenUnhandledException_whenHandling_thenInternalDetailsAreHidden() {
        ResponseEntity<ErrorResponse> response = classUnderTest.handleGenericException(
            new RuntimeException(PRIVATE_DETAIL)
        );

        assertErrorResponse(response, INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
        assertDoesNotExposePrivateDetail(response);
    }

    private static MethodArgumentNotValidException methodArgumentNotValidException(FieldError... fieldErrors) {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), REQUEST_CONTEXT);
        Stream.of(fieldErrors).forEach(bindingResult::addError);
        return new MethodArgumentNotValidException(prepareMethodParameter(0), bindingResult);
    }

    private static FieldError fieldError(String fieldName, String message) {
        return new FieldError(REQUEST_CONTEXT, fieldName, message);
    }

    private static ConstraintViolationException prepareConstraintViolationException(String propertyPath, String message) {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn(propertyPath);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn(message);
        return new ConstraintViolationException(Set.of(violation));
    }

    private static HandlerMethodValidationException prepareHandlerMethodValidationException(
        List<ParameterValidationResult> parameterResults,
        List<MessageSourceResolvable> crossParameterErrors,
        boolean returnValueFailure
    ) {
        Method method = returnValueFailure ? returnValueMethod() : requestValidationMethod();
        MethodValidationResult result = MethodValidationResult.create(
            new GlobalExceptionHandlerTest(),
            method,
            parameterResults,
            crossParameterErrors
        );
        return new HandlerMethodValidationException(result);
    }

    private static ParameterValidationResult prepareParameterValidationResult(
        int parameterIndex,
        MessageSourceResolvable error
    ) {
        Method method = parameterIndex == -1 ? returnValueMethod() : requestValidationMethod();
        return new ParameterValidationResult(
            prepareMethodParameter(method, parameterIndex),
            null,
            List.of(error),
            null,
            null,
            null,
            (message, sourceType) -> null
        );
    }

    private static MessageSourceResolvable prepareMessageSourceResolvable(String defaultMessage) {
        return new DefaultMessageSourceResolvable(new String[0], null, defaultMessage);
    }

    private static MethodParameter prepareMethodParameter(int parameterIndex) {
        return prepareMethodParameter(requestValidationMethod(), parameterIndex);
    }

    private static MethodParameter prepareMethodParameter(Method method, int parameterIndex) {
        MethodParameter methodParameter = new MethodParameter(method, parameterIndex);
        methodParameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        return methodParameter;
    }

    private static Method requestValidationMethod() {
        return method(REQUEST_VALIDATION_METHOD_NAME, String.class, String.class);
    }

    private static Method returnValueMethod() {
        return method(RETURN_VALUE_VALIDATION_METHOD_NAME, String.class);
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            return GlobalExceptionHandlerTest.class.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Missing validation test method: " + name, exception);
        }
    }

    private static void assertErrorResponse(
        ResponseEntity<ErrorResponse> response,
        HttpStatus expectedStatus,
        String expectedMessage
    ) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(expectedStatus.value());
        assertThat(response.getBody().getMessage()).isEqualTo(expectedMessage);
    }

    private static void assertDoesNotExposePrivateDetail(ResponseEntity<ErrorResponse> response) {
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).doesNotContain(PRIVATE_DETAIL);
    }

    // Spring's validation exception needs real method metadata for parameter names and return-value detection.
    private void prepareRequestValidationTarget(String orderId, String address) {
        requireNonNull(orderId);
        requireNonNull(address);
    }

    private String prepareReturnValueValidationTarget(String input) {
        return input;
    }
}
