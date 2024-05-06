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

import com.android.tools.adtui.model.TooltipModel;
import com.android.tools.profiler.proto.Common;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class StudioMonitorStage extends StreamingStage {

  @NotNull
  private final List<ProfilerMonitor> myMonitors;

  public StudioMonitorStage(@NotNull StudioProfilers profiler) {
    super(profiler);
    myMonitors = new LinkedList<>();
  }

  @Override
  public void enter() {
    logEnterStage();
    // Clear the selection
    getTimeline().getSelectionRange().clear();

    Common.Session session = getStudioProfilers().getSession();
    if (session != Common.Session.getDefaultInstance()) {
      SupportLevel supportLevel = getStudioProfilers().getSelectedSessionSupportLevel();
      for (StudioProfiler profiler : getStudioProfilers().getProfilers()) {
        if (supportLevel.isMonitorSupported(profiler)) {
          myMonitors.add(profiler.newMonitor());
        }
      }
    }
    myMonitors.forEach(ProfilerMonitor::enter);

    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());
  }

  @Override
  public void exit() {
    myMonitors.forEach(ProfilerMonitor::exit);
    myMonitors.clear();
  }

  @NotNull
  public List<ProfilerMonitor> getMonitors() {
    return myMonitors;
  }

  @Override
  public void setTooltip(TooltipModel tooltip) {
    super.setTooltip(tooltip);
    myMonitors.forEach(monitor -> monitor
      .setFocus(getTooltip() instanceof ProfilerMonitorTooltip && ((ProfilerMonitorTooltip)getTooltip()).getMonitor() == monitor));
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.OVERVIEW_STAGE;
  }
}
