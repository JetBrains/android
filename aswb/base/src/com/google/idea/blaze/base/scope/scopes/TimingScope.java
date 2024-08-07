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
import com.google.common.collect.Lists;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.intellij.openapi.diagnostic.Logger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Collects and logs timing information. */
public class TimingScope implements BlazeScope {

  private static final Logger logger = Logger.getInstance(TimingScope.class);

  /** The type of event for which timing information is being recorded */
  public enum EventType {
    BlazeInvocation,
    Prefetching,
    Other,
  }

  private final String name;
  private final EventType eventType;

  private Instant startTime;

  private Optional<Duration> duration = Optional.empty();

  private final List<TimingScopeListener> scopeListeners = Lists.newArrayList();

  @Nullable private TimingScope parentScope;

  private final List<TimingScope> children = Lists.newArrayList();

  public TimingScope(String name, EventType eventType) {
    this.name = name;
    this.eventType = eventType;
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    startTime = Instant.now();
    parentScope = context.getParentScope(this);

    if (parentScope != null) {
      parentScope.children.add(this);
    }
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    if (context.isCancelled()) {
      duration = Optional.of(Duration.ZERO);
      return;
    }

    Duration elapsedTime = Duration.between(startTime, Instant.now());
    duration = Optional.of(elapsedTime);

    if (!scopeListeners.isEmpty()) {
      ImmutableList<TimedEvent> output = collectTimedEvents();
      scopeListeners.forEach(l -> l.onScopeEnd(output, elapsedTime));
    }
    if (parentScope == null && elapsedTime.toMillis() > 100) {
      logTimingData();
    }
  }

  private TimedEvent getTimedEvent() {
    return new TimedEvent(name, eventType, duration.orElse(Duration.ZERO), children.isEmpty());
  }

  /** Adds a TimingScope listener to its list of listeners. */
  @CanIgnoreReturnValue
  public TimingScope addScopeListener(TimingScopeListener listener) {
    scopeListeners.add(listener);
    return this;
  }

  private ImmutableList<TimedEvent> collectTimedEvents() {
    List<TimedEvent> output = new ArrayList<>();
    collectTimedEvents(this, output);
    return ImmutableList.copyOf(output);
  }

  /** Recursively walk the scopes tree, collecting timing info. */
  private static void collectTimedEvents(TimingScope timingScope, List<TimedEvent> data) {
    data.add(timingScope.getTimedEvent());
    for (TimingScope child : timingScope.children) {
      collectTimedEvents(child, data);
    }
  }

  private void logTimingData() {
    logger.info("==== TIMING REPORT ====");
    logTimingData(this, /* depth= */ 0);
  }

  private static void logTimingData(TimingScope timingScope, int depth) {
    String selfString = "";

    // Self time trivially 100% if no children
    if (timingScope.children.size() > 0) {
      // Calculate self time as <my duration> - <sum child duration>
      Duration selfTime = timingScope.getDuration();
      for (TimingScope child : timingScope.children) {
        selfTime = selfTime.minus(child.getDuration());
      }
      if (selfTime.toMillis() > 100) {
        selfString = String.format(" (%s)", durationStr(selfTime));
      }
    }

    // TODO(brendandouglas): combine repeated child events with the same name (e.g. sharded builds)
    logger.info(
        String.format(
            "%s%s: %s%s",
            getIndentation(depth),
            timingScope.name,
            durationStr(timingScope.getDuration()),
            selfString));

    for (TimingScope child : timingScope.children) {
      logTimingData(child, depth + 1);
    }
  }

  private Duration getDuration() {
    if (duration.isPresent()) {
      return duration.get();
    }
    // Could happen if a TimingScope outlives the root context, so the actual duration is not yet
    // known.
    logger.warn(String.format("Duration not computed for TimingScope %s", name));
    return Duration.ZERO;
  }

  private static String durationStr(Duration duration) {
    long timeMillis = duration.toMillis();
    return timeMillis >= 1000
        ? String.format("%.1fs", timeMillis / 1000d)
        : String.format("%sms", timeMillis);
  }

  private static String getIndentation(int depth) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; ++i) {
      sb.append("    ");
    }
    return sb.toString();
  }
}
