package io.github.sgirishshiva.glyphwizard.evals

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun `main prints usage until suites exist`() {
        val original = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        try {
            main()
        } finally {
            System.setOut(original)
        }
        assertEquals("glyphwizard-evals: no suites yet (M5)\n", captured.toString(Charsets.UTF_8))
    }
}
