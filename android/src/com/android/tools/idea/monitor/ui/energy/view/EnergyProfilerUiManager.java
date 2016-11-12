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
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.Poller;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.datastore.SeriesDataType;
import com.android.tools.idea.monitor.ui.BaseProfilerUiManager;
import com.android.tools.idea.monitor.ui.BaseSegment;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.energy.model.EnergyPoller;
import com.google.common.collect.Sets;
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

  // Whether or not the energy component usage graph is showing instantaneous power usage or total power usage.
  // The energy data adapter is able to represent it's stored energy data in two ways: instantaneous power usage
  // and total power usage.  We use this variable to keep track which one it's currently displaying.
  private boolean myIsDisplayingInstantaneousEnergyUsage = true;

  public EnergyProfilerUiManager(@NotNull Range timeCurrentRangeUs, @NotNull Choreographer choreographer,
                                 @NotNull SeriesDataStore datastore, @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    super(timeCurrentRangeUs, choreographer, datastore, eventDispatcher);
  }

  @Nullable
  @Override
  public Set<Poller> createPollers(int pid) {
    myEnergyPoller = new EnergyPoller(myDataStore, pid, myIsDisplayingInstantaneousEnergyUsage);
    return Sets.newHashSet(myEnergyPoller);
  }

  public void toggleDisplayInstantaneousEnergyUsage() {
    myIsDisplayingInstantaneousEnergyUsage = !myIsDisplayingInstantaneousEnergyUsage;
    for (SeriesDataType type : EnergyPoller.ENERGY_DATA_TYPES) {
      myEnergyPoller.getEnergyAdapter(type).setReturnInstantaneousData(myIsDisplayingInstantaneousEnergyUsage);
    }
  }

  @Override
  public void setupExtendedOverviewUi(@NotNull JPanel toolbar, @NotNull JPanel overviewPanel) {
    super.setupExtendedOverviewUi(toolbar, overviewPanel);

    myToggleDeltaButton = new JButton(AndroidIcons.Ddms.SysInfo);
    myToggleDeltaButton.addActionListener(e -> {
      toggleDisplayInstantaneousEnergyUsage();
    });
    toolbar.add(myToggleDeltaButton, HorizontalLayout.LEFT);
  }

  @NotNull
  @Override
  protected BaseSegment createOverviewSegment(@NotNull Range timeCurrentRangeUs,
                                              @NotNull SeriesDataStore dataStore,
                                              @NotNull EventDispatcher<ProfilerEventListener> eventDispatcher) {
    return new EnergySegment(timeCurrentRangeUs, dataStore, eventDispatcher);
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
