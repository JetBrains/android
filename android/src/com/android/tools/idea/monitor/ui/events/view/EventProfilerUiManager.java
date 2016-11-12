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
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.google.common.collect.Sets;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public final class EventProfilerUiManager extends BaseProfilerUiManager {

  // The event monitor takes up constant space in different level views.
  private static final int EVENT_MONITOR_MIN_HEIGHT = JBUI.scale(55);

  // TODO: EventActionType enum should return its associated Icon so we don't need to keep them in sync across files.
  private static final Icon[] ICONS = {
    AndroidIcons.Profiler.Touch,
    AndroidIcons.Profiler.TouchHold,
    AndroidIcons.Profiler.DoubleTap,
    AndroidIcons.Profiler.Rotation
  };

  public EventProfilerUiManager(@NotNull Range timeCurrentRangeUs, @NotNull Choreographer choreographer,
                                @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(timeCurrentRangeUs, choreographer, datastore, eventDispatcher);
  }

  @Override
  public Set<Poller> createPollers(int pid) {
    //TODO: Remove this function from the interface when all pollers are migrated
    return Sets.newHashSet();
  }

  /**
   *
   * @param overviewPanel
   */
  @Override
  public void setupOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    myOverviewSegment = createOverviewSegment(myTimeCurrentRangeUs, myDataStore, myEventDispatcher);
    setupAndRegisterSegment(myOverviewSegment, EVENT_MONITOR_MIN_HEIGHT, EVENT_MONITOR_MIN_HEIGHT, EVENT_MONITOR_MIN_HEIGHT);
    overviewPanel.add(myOverviewSegment);
  }

  @Override
  @NotNull
  protected BaseSegment createOverviewSegment(@NotNull Range timeCurrentRangeUs,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new EventSegment(timeCurrentRangeUs, dataStore, ICONS, eventDispatcher);
  }
}
