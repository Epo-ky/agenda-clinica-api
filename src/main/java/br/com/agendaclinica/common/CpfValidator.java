package br.com.agendaclinica.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<Cpf, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isValid(value);
    }

    /** Fica estático para ser testado sem subir o Spring. */
    public static boolean isValid(String value) {
        String digits = onlyDigits(value);
        if (digits.length() != 11) {
            return false;
        }
        // 111.111.111-11 e afins passam na conta dos dígitos, mas não são CPFs válidos.
        if (digits.chars().distinct().count() == 1) {
            return false;
        }
        int first = checkDigit(digits, 9);
        int second = checkDigit(digits, 10);
        return first == digits.charAt(9) - '0' && second == digits.charAt(10) - '0';
    }

    public static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /** Calcula o dígito verificador usando os primeiros {@code length} dígitos. */
    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }
}
