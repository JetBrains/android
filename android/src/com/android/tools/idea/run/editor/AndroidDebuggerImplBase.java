/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.annotations.concurrency.GuardedBy;
import com.android.sdklib.AndroidVersion;
import com.google.common.collect.Sets;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class AndroidDebuggerImplBase<S extends AndroidDebuggerState> implements AndroidDebugger<S> {

  @GuardedBy("this")
  private Set<XBreakpointType<?, ?>> mySupportedBreakpointTypes;
  private final Set<Class<? extends XBreakpointType<?, ?>>> breakpointTypeClasses;

  protected AndroidDebuggerImplBase(Set<Class<? extends XBreakpointType<?, ?>>> breakpointTypeClasses) {
    this.breakpointTypeClasses = breakpointTypeClasses;
  }

  @Override
  @NotNull
  public synchronized Set<XBreakpointType<?, ?>> getSupportedBreakpointTypes(@NotNull AndroidVersion version) {
    if (mySupportedBreakpointTypes == null) {
      XDebuggerUtil debuggerUtil = XDebuggerUtil.getInstance();
      mySupportedBreakpointTypes = Sets.newHashSet();
      for (Class bpTypeCls: breakpointTypeClasses) {
        mySupportedBreakpointTypes.add(debuggerUtil.findBreakpointType(bpTypeCls));
      }
    }
    return mySupportedBreakpointTypes;
  }

  @Override
  @NotNull
  public String getAmStartOptions(@NotNull AndroidVersion version) {
    return "";
  }
}
