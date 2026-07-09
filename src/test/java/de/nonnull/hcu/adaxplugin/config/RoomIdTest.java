package de.nonnull.hcu.adaxplugin.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RoomIdTest {

    @Test
    public void toIdentifier_joinsWithDash() {
        assertEquals("1-2", new RoomId(1, 2).toIdentifier());
    }

    @Test
    public void fromIdentifier_parsesBothParts() {
        assertEquals(new RoomId(1, 2), RoomId.fromIdentifier("1-2"));
    }

    @Test
    public void identifier_roundTrips() {
        final var roomId = new RoomId(42, 4711);
        assertEquals(roomId, RoomId.fromIdentifier(roomId.toIdentifier()));
    }

    @Test
    public void fromIdentifier_handlesIdsAboveIntegerRange() {
        final var homeId = 3_000_000_000L;
        final var roomId = 4_000_000_000L;
        final var parsed = RoomId.fromIdentifier(homeId + "-" + roomId);
        assertEquals(homeId, parsed.getHomeId());
        assertEquals(roomId, parsed.getRoomId());
    }

    @Test
    public void fromIdentifier_rejectsWrongNumberOfParts() {
        assertThrows(IllegalArgumentException.class, () -> RoomId.fromIdentifier("1"));
        assertThrows(IllegalArgumentException.class, () -> RoomId.fromIdentifier("1-2-3"));
    }

    @Test
    public void fromIdentifier_rejectsNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> RoomId.fromIdentifier("a-b"));
        assertThrows(IllegalArgumentException.class, () -> RoomId.fromIdentifier("1-x"));
    }
}
