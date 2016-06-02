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
package com.android.tools.idea.editors.gfxtrace;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.breakpoints.FilteredRequestor;
import com.intellij.ui.classFilter.ClassFilter;
import com.sun.jdi.event.LocatableEvent;

public class ClassFilterRequestor implements FilteredRequestor {

  private ClassFilter[] myClassFilters;

  public ClassFilterRequestor(String className) {
    myClassFilters = new ClassFilter[]{
      new ClassFilter(className)
    };
  }

  @Override
  public String getSuspendPolicy() {
    return DebuggerSettings.SUSPEND_ALL;
  }

  @Override
  public boolean isInstanceFiltersEnabled() {
    return false;
  }

  @Override
  public InstanceFilter[] getInstanceFilters() {
    return new InstanceFilter[0];
  }

  @Override
  public boolean isCountFilterEnabled() {
    return false;
  }

  @Override
  public int getCountFilter() {
    return 0;
  }

  @Override
  public boolean isClassFiltersEnabled() {
    return true;
  }

  @Override
  public ClassFilter[] getClassFilters() {
    return myClassFilters;
  }

  @Override
  public ClassFilter[] getClassExclusionFilters() {
    return new ClassFilter[0];
  }

  @Override
  public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event)
    throws EventProcessingException {
    return false;
  }
}
