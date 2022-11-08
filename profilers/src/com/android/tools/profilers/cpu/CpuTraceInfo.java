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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.ConfigurableDurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Trace;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

/**
 * This class contains meta information about a trace file, such as traceId, capture start and capture end.
 */
public class CpuTraceInfo implements ConfigurableDurationData {
  @NotNull private final Cpu.CpuTraceInfo myInfo;
  @NotNull private final Range myRange;

  public CpuTraceInfo(Cpu.CpuTraceInfo traceInfo) {
    myInfo = traceInfo;
    myRange = new Range(TimeUnit.NANOSECONDS.toMicros(traceInfo.getFromTimestamp()),
                        TimeUnit.NANOSECONDS.toMicros(traceInfo.getToTimestamp()));
  }

  @NotNull
  public Cpu.CpuTraceInfo getTraceInfo() {
    return myInfo;
  }

  @Override
  public long getDurationUs() {
    return myInfo.getToTimestamp() == -1 ? Long.MAX_VALUE : (long)myRange.getLength();
  }

  @NotNull
  public Range getRange() {
    return myRange;
  }

  public long getTraceId() {
    return myInfo.getTraceId();
  }

  @NotNull
  public TraceType getTraceType() {
    return TraceType.from(myInfo.getConfiguration());
  }

  @NotNull
  public Trace.TraceMode getTraceMode() {
    return myInfo.getConfiguration().getUserOptions().getTraceMode();
  }

  @NotNull
  public Trace.TraceInitiationType getInitiationType() {
    return myInfo.getConfiguration().getInitiationType();
  }

  @Override
  public boolean getSelectableWhenMaxDuration() {
    return false;
  }

  @Override
  public boolean canSelectPartialRange() {
    return true;
  }
}
