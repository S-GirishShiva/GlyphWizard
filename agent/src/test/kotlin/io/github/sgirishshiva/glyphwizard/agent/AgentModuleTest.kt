package io.github.sgirishshiva.glyphwizard.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentModuleTest {
    @Test
    fun `module reports its name`() {
        assertEquals("agent", AgentModule.NAME)
    }
}
