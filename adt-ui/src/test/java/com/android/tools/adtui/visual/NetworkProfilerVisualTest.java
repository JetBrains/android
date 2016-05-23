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

package com.android.tools.adtui.visual;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.SeriesDataStore;
import com.android.tools.adtui.segment.NetworkSegment;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

public class NetworkProfilerVisualTest extends VisualTest {

  private static final String NETWORK_PROFILER_NAME = "Network Profiler";

  private NetworkSegment mSegment;


  private Range mSharedRange;

  private long mStartTimeMs;

  private AnimatedTimeRange mAnimatedTimeRange;

  @Override
  protected void registerComponents(List<AnimatedComponent> components) {
    mSegment.registerComponents(components);

  }

  @Override
  public String getName() {
    return NETWORK_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    mStartTimeMs = System.currentTimeMillis();
    mSharedRange = new Range(0, 0);
    mAnimatedTimeRange = new AnimatedTimeRange(mSharedRange, mStartTimeMs);
    mSegment = new NetworkSegment(mSharedRange, mDataStore);
    List<Animatable> animatables = new ArrayList<>();
    animatables.add(mAnimatedTimeRange);
    mSegment.createComponentsList(animatables);
    return animatables;
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weighty = .5;
    constraints.weightx = 1;
    constraints.gridy = 0;
    mSegment.initializeComponents();
    panel.add(mSegment, constraints);
  }
}
