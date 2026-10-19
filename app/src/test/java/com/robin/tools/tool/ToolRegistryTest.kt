package com.robin.tools.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun toolIds_areUnique() {
        val ids = ToolRegistry.tools.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyTool_hasTitleAndDescription() {
        ToolRegistry.tools.forEach { tool ->
            assertTrue("Missing title for ${tool.id}", tool.titleRes != 0)
            assertTrue("Missing description for ${tool.id}", tool.descriptionRes != 0)
        }
    }

    @Test
    fun everyCategory_hasAtLeastOneTool() {
        ToolCategory.entries.forEach { category ->
            assertTrue(
                "No tools in $category",
                ToolRegistry.tools.any { it.category == category }
            )
        }
    }

    @Test
    fun everyMediaTool_belongsToAGroup() {
        ToolRegistry.tools
            .filter { it.category == ToolCategory.MEDIA }
            .forEach { tool ->
                assertTrue("Media tool ${tool.id} has no group", tool.group != null)
            }
    }

    @Test
    fun everyGroup_hasAtLeastOneTool() {
        ToolGroup.entries.forEach { group ->
            assertTrue(
                "No tools in $group",
                ToolRegistry.tools.any { it.group == group }
            )
        }
    }

    @Test
    fun byId_returnsRegisteredTool() {
        val first = ToolRegistry.tools.first()
        assertEquals(first, ToolRegistry.byId(first.id))
    }
}
