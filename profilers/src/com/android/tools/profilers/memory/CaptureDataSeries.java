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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.model.DataSeries;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

abstract class CaptureDataSeries<T extends CaptureObject> implements DataSeries<CaptureDurationData<T>> {
  @NotNull protected final MemoryServiceGrpc.MemoryServiceBlockingStub myClient;
  @Nullable protected final Common.Session mySession;
  protected final int myProcessId;
  @NotNull protected final RelativeTimeConverter myConverter;
  @NotNull protected final FeatureTracker myFeatureTracker;

  protected CaptureDataSeries(@NotNull MemoryServiceGrpc.MemoryServiceBlockingStub client,
                              @Nullable Common.Session session, int processId,
                              @NotNull RelativeTimeConverter converter, @NotNull FeatureTracker featureTracker) {
    myClient = client;
    myProcessId = processId;
    myConverter = converter;
    mySession = session;
    myFeatureTracker = featureTracker;
  }

  public static long getHostTime(long time) {
    return TimeUnit.NANOSECONDS.toMicros(time);
  }

  public static long getDurationUs(long startTimeNs, long endTimeNs) {
    return endTimeNs == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUnit.NANOSECONDS.toMicros(endTimeNs - startTimeNs);
  }
}
