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

package com.android.tools.idea.monitor.ui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.visual.VisualTest;
import com.android.tools.idea.monitor.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.ui.memory.view.MemorySegment;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MemoryProfilerVisualTest extends VisualTest {

  private static final String MEMORY_PROFILER_NAME = "Memory Profiler";

  private SeriesDataStore mDataStore;

  private MemorySegment mSegment;

  @Override
  protected void initialize() {
    mDataStore = new VisualTestSeriesDataStore();
    super.initialize();
  }

  @Override
  protected void reset() {
    if (mDataStore != null) {
      mDataStore.reset();
    }

    super.reset();
  }

  @Override
  public String getName() {
    return MEMORY_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    long startTimeMs = System.currentTimeMillis();
    Range xRange = new Range();
    AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(xRange, startTimeMs);
    mSegment = new MemorySegment(xRange, mDataStore);
    List<Animatable> animatables = new ArrayList<>();
    animatables.add(animatedTimeRange);
    animatables.add(xRange);
    mSegment.createComponentsList(animatables);

    return animatables;
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new BorderLayout());
    mSegment.initializeComponents();
    mSegment.toggleView(true);
    panel.add(mSegment, BorderLayout.CENTER);
  }
}
