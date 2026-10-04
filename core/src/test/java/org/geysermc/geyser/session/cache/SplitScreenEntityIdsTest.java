/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.session.cache;

import org.geysermc.geyser.session.GeyserSession;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SplitScreenEntityIdsTest {
    private final SplitScreenEntityIds ids = new SplitScreenEntityIds();
    private final GeyserSession primary = mock(GeyserSession.class);
    private final GeyserSession guest = mock(GeyserSession.class);
    private final UUID creeper = UUID.randomUUID();

    private static final int JAVA_ID = 500;

    @Test
    public void sameCreatureSharesOneIdUntilTheLastPlayerStopsTrackingIt() {
        assertEquals(10L, ids.acquire(creeper, JAVA_ID, primary, 10));
        assertEquals(10L, ids.acquire(creeper, JAVA_ID, guest, 4294967300L), "guest reuses the primary's id");
        assertEquals(4294967301L, ids.acquire(UUID.randomUUID(), 501, guest, 4294967301L), "another creature keeps its own id");

        assertFalse(ids.release(creeper, JAVA_ID, primary), "guest still sees it, so the client must keep it");
        assertTrue(ids.release(creeper, JAVA_ID, guest));

        assertEquals(4294967302L, ids.acquire(creeper, JAVA_ID, guest, 4294967302L), "a removed id is never reused");
    }

    @Test
    public void onlyTheSameEntityOnTheSameServerIsShared() {
        ids.acquire(creeper, JAVA_ID, primary, 10);

        assertEquals(4294967300L, ids.acquire(creeper, 77, guest, 4294967300L), "same UUID on another server is another creature");
        assertNull(ids.acquire(creeper, JAVA_ID, primary, 11), "a second entity with the same identity in one session stays unshared");
    }

    @Test
    public void disconnectedPlayerDoesNotKeepCreaturesAlive() {
        ids.acquire(creeper, JAVA_ID, primary, 10);
        ids.acquire(creeper, JAVA_ID, guest, 4294967300L);
        when(guest.isClosed()).thenReturn(true);

        assertTrue(ids.release(creeper, JAVA_ID, primary));
    }
}
