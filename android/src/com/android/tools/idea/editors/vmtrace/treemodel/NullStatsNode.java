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
import com.android.tools.perflib.vmtrace.ThreadInfo;
import com.android.tools.perflib.vmtrace.VmTraceData;
import org.jetbrains.annotations.Nullable;

/** Tree Node displayed when the trace file hasn't been parsed yet. */
public class NullStatsNode implements StatsNode {
  @Override
  public int getChildCount() {
    return 0;
  }

  @Override
  public Object getChild(int index) {
    return null;
  }

  @Override
  public boolean isLeaf() {
    return true;
  }

  @Nullable
  @Override
  public Object getValueAt(int column, ThreadInfo thread, VmTraceData vmTraceData, ClockType clock) {
    return null;
  }

  @Override
  public void setSortColumn(StatsTableColumn sortByColumn, boolean sortAscending) {
  }

  @Override
  public String toString() {
    return "Loading...";
  }
}
