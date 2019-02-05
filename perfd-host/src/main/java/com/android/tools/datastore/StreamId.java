/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.datastore;

import com.android.tools.profiler.proto.Common;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * A semantic wrapper for the id of a stream, which is just a long for a device (serial + boot_id).
 */
public final class StreamId {
  private static final Map<Long, StreamId> ourInstances = new HashMap<>();
  private long myStreamId;

  @NotNull
  public static StreamId of(long streamId) {
    return ourInstances.computeIfAbsent(streamId, id -> new StreamId(id));
  }

  /**
   * Only used by the legacy pipeline where we use set device ID to Session's streamId field.
   */
  @NotNull
  public static StreamId fromSession(@NotNull Common.Session session) {
    return of(session.getStreamId());
  }

  private StreamId(long streamId) {
    myStreamId = streamId;
  }

  public long get() {
    return myStreamId;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(myStreamId);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof StreamId)) {
      return false;
    }

    StreamId other = (StreamId)obj;
    return myStreamId == other.myStreamId;
  }
}
