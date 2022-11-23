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

package com.android.tools.adtui.visualtests;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.statechart.StateChart;
import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.updater.Updatable;
import com.intellij.ui.JBColor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StateChartVisualTest extends VisualTest {

  private StateChartModel<MockFruitState> myNetworkModel;
  private StateChartModel<MockStrengthState> myRadioModel;

  public enum MockFruitState {
    NONE,
    APPLE,
    GRAPE,
    ORANGE,
    BANANA
  }

  public enum MockStrengthState {
    NONE,
    WEAK,
    STRONG
  }

  private static final int AXIS_SIZE = 100;

  private Range mTimeGlobalRangeUs;

  private AnimatedTimeRange mAnimatedTimeRange;

  private StateChart<MockFruitState> mNetworkStatusChart;

  private StateChart<MockStrengthState> mRadioStateChart;

  @NotNull
  private List<DefaultDataSeries<MockFruitState>> mNetworkDataEntries = new ArrayList<>();

  @NotNull
  private List<DefaultDataSeries<MockStrengthState>> mRadioDataEntries = new ArrayList<>();

  private static EnumMap<MockFruitState, Color> getFruitStateColor() {
    EnumMap<MockFruitState, Color> colors = new EnumMap<>(MockFruitState.class);
    colors.put(MockFruitState.NONE, new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0)));
    colors.put(MockFruitState.APPLE, JBColor.RED);
    colors.put(MockFruitState.GRAPE, JBColor.MAGENTA);
    colors.put(MockFruitState.ORANGE, JBColor.ORANGE);
    colors.put(MockFruitState.BANANA, JBColor.YELLOW);
    return colors;
  }

  private static EnumMap<MockStrengthState, Color> getStrengthColor() {
    EnumMap<MockStrengthState, Color> colors = new EnumMap<>(MockStrengthState.class);
    colors.put(MockStrengthState.NONE, new JBColor(new Color(0, 0, 0, 0), new Color(0, 0, 0, 0)));
    colors.put(MockStrengthState.WEAK, JBColor.CYAN);
    colors.put(MockStrengthState.STRONG, JBColor.BLUE);
    return colors;
  }

  @Override
  protected List<Updatable> createModelList() {
    long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    mTimeGlobalRangeUs = new Range(nowUs, nowUs + TimeUnit.SECONDS.toMicros(60));
    mAnimatedTimeRange = new AnimatedTimeRange(mTimeGlobalRangeUs, 0);
    DefaultDataSeries<MockFruitState> networkSeries = new DefaultDataSeries<>();
    DefaultDataSeries<MockStrengthState> radioSeries = new DefaultDataSeries<>();

    RangedSeries<MockFruitState> networkData = new RangedSeries<>(mTimeGlobalRangeUs, networkSeries);
    RangedSeries<MockStrengthState> radioData = new RangedSeries<>(mTimeGlobalRangeUs, radioSeries);

    myNetworkModel = new StateChartModel<>();
    mNetworkStatusChart = new StateChart<>(myNetworkModel, getFruitStateColor());
    myNetworkModel.addSeries(networkData);
    mNetworkDataEntries.add(networkSeries);

    myRadioModel = new StateChartModel<>();
    mRadioStateChart = new StateChart<>(myRadioModel, getStrengthColor());
    myRadioModel.addSeries(radioData);
    mRadioDataEntries.add(radioSeries);

    return Collections.singletonList(mAnimatedTimeRange);
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(mNetworkStatusChart, mRadioStateChart);
  }

  @Override
  public String getName() {
    return "StateChart";
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    panel.setLayout(new BorderLayout());

    JLayeredPane timelinePane = createMockTimeline();
    panel.add(timelinePane, BorderLayout.CENTER);

    final JPanel controls = new JPanel();
    LayoutManager manager = new BoxLayout(controls, BoxLayout.Y_AXIS);
    controls.setLayout(manager);
    panel.add(controls, BorderLayout.WEST);

    final AtomicInteger networkVariance = new AtomicInteger(MockFruitState.values().length);
    final AtomicInteger radioVariance = new AtomicInteger(MockStrengthState.values().length);
    final AtomicInteger delay = new AtomicInteger(100);

    //TODO Refactor this to come from the DataStore, in the mean time we will leak a thread every time reset is called on this test.
    Thread updateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          // Store off the last state to simulate the same preprocessing the DataStore does on each series.
          IntList lastNetworkData = new IntArrayList();
          IntList lastRadioVariance = new IntArrayList();
          while (true) {
            long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());

            int v = networkVariance.get();
            boolean networkChanged = false;
            for (int i = 0; i < mNetworkDataEntries.size();i++) {
              DefaultDataSeries<MockFruitState> series = mNetworkDataEntries.get(i);
              if (Math.random() > 0.5f) {
                int index = (int)(Math.random() * v);
                if(lastNetworkData.size() <= i) {
                  lastNetworkData.add(-1);
                }
                if (lastNetworkData.getInt(i) != index) {
                  series.add(nowUs, MockFruitState.values()[index]);
                  networkChanged = true;
                  lastNetworkData.set(i, index);
                }
              }
            }
            if (networkChanged) {
              myNetworkModel.changed(StateChartModel.Aspect.MODEL_CHANGED);
            }

            v = radioVariance.get();
            boolean radioChanged = false;
            for (int i = 0; i < mRadioDataEntries.size();i++) {
              DefaultDataSeries<MockStrengthState> series = mRadioDataEntries.get(i);
              if (Math.random() > 0.5f) {
                int index = (int)(Math.random() * v);
                if(lastRadioVariance.size() <= i) {
                  lastRadioVariance.add(-1);
                }
                if (lastRadioVariance.getInt(i) != index) {
                  series.add(nowUs, MockStrengthState.values()[index]);
                  radioChanged = true;
                  lastRadioVariance.set(i, index);
                }
              }
            }
            if (radioChanged) {
              myRadioModel.changed(StateChartModel.Aspect.MODEL_CHANGED);
            }

            Thread.sleep(delay.get());
          }
        }
        catch (InterruptedException e) {
          // do nothing
        }
      }
    };
    updateDataThread.start();

    controls.add(VisualTest.createVariableSlider("Gap", 0, 100, new VisualTests.Value() {
      @Override
      public void set(int v) {
        mNetworkStatusChart.setHeightGap(v / 100f);
        mRadioStateChart.setHeightGap(v / 100f);
      }

      @Override
      public int get() {
        return -1; // unused
      }
    }));
    controls.add(VisualTest.createVariableSlider("Delay", 10, 5000, new VisualTests.Value() {
      @Override
      public void set(int v) {
        delay.set(v);
      }

      @Override
      public int get() {
        return delay.get();
      }
    }));
    controls.add(VisualTest.createVariableSlider("Fruit Variance", 1, MockFruitState.values().length, new VisualTests.Value() {
      @Override
      public void set(int v) {
        networkVariance.set(v);
      }

      @Override
      public int get() {
        return networkVariance.get();
      }
    }));
    controls.add(VisualTest.createVariableSlider("Strength Variance", 1, MockStrengthState.values().length, new VisualTests.Value() {
      @Override
      public void set(int v) {
        radioVariance.set(v);
      }

      @Override
      public int get() {
        return radioVariance.get();
      }
    }));
    controls.add(VisualTest.createButton("Add Fruit Series", e -> {
      DefaultDataSeries<MockFruitState> networkSeries = new DefaultDataSeries<>();
      RangedSeries<MockFruitState> networkData = new RangedSeries(mTimeGlobalRangeUs, networkSeries);
      myNetworkModel.addSeries(networkData);
      mNetworkDataEntries.add(networkSeries);
    }));
    controls.add(VisualTest.createButton("Add Strength Series", e -> {
      DefaultDataSeries<MockStrengthState> radioSeries = new DefaultDataSeries<>();
      RangedSeries<MockStrengthState> radioData = new RangedSeries(mTimeGlobalRangeUs, radioSeries);
      myRadioModel.addSeries(radioData);
      mRadioDataEntries.add(radioSeries);
    }));
    controls.add(VisualTest.createCheckbox("Shift xRange Min",
                                           itemEvent -> mAnimatedTimeRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED)));
    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }

  private JLayeredPane createMockTimeline() {
    JLayeredPane timelinePane = new JLayeredPane();

    timelinePane.add(mNetworkStatusChart);
    timelinePane.add(mRadioStateChart);
    timelinePane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          int numChart = 0;
          for (Component c : host.getComponents()) {
            if (c instanceof AxisComponent) {
              AxisComponent axis = (AxisComponent)c;
              switch (axis.getOrientation()) {
                case LEFT:
                  axis.setBounds(0, 0, AXIS_SIZE, dim.height);
                  break;
                case BOTTOM:
                  axis.setBounds(0, dim.height - AXIS_SIZE, dim.width, AXIS_SIZE);
                  break;
                case RIGHT:
                  axis.setBounds(dim.width - AXIS_SIZE, 0, AXIS_SIZE, dim.height);
                  break;
                case TOP:
                  axis.setBounds(0, 0, dim.width, AXIS_SIZE);
                  break;
              }
            }
            else if (c instanceof StateChart) {
              int y = numChart % 2 == 0 ? AXIS_SIZE : dim.height - AXIS_SIZE * 2;
              c.setBounds(AXIS_SIZE, y, dim.width - AXIS_SIZE * 2, AXIS_SIZE);
              numChart++;
            }
          }
        }
      }
    });

    return timelinePane;
  }
}
