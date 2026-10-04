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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gives a Java entity one Bedrock runtime id across every split-screen player on a console.
 *
 * <p>Each local player is its own session with its own Java connection, so the server reports the same creature
 * to each of them. The console keeps one entity list per connection, as vanilla feeds every local player from one
 * server with one id per creature, so each session must spawn the creature under the same id and only the last
 * session to stop tracking it may remove it. Entities are matched by Java UUID together with Java entity id: every
 * player on one backend server sees the same pair, while a creature on another server, even one copied from the
 * same world template, has a different entity id.
 */
public final class SplitScreenEntityIds {
    private final Map<JavaEntity, Shared> byEntity = new HashMap<>();

    private record JavaEntity(UUID uuid, int javaId) {
    }

    private record Shared(long geyserId, Set<GeyserSession> holders) {
    }

    /**
     * @return the id every session on this console uses for the entity, or {@code null} when {@code holder} already
     * holds one with this identity and must keep {@code ownId} unshared
     */
    public synchronized @Nullable Long acquire(UUID uuid, int javaId, GeyserSession holder, long ownId) {
        JavaEntity key = new JavaEntity(uuid, javaId);
        Shared shared = byEntity.get(key);
        if (shared == null || !hasLiveHolder(shared)) {
            shared = new Shared(ownId, Collections.newSetFromMap(new IdentityHashMap<>()));
            byEntity.put(key, shared);
        }
        if (!shared.holders().add(holder)) {
            return null;
        }
        return shared.geyserId();
    }

    /**
     * @return whether no other session on this console still tracks the entity, so the client may remove it
     */
    public synchronized boolean release(UUID uuid, int javaId, GeyserSession holder) {
        JavaEntity key = new JavaEntity(uuid, javaId);
        Shared shared = byEntity.get(key);
        if (shared == null) {
            return true;
        }
        shared.holders().remove(holder);
        if (hasLiveHolder(shared)) {
            return false;
        }
        // The client ignores a spawn for an id it already removed, so a returning entity must get a fresh one.
        byEntity.remove(key);
        return true;
    }

    private static boolean hasLiveHolder(Shared shared) {
        // A session that disconnected never releases what it held.
        shared.holders().removeIf(GeyserSession::isClosed);
        return !shared.holders().isEmpty();
    }
}
