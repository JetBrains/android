/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.vmtrace.treemodel;

import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.perflib.vmtrace.MethodProfileData;
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public abstract class AbstractProfileDataNode implements StatsNode {
  @Nullable
  protected abstract MethodProfileData getProfileData();

  @Nullable
  protected Object renderColumn(StatsTableColumn column, ThreadInfo thread, VmTraceData traceData, ClockType clock) {
    MethodProfileData profileData = getProfileData();
    switch (column) {
      case NAME:
        return null;
      case INCLUSIVE_TIME:
        long inclusiveTimeNs = profileData == null ? 0 : profileData.getInclusiveTime(thread, clock, TimeUnit.NANOSECONDS);
        return getValueAndPercentagePair(thread, traceData, clock, inclusiveTimeNs);
      case EXCLUSIVE_TIME:
        inclusiveTimeNs = profileData == null ? 0 : profileData.getExclusiveTime(thread, clock, TimeUnit.NANOSECONDS);
        return getValueAndPercentagePair(thread, traceData, clock, inclusiveTimeNs);
      case INVOCATION_COUNT:
        return profileData == null ? 0 : profileData.getInvocationCount(thread);
      default:
        return "";
    }
  }

  private static Pair<Long,Double> getValueAndPercentagePair(ThreadInfo thread, VmTraceData traceData, ClockType clock, long timeNanos) {
    double percent = traceData.getDurationPercentage(timeNanos, thread, clock) / 100.0d;
    long timeMicros = TimeUnit.MICROSECONDS.convert(timeNanos, TimeUnit.NANOSECONDS);
    return Pair.create(timeMicros, percent);
  }
}
