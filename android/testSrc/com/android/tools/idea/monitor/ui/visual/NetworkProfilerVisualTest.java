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
import com.android.tools.adtui.RangeScrollbar;
import com.android.tools.adtui.visualtests.VisualTest;
import com.android.tools.adtui.model.Range;
import com.android.tools.datastore.SeriesDataStore;
import com.android.tools.idea.monitor.tool.ProfilerEventListener;
import com.android.tools.idea.monitor.ui.network.model.HttpData;
import com.android.tools.idea.monitor.ui.network.model.NetworkCaptureModel;
import com.android.tools.idea.monitor.ui.network.view.NetworkCaptureSegment;
import com.android.tools.idea.monitor.ui.network.view.NetworkDetailedView;
import com.android.tools.idea.monitor.ui.network.view.NetworkRadioSegment;
import com.android.tools.idea.monitor.ui.network.view.NetworkSegment;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NetworkProfilerVisualTest extends VisualTest {
  private static final String NETWORK_PROFILER_NAME = "Network Profiler";
  private static int CAPTURE_DATA_SIZE = 20;

  private SeriesDataStore myDataStore;

  private NetworkSegment mySegment;

  private NetworkRadioSegment myRadioSegment;

  private NetworkCaptureSegment myCaptureSegment;

  private NetworkDetailedView myDetailedView;

  private List<HttpData> myCaptureData;

  @Override
  protected void initialize() {
    myDataStore = new VisualTestSeriesDataStore();
    super.initialize();
  }

  @Override
  protected void reset() {
    if (myDataStore != null) {
      myDataStore.reset();
    }
    super.reset();
  }

  @Override
  public String getName() {
    return NETWORK_PROFILER_NAME;
  }

  @Override
  protected List<Animatable> createComponentsList() {
    long startTimeUs = myDataStore.getLatestTimeUs();
    Range timeCurrentRangeUs = new Range(startTimeUs - RangeScrollbar.DEFAULT_VIEW_LENGTH_US, startTimeUs);
    AnimatedTimeRange animatedTimeRange = new AnimatedTimeRange(timeCurrentRangeUs, 0);

    EventDispatcher<ProfilerEventListener> dummyDispatcher = EventDispatcher.create(ProfilerEventListener.class);
    mySegment = new NetworkSegment(timeCurrentRangeUs, myDataStore, dummyDispatcher);
    myDetailedView = new NetworkDetailedView();

    myDetailedView.setVisible(false);

    generateNetworkCaptureData(startTimeUs);
    myCaptureSegment =
      new NetworkCaptureSegment(timeCurrentRangeUs, new TestNetworkCaptureModel(), httpData -> myDetailedView.setVisible(true),
                                dummyDispatcher);

    myRadioSegment = new NetworkRadioSegment(timeCurrentRangeUs, myDataStore, dummyDispatcher);

    List<Animatable> animatables = new ArrayList<>();
    animatables.add(animatedTimeRange);
    mySegment.createComponentsList(animatables);
    myCaptureSegment.createComponentsList(animatables);
    myRadioSegment.createComponentsList(animatables);
    animatables.add(myCaptureSegment);
    return animatables;
  }

  @Override
  protected void populateUi(@NonNull JPanel panel) {
    panel.setLayout(new GridBagLayout());
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;


    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weighty = 0;
    constraints.weightx = 1;
    myRadioSegment.initializeComponents();
    myRadioSegment.setPreferredSize(new Dimension(0, 40));
    panel.add(myRadioSegment, constraints);

    constraints.weighty = .5;
    constraints.gridx = 0;
    constraints.gridy = 1;
    mySegment.initializeComponents();
    mySegment.toggleView(true);
    panel.add(mySegment, constraints);


    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.weighty = 1;
    myCaptureSegment.initializeComponents();
    panel.add(myCaptureSegment, constraints);

    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.gridheight = 3;
    constraints.weightx = 0;
    constraints.weighty = 0;
    panel.add(myDetailedView, constraints);
  }

  private void generateNetworkCaptureData(long startTimeUs) {
    long endTimeUs = startTimeUs + 20000000;
    myCaptureData = new ArrayList<>();
    for (int i = 0; i < CAPTURE_DATA_SIZE; ++i) {
      long start = random(startTimeUs, endTimeUs);
      long download = random(start, endTimeUs);
      long end = random(download, endTimeUs);
      HttpData data = new HttpData();
      data.setStartTimeUs(start);
      data.setDownloadingTimeUs(download);
      data.setEndTimeUs(end);
      data.setUrl("www.fake.url/" + i);
      myCaptureData.add(data);
    }

  }

  private static long random(long min, long max) {
    return min + (int)(Math.random() * ((max - min) + 1));
  }

  /**
   * Mock class which queries fake test data.
   */
  private class TestNetworkCaptureModel implements NetworkCaptureModel {
    @NotNull
    @Override
    public List<HttpData> getData(@NotNull Range timeCurrentRangeUs) {
      List<HttpData> dataList = new ArrayList<>();
      long viewStartUs = (long)timeCurrentRangeUs.getMin();
      long viewEndUs = (long)timeCurrentRangeUs.getMax();

      for (HttpData data: myCaptureData) {
        if (Math.max(viewStartUs, data.getStartTimeUs()) <= Math.min(viewEndUs, data.getEndTimeUs())) {
          dataList.add(data);
        }
      }
      return dataList;
    }
  }
}
