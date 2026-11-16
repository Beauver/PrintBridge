package com.beauver.printbridge.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The text may take at most {@link #value} bytes in UTF-8. Unlike {@code @Size} this counts
 * accented letters as two bytes and emoji as four, which matters where bytes are the real limit
 * (bcrypt hashes only the first 72 bytes of a password). {@code null} is valid.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
