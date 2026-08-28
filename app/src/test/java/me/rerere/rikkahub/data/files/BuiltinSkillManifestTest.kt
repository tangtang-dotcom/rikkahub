package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Test

class BuiltinSkillManifestTest {
    @Test
    fun `Eta builtin skill manifest exposes required skills`() {
        val skills = parseBuiltinSkillManifest(
            """
                {
                  "skills": [
                    {"id":"self-improving-agent","assetPath":"builtin_skills/self-improving-agent"},
                    {"id":"skill-creator","assetPath":"builtin_skills/skill-creator"},
                    {"id":"skill-installer","assetPath":"builtin_skills/skill-installer"}
                  ]
                }
            """.trimIndent()
        )

        assertEquals(
            listOf("self-improving-agent", "skill-creator", "skill-installer"),
            skills.map { it.id },
        )
    }
}
