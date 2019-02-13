/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport.poller;

import com.android.tools.profiler.proto.Common;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Individual listeners that handle one event kind each. {@code TransportEventPoller} subscribers can register
 * multiple listeners to a single poller.
 */
public final class TransportEventListener {
  @NotNull private final Common.Event.Kind myEventKind;
  @NotNull private final Consumer<Common.Event> myCallback;
  @NotNull private Executor myExecutor;

  @Nullable private Supplier<Long> myStreamId;
  @Nullable private Supplier<Integer> myProcessId;
  @Nullable private Supplier<Long> myStartTime;
  @Nullable private Supplier<Long> myEndTime;

  @NotNull private Predicate<Common.Event> myFilter;


  private TransportEventListener(Builder builder) {
    myEventKind = builder.myEventKind;
    myCallback = builder.myCallback;
    myExecutor = builder.myExecutor;

    myStreamId = builder.myStreamId;
    myProcessId = builder.myProcessId;

    myStartTime = builder.myStartTime;
    myEndTime = builder.myEndTime;

    if (builder.myFilter != null) {
      myFilter = builder.myFilter;
    } else {
      myFilter = event -> true;
    }
  }

  @NotNull
  public Common.Event.Kind getEventKind() {
    return myEventKind;
  }

  @NotNull
  public Consumer<Common.Event> getCallback() {
    return myCallback;
  }

  @NotNull
  public Executor getExecutor() {
    return myExecutor;
  }

  @Nullable
  public Supplier<Long> getStreamId() {
    return myStreamId;
  }

  @Nullable
  public Supplier<Integer> getProcessId() {
    return myProcessId;
  }

  @Nullable
  public Supplier<Long> getStartTime() {
    return myStartTime;
  }

  @Nullable
  public Supplier<Long> getEndTime() {
    return myEndTime;
  }

  @NotNull
  public Predicate<Common.Event> getFilter() {
    return myFilter;
  }

  public static class Builder {
    @NotNull private Common.Event.Kind myEventKind;
    @NotNull private Consumer<Common.Event> myCallback;
    @NotNull private Executor myExecutor;

    @Nullable private Supplier<Long> myStreamId;
    @Nullable private Supplier<Integer> myProcessId;
    @Nullable private Supplier<Long> myStartTime;
    @Nullable private Supplier<Long> myEndTime;

    private Predicate<Common.Event> myFilter;

    // The callback is for each Event that we receive from the EventGroupResponse
    public Builder(@NotNull Common.Event.Kind eventKind, @NotNull Consumer<Common.Event> callback, @NotNull Executor executor) {
      myEventKind = eventKind;
      myCallback = callback;
      myExecutor = executor;
    }

    public Builder setStreamId(@Nullable Supplier<Long> streamId) {
      myStreamId = streamId;
      return this;
    }

    public Builder setProcessId(@Nullable Supplier<Integer> processId) {
      myProcessId = processId;
      return this;
    }

    // If this is not set, then the poller will keep track of its own myLastEventRequestTimestampNs
    public Builder setTimeRange(@Nullable Supplier<Long> startTime, @Nullable Supplier<Long> endTime) {
      myStartTime = startTime;
      myEndTime = endTime;
      return this;
    }

    // Used to determine whether the callback should be triggered, for example, if we
    // want to filter to only listen for the ProcessStarted events within the Process kind.
    public Builder setFilter(@Nullable Predicate<Common.Event> filter) {
      myFilter = filter;
      return this;
    }

    @NotNull
    public TransportEventListener build() {
      return new TransportEventListener(this);
    }
  }
}
