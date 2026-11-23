package com.beauver.printbridge.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ISO 4217 currency code that Java knows (e.g. {@code EUR}, {@code usd}); case doesn't matter.
 * <p>
 * Only checks the format: {@code null} passes, so a required currency also needs {@code @NotNull}.
 * That is the Bean Validation convention (like {@code @Size} and {@link MaxUtf8Bytes}): a missing
 * value then gives one clear "is required" message instead of two violations.
 */
@Documented
@Constraint(validatedBy = CurrencyCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrencyCode {

    String message() default "Unknown currency";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
