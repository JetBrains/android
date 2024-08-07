/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.scope.scopes;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import java.time.Duration;

/** Called once the scope ends, with the timing information of the scope and all its children. */
public interface TimingScopeListener {

  /** Timing information for a scoped event. */
  class TimedEvent {
    public final String name;
    public final EventType type;
    public final Duration duration;
    public final boolean isLeafEvent;

    public TimedEvent(String name, EventType type, Duration duration, boolean isLeafEvent) {
      this.name = name;
      this.type = type;
      this.duration = duration;
      this.isLeafEvent = isLeafEvent;
    }
  }

  /** Called once the scope ends */
  void onScopeEnd(ImmutableList<TimedEvent> events, Duration totalTime);
}
