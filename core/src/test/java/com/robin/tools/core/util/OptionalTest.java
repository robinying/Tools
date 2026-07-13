package com.robin.tools.core.util;

import org.junit.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OptionalTest {

    @Test
    public void emptyAndNullableValuesExposeExpectedPresence() {
        Optional<String> empty = Optional.empty();
        Optional<String> present = Optional.ofNullable("value");

        assertTrue(empty.isEmpty());
        assertFalse(empty.isPresent());
        assertTrue(present.isPresent());
        assertEquals("value", present.get());
    }

    @Test
    public void ofRejectsNullAndEmptyGetThrows() {
        try {
            Optional.of(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected.
        }

        try {
            Optional.empty().get();
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            assertEquals("No value present", expected.getMessage());
        }
    }

    @Test
    public void filterAndMapPreserveOnlyMatchingValues() {
        Optional<Integer> value = Optional.of(4);

        assertSame(value, value.filter(number -> number % 2 == 0));
        assertTrue(value.filter(number -> number % 2 != 0).isEmpty());
        assertEquals("value-4", value.map(number -> "value-" + number).get());
        assertTrue(value.map(number -> null).isEmpty());
    }

    @Test
    public void emptyOptionalSkipsMapperAndPredicate() {
        AtomicInteger invocations = new AtomicInteger();
        Optional<Integer> empty = Optional.empty();

        Optional<String> mapped = empty.map(value -> {
            invocations.incrementAndGet();
            return String.valueOf(value);
        });
        Optional<Integer> filtered = empty.filter(value -> {
            invocations.incrementAndGet();
            return true;
        });

        assertTrue(mapped.isEmpty());
        assertTrue(filtered.isEmpty());
        assertEquals(0, invocations.get());
    }

    @Test
    public void flatMapAndOrRespectLazyFallbacks() {
        AtomicInteger supplierCalls = new AtomicInteger();
        Optional<String> present = Optional.of("value");
        Optional<String> empty = Optional.empty();

        assertEquals(5, present.flatMap(value -> Optional.of(value.length())).get().intValue());
        assertSame(present, present.or(() -> {
            supplierCalls.incrementAndGet();
            return Optional.of("fallback");
        }));
        assertEquals("fallback", empty.or(() -> {
            supplierCalls.incrementAndGet();
            return Optional.of("fallback");
        }).get());
        assertEquals(1, supplierCalls.get());
    }

    @Test
    public void fallbackAndExceptionMethodsUseExpectedBranch() {
        AtomicInteger supplierCalls = new AtomicInteger();

        assertEquals("value", Optional.of("value").orElseGet(() -> {
            supplierCalls.incrementAndGet();
            return "fallback";
        }));
        assertEquals("fallback", Optional.<String>empty().orElseGet(() -> {
            supplierCalls.incrementAndGet();
            return "fallback";
        }));
        assertEquals(1, supplierCalls.get());

        try {
            Optional.empty().orElseThrow(IllegalStateException::new);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }
}
