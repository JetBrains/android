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

import com.android.tools.perflib.vmtrace.*;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class StatsByThreadNode extends AbstractProfileDataNode implements StatsNode {
  private final VmTraceData myTraceData;
  private final ThreadInfo myThread;
  private final List<MethodInfo> myMethods;

  public StatsByThreadNode(@NotNull VmTraceData traceData, @NotNull ThreadInfo thread) {
    myTraceData = traceData;
    myThread = thread;
    myMethods = getMethodsInThread(traceData, myThread);
  }

  @Override
  public synchronized int getChildCount() {
    return myMethods.size();
  }

  @NotNull
  private static List<MethodInfo> getMethodsInThread(final VmTraceData traceData, final ThreadInfo thread) {
    List<MethodInfo> results = Lists.newArrayList();

    for (MethodInfo info : traceData.getMethods().values()) {
        if (info.getProfileData().getInvocationCount(thread) > 0) {
          results.add(info);
        }
    }

    return results;
  }

  @Override
  public Object getChild(int index) {
    MethodInfo method = myMethods.get(index);
    return new StatsByMethodNode(method);
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  @Nullable
  @Override
  protected MethodProfileData getProfileData() {
    Call topLevelCall = myThread.getTopLevelCall();
    if (topLevelCall == null) {
      return null;
    }
    long methodId = topLevelCall.getMethodId();
    return myTraceData.getMethod(methodId).getProfileData();
  }

  @Nullable
  @Override
  public Object getValueAt(int columnIndex, ThreadInfo thread, VmTraceData vmTraceData, ClockType clock) {
    StatsTableColumn column = StatsTableColumn.fromColumnIndex(columnIndex);
    switch (column) {
      case NAME:
        return this;
      case INCLUSIVE_TIME:
        return renderColumn(column, thread, vmTraceData, clock);
      default:
        return null;
    }
  }

  @Override
  public String toString() {
    return "Thread " + myThread.getName();
  }
}
