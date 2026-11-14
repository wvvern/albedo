package dev.albedo.core.shader;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static dev.albedo.core.shader.ShaderState.CLOSED;
import static dev.albedo.core.shader.ShaderState.FAILED;
import static dev.albedo.core.shader.ShaderState.NOT_STARTED;
import static dev.albedo.core.shader.ShaderState.READY;
import static dev.albedo.core.shader.ShaderState.RUNNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderStateTest {
    @Test
    void eachLegalMoveGoesToItsTarget() {
        assertMoves(NOT_STARTED, EnumSet.of(RUNNING, READY, FAILED, CLOSED));
        assertMoves(RUNNING, EnumSet.of(READY, FAILED, CLOSED));
        assertMoves(READY, EnumSet.of(READY, FAILED, CLOSED));
        assertMoves(FAILED, EnumSet.of(CLOSED));
    }

    @Test
    void closedStaysClosed() {
        for (ShaderState next : ShaderState.values()) assertEquals(CLOSED, CLOSED.to(next), "CLOSED to " + next);
    }

    @Test
    void onlyReadyIsUsableAndOnlyFailedAndClosedAreDead() {
        for (ShaderState s : ShaderState.values()) {
            assertEquals(s == READY, s.usable(), s.name());
            assertEquals(s == FAILED || s == CLOSED, s.dead(), s.name());
        }
        assertFalse(FAILED.to(READY).usable(), "a failed shader never compiles again");
        assertTrue(READY.to(FAILED).dead(), "the game can reject a ready pipeline");
    }

    private static void assertMoves(ShaderState from, Set<ShaderState> legal) {
        for (ShaderState next : ShaderState.values()) {
            assertEquals(legal.contains(next) ? next : from, from.to(next), from + " to " + next);
        }
    }
}
