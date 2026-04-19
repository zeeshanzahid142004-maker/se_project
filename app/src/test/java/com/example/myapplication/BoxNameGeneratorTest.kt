package com.example.myapplication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxNameGeneratorTest {

    @Test
    fun generate_returnsBoxPrefixedName() {
        val name = BoxNameGenerator.generate()
        assertTrue("Name should start with 'BOX-'", name.startsWith("BOX-"))
    }

    @Test
    fun generate_hasThreeParts() {
        val name = BoxNameGenerator.generate()
        val parts = name.split("-")
        assertTrue("Name should have 3 dash-separated parts", parts.size == 3)
    }

    @Test
    fun generateUnique_returnsNameNotInExistingSet() {
        val existing = mutableSetOf<String>()
        repeat(50) { existing.add(BoxNameGenerator.generate()) }
        val unique = BoxNameGenerator.generateUnique(existing)
        // Result must either not be in the existing set OR be the timestamp fallback
        val isNew        = !existing.contains(unique)
        val isFallback   = unique.startsWith("BOX-") && unique.removePrefix("BOX-").toLongOrNull() != null
        assertTrue(
            "generateUnique should return a name absent from existing set or a timestamp fallback",
            isNew || isFallback
        )
    }

    @Test
    fun generateUnique_emptySet_returnsValidName() {
        val name = BoxNameGenerator.generateUnique(emptySet())
        assertTrue("Name should start with 'BOX-'", name.startsWith("BOX-"))
        assertFalse("Name should not be blank", name.isBlank())
    }

    @Test
    fun generateUnique_neverReturnsBlank() {
        // Fill the set with many names to force the timestamp fallback path
        val huge = (1..5000).map { BoxNameGenerator.generate() }.toSet()
        val result = BoxNameGenerator.generateUnique(huge, maxAttempts = 3)
        assertFalse("Result must never be blank", result.isBlank())
        assertTrue("Result must start with 'BOX-'", result.startsWith("BOX-"))
    }
}
