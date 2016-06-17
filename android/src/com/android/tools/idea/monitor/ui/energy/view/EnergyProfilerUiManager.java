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
package com.android.tools.idea.monitor.ui.energy.view;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.Range;
import com.android.tools.idea.monitor.datastore.Poller;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.ui.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.energy.model.EnergyPoller;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.EventDispatcher;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

/*
 * TODO maybe we should use an event dispatcher instead of manually hooking up expansion callbacks?
 */
public class EnergyProfilerUiManager extends BaseProfilerUiManager {
  private JButton myToggleDeltaButton;
  private EnergyPoller myEnergyPoller;

  public EnergyProfilerUiManager(@NotNull Range xRange, @NotNull Choreographer choreographer,
                                 @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(xRange, choreographer, datastore, eventDispatcher);
  }

  @Nullable
  @Override
  public Set<Poller> createPollers(int pid) {
    myEnergyPoller = new EnergyPoller(myDataStore, pid);
    return Sets.newHashSet(myEnergyPoller);
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(toolbar, overviewPanel);

    myToggleDeltaButton = new JButton(AndroidIcons.Ddms.SysInfo);
    myToggleDeltaButton.addActionListener(e -> {
      myEnergyPoller.toggleDataDisplay();
    });
    toolbar.add(myToggleDeltaButton, HorizontalLayout.LEFT);
  }

  @NotNull
  @Override
  protected BaseSegment createOverviewSegment(@NotNull Range xRange,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new EnergySegment(xRange, dataStore, eventDispatcher);
  }

  @Override
  public void resetProfiler(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel, @NotNull JPanel detailPanel) {
    super.resetProfiler(toolbar, overviewPanel, detailPanel);

    if (myToggleDeltaButton != null) {
      toolbar.remove(myToggleDeltaButton);
      myToggleDeltaButton = null;
    }
  }

}
