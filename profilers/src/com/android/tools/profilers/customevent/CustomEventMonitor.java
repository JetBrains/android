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
package com.android.tools.profilers.customevent;

import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTooltip;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

public class CustomEventMonitor extends ProfilerMonitor {

  public CustomEventMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);
  }

  @Override
  @NotNull
  public String getName() {
    return "CUSTOM EVENTS";
  }

  @Override
  public ProfilerTooltip buildTooltip() {
    return new CustomEventMonitorTooltip(this);
  }

  @Override
  public void exit() {
  }

  @Override
  public void enter() {
  }

  @Override
  public void expand() {
    myProfilers.setStage(new CustomEventProfilerStage(myProfilers));
  }
}
