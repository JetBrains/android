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
package com.android.tools.profilers;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.Timeline;
import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.profiler.proto.Common;
import org.jetbrains.annotations.NotNull;

/**
 * A component of the StudioMonitor stage. This represents the part of each profiler that is shown
 * when monitoring an app (aka L1).
 */
public abstract class ProfilerMonitor extends AspectModel<ProfilerMonitor.Aspect> {

  public enum Aspect {
    FOCUS,
    ENABLE
  }

  @NotNull
  protected final StudioProfilers myProfilers;
  private boolean myFocus;

  public ProfilerMonitor(@NotNull StudioProfilers profilers) {
    myProfilers = profilers;
  }

  @NotNull
  public final Timeline getTimeline() {
    return myProfilers.getTimeline();
  }

  public abstract void exit();

  public abstract void enter();

  @NotNull
  public StudioProfilers getProfilers() {
    return myProfilers;
  }

  public void expand() {
  }

  public boolean canExpand() {
    return true;
  }

  public abstract String getName();

  public abstract TooltipModel buildTooltip();

  public void setFocus(boolean focus) {
    if (focus != myFocus) {
      myFocus = focus;
      changed(Aspect.FOCUS);
    }
  }

  public boolean isFocused() {
    return myFocus;
  }

  public boolean isEnabled() {
    return true;
  }
}
