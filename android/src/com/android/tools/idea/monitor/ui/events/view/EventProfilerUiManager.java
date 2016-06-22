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
package com.android.tools.idea.monitor.ui.events.view;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public final class EventProfilerUiManager extends BaseProfilerUiManager {

  // The event monitor takes out half the space as the expanded profiler in L2/L3 view.
  private static final int EVENT_MONITOR_MAX_HEIGHT = JBUI.scale(Short.MAX_VALUE / 2);

  // TODO: Replace with actual icons.
  private static final Icon[] ICONS = {
    AndroidIcons.ToolWindows.Warning,
    AndroidIcons.ToolWindows.Warning,
    AndroidIcons.ToolWindows.Warning
  };

  public EventProfilerUiManager(@NotNull Range xRange, @NotNull Choreographer choreographer,
                                @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(xRange, choreographer, datastore, eventDispatcher);
  }

  @Override
  public Set<Poller> createPollers(int pid) {
    return null;
  }

  /**
   *
   * @param overviewPanel
   */
  @Override
  public void setupOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    myOverviewSegment = createOverviewSegment(myXRange, myDataStore, myEventDispatcher);
    setupAndRegisterSegment(myOverviewSegment, DEFAULT_MONITOR_MIN_HEIGHT, DEFAULT_MONITOR_PREFERRED_HEIGHT, EVENT_MONITOR_MAX_HEIGHT);
    overviewPanel.add(myOverviewSegment);
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range xRange,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new EventSegment(xRange, dataStore, ICONS, eventDispatcher);
  }
}
