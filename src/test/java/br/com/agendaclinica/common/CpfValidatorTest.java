package br.com.agendaclinica.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpfValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "529.982.247-25", "11144477735"})
    void aceitaCpfValido(String cpf) {
        assertTrue(CpfValidator.isValid(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224726", "12345678900", "123", "", "abc", "529.982.247-2"})
    void recusaCpfInvalido(String cpf) {
        assertFalse(CpfValidator.isValid(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "99999999999"})
    void recusaCpfComTodosOsDigitosIguais(String cpf) {
        assertFalse(CpfValidator.isValid(cpf));
    }

    @Test
    void removeAPontuacao() {
        assertEquals("52998224725", CpfValidator.onlyDigits("529.982.247-25"));
        assertEquals("", CpfValidator.onlyDigits(null));
    }
}
