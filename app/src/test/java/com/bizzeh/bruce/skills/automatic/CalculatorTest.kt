package com.bizzeh.bruce.skills.automatic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class CalculatorTest {
    private fun value(expression: String) = Calculator.format((Calculator.evaluate(expression) as Calculation.Value).value)
    private fun error(expression: String) = (Calculator.evaluate(expression) as Calculation.Error).reason

    /** Including the calculator cases from TASK-033. */
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "1234 * 5678 | 7006652",
            "17.5% * 2340 | 409.5",
            "187.60 / 4 | 46.9",
            "(45 + 38) * 12 - 7 | 989",
            "2 ^ 20 | 1048576",
            "9876 / 12 | 823",
            "2 ^ 3 ^ 2 | 512",
            "2 ^ -2 | 0.25",
            "-(-2) + +3 | 5",
            "1,000 + 1 | 1001",
            "6 × 7 ÷ 2 | 21",
            "50%% | 0.005",
            "10 - 2 - 3 | 5",
            "1 / 3 * 3 | 0.9999999999999999999999999999999999",
            ".5 + 1. | 1.5",
            "0.1 + 0.2 | 0.3",
        ],
    )
    fun evaluatesExactly(expression: String, expected: String) {
        assertEquals(expected, value(expression))
    }

    @ParameterizedTest
    @ValueSource(strings = ["1 / 0", "0 ^ -1", "(1 + 2", "1 +", "", "   ", "import os", "1..2", ".", "2 ^ 0.5", "2 ^ 1001", "10 ^ 999 * 10 ^ 999", "1 2"])
    fun refusesWhatItCannotEvaluate(expression: String) {
        assertTrue(error(expression).isNotBlank())
    }

    @org.junit.jupiter.api.Test
    fun inputSizeAndNestingAreBounded() {
        assertEquals("expression longer than ${Calculator.MAX_LENGTH} characters", error("1+".repeat(101) + "1"))
        assertEquals("too deeply nested", error("(".repeat(40) + "1" + ")".repeat(40)))
        assertEquals("too deeply nested", error("-".repeat(40) + "1"))
        assertEquals("division by zero", error("1 / (2 - 2)"))
        assertEquals("unexpected 'i'", error("import os"))
    }
}
