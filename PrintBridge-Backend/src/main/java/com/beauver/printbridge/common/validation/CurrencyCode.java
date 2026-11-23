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
 * {@code null} is valid.
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
