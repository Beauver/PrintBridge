package com.beauver.printbridge.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Locale;

public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            Currency.getInstance(value.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }
}
