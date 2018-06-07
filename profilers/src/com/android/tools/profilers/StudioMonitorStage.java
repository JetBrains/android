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

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

import static com.android.tools.profilers.StudioProfilers.INVALID_PROCESS_ID;

public class StudioMonitorStage extends Stage {

  @NotNull
  private List<ProfilerMonitor> myMonitors;

  @NotNull
  private final Updatable myUpdatable;

  public StudioMonitorStage(@NotNull StudioProfilers profiler) {
    super(profiler);
    myMonitors = new LinkedList<>();
    myUpdatable = elapsedNs -> getStudioProfilers().getTimeline().getTooltipRange().changed(Range.Aspect.RANGE);
  }

  @Override
  public void enter() {
    // Clear the selection
    getStudioProfilers().getTimeline().getSelectionRange().clear();

    myMonitors.clear();
    int processId = getStudioProfilers().getProcessId();
    if (processId != INVALID_PROCESS_ID) {
      for (StudioProfiler profiler : getStudioProfilers().getProfilers()) {
        myMonitors.add(profiler.newMonitor());
      }
    }
    myMonitors.forEach(ProfilerMonitor::enter);

    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getClass());
    getStudioProfilers().getUpdater().register(myUpdatable);
  }

  @Override
  public void exit() {
    getStudioProfilers().getUpdater().unregister(myUpdatable);
    myMonitors.forEach(ProfilerMonitor::exit);
  }

  @NotNull
  public List<ProfilerMonitor> getMonitors() {
    return myMonitors;
  }

  @Override
  public void setTooltip(ProfilerTooltip tooltip) {
    super.setTooltip(tooltip);
    myMonitors.forEach(monitor -> monitor
      .setFocus(getTooltip() instanceof ProfilerMonitorTooltip && ((ProfilerMonitorTooltip)getTooltip()).getMonitor() == monitor));
  }
}
