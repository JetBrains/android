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

import static com.android.tools.adtui.common.AdtUiUtils.GBC_FULL;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AnimatedTimeRange;
import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.DefaultDurationData;
import com.android.tools.adtui.model.DurationDataModel;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.adtui.model.updater.Updatable;
import com.intellij.ui.JBColor;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Stroke;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.jetbrains.annotations.NotNull;

public class LineChartVisualTest extends VisualTest {

  private final JLabel mClickDisplayLabel = new JLabel();

  private LineChart mLineChart;

  private RangeSelectionComponent myRangeSelectionComponent;

  private OverlayComponent myOverlayComponent;

  private List<RangedContinuousSeries> mRangedData;

  private List<DefaultDataSeries<Long>> mData;

  private AnimatedTimeRange mAnimatedTimeRange;

  private DefaultDataSeries<DefaultDurationData> mDurationData1;

  private DefaultDataSeries<DefaultDurationData> mDurationData2;

  private DefaultDataSeries<DefaultDurationData> mDurationData3;

  private DurationDataRenderer<DefaultDurationData> mDurationRendererBlocking;

  private DurationDataRenderer<DefaultDurationData> mDurationRendererAttached;

  @Override
  protected List<Updatable> createModelList() {
    mRangedData = new ArrayList<>();
    mData = new ArrayList<>();

    long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    Range timeGlobalRangeUs = new Range(nowUs, nowUs + TimeUnit.SECONDS.toMicros(60));
    LineChartModel model = new LineChartModel(newDirectExecutorService());
    mLineChart = new LineChart(model);
    mLineChart.setBackground(JBColor.background());
    mAnimatedTimeRange = new AnimatedTimeRange(timeGlobalRangeUs, 0);

    List<Updatable> componentsList = new ArrayList<>();

    RangeSelectionModel selection = new RangeSelectionModel(new Range(0, 0), timeGlobalRangeUs);
    myRangeSelectionComponent = new RangeSelectionComponent(selection);
    myOverlayComponent = new OverlayComponent(myRangeSelectionComponent);

    // Add the scene components to the list
    componentsList.add(mAnimatedTimeRange);
    componentsList.add(model);

    Range yRange = new Range(0.0, 100.0);
    for (int i = 0; i < 4; i++) {
      if (i % 2 == 0) {
        yRange = new Range(0.0, 100.0);
      }
      DefaultDataSeries<Long> series = new DefaultDataSeries<>();
      RangedContinuousSeries ranged =
        new RangedContinuousSeries("Widgets #" + i, timeGlobalRangeUs, yRange, series);
      mRangedData.add(ranged);
      mData.add(series);
    }
    model.addAll(mRangedData);

    mDurationData1 = new DefaultDataSeries<>();
    mDurationData2 = new DefaultDataSeries<>();
    mDurationData3 = new DefaultDataSeries<>();
    RangedSeries<DefaultDurationData> series1 = new RangedSeries<>(timeGlobalRangeUs, mDurationData1);
    RangedSeries<DefaultDurationData> series2 = new RangedSeries<>(timeGlobalRangeUs, mDurationData2);
    RangedSeries<DefaultDurationData> series3 = new RangedSeries<>(timeGlobalRangeUs, mDurationData3);
    DurationDataModel<DefaultDurationData> model1 = new DurationDataModel<>(series1);
    mDurationRendererBlocking = new DurationDataRenderer.Builder<>(model1, Color.WHITE)
      .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
      .setIcon(UIManager.getIcon("Tree.leafIcon"))
      .setLabelProvider(durationdata -> "Blocking")
      .setClickHandler(durationData -> mClickDisplayLabel.setText(durationData.toString())).build();

    DurationDataModel<DefaultDurationData> model2 = new DurationDataModel<>(series2);
    model2.setAttachedSeries(mRangedData.get(0), Interpolatable.SegmentInterpolator);
    model2.setAttachPredicate(duration -> {
      List<SeriesData<DefaultDurationData>> series = series3.getSeries();
      for (SeriesData<DefaultDurationData> data : series) {
        if (data.x <= duration.x && (data.value.getDurationUs() == Long.MAX_VALUE || (data.x + data.value.getDurationUs()) >= duration.x)) {
          return false;
        }
      }
      return  true;
    });
    model2.setRenderSeriesPredicate((data, series) -> !series.getName().equals("Widgets #0"));
    mDurationRendererAttached = new DurationDataRenderer.Builder<>(model2, Color.BLACK)
      .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
      .setIcon(UIManager.getIcon("Tree.leafIcon"))
      .setLabelProvider(durationdata -> "Attached")
      .setStroke(new BasicStroke(1))
      .setClickHandler(durationData -> mClickDisplayLabel.setText(durationData.toString())).build();
    mLineChart.addCustomRenderer(mDurationRendererBlocking);
    mLineChart.addCustomRenderer(mDurationRendererAttached);
    myOverlayComponent.addDurationDataRenderer(mDurationRendererBlocking);
    myOverlayComponent.addDurationDataRenderer(mDurationRendererAttached);
    componentsList.add(model1);
    componentsList.add(model2);

    return componentsList;
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Collections.singletonList(mLineChart);
  }

  @Override
  public String getName() {
    return "LineChart";
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    JPanel layered = new JPanel(new GridBagLayout());
    JPanel controls = VisualTest.createControlledPane(panel, layered);
    mLineChart.setBorder(BorderFactory.createLineBorder(AdtUiUtils.DEFAULT_BORDER_COLOR));
    layered.setBackground(JBColor.background());
    layered.add(myOverlayComponent, GBC_FULL);
    layered.add(myRangeSelectionComponent, GBC_FULL);
    layered.add(mLineChart, GBC_FULL);

    final AtomicInteger variance = new AtomicInteger(10);
    final AtomicInteger delay = new AtomicInteger(100);
    Thread updateDataThread = new Thread() {
      @Override
      public void run() {
        super.run();
        try {
          while (true) {
            int v = variance.get();
            long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
            for (DefaultDataSeries<Long> series : mData) {
              List<SeriesData<Long>> data = series.getAllData();
              long last = data.isEmpty() ? 0 : data.get(data.size() - 1).value;
              float delta = ((float)Math.random() - 0.45f) * v;
              // Make sure not to add negative numbers.
              long current = Math.max(last + (long)delta, 0);
              series.add(nowUs, current);
            }
            Thread.sleep(delay.get());
          }
        }
        catch (InterruptedException e) {
        }
      }
    };

    updateDataThread.start();
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
    controls.add(VisualTest.createVariableSlider("Variance", 0, 50, new VisualTests.Value() {
      @Override
      public void set(int v) {
        variance.set(v);
      }

      @Override
      public int get() {
        return variance.get();
      }
    }));
    controls.add(VisualTest.createVariableSlider("Top padding", 0, 200, new VisualTests.ValueAdapter() {
      @Override
      protected void onSet(int v) {
        mLineChart.setTopPadding(v);
      }
    }));

    controls.add(VisualTest.createVariableSlider("Line width", 1, 10, new VisualTests.Value() {
      @Override
      public void set(int v) {
        Stroke stroke = new BasicStroke(v);
        for (int i = 0; i < mRangedData.size(); i += 2) {
          RangedContinuousSeries series = mRangedData.get(i);
          mLineChart.getLineConfig(series).setStroke(stroke);
        }
      }

      @Override
      public int get() {
        // Returns the stroke width of the first line, in case there is one, or a default (1) value
        RangedContinuousSeries firstSeries = mRangedData.get(0);
        Stroke firstLineStroke = mLineChart.getLineConfig(firstSeries).getStroke();
        return firstLineStroke instanceof BasicStroke ? (int)((BasicStroke)firstLineStroke).getLineWidth() : 1;
      }
    }));
    controls.add(VisualTest.createVariableSlider("Bucket interval", 0, 1000, new VisualTests.Value() {
      @Override
      public void set(int v) {
        for (int i = 0; i < mRangedData.size(); i+=2) {
          mLineChart.getLineConfig(mRangedData.get(i)).setDataBucketInterval(v * 1000);
        }
      }

      @Override
      public int get() {
        return (int) mLineChart.getLineConfig(mRangedData.get(0)).getDataBucketInterval();
      }
    }));
    controls.add(VisualTest.createCheckbox("Shift xRange Min", itemEvent ->
      mAnimatedTimeRange.setShift(itemEvent.getStateChange() == ItemEvent.SELECTED)));
    controls.add(VisualTest.createCheckbox("Stepped chart", itemEvent -> {
      boolean isStepped = itemEvent.getStateChange() == ItemEvent.SELECTED;
      // Make only some lines stepped
      for (int i = 0; i < mRangedData.size(); i += 2) {
        RangedContinuousSeries series = mRangedData.get(i);
        mLineChart.getLineConfig(series).setStepped(isStepped);
      }
    }));
    controls.add(VisualTest.createCheckbox("Dashed lines", itemEvent -> {
      Stroke stroke = itemEvent.getStateChange() == ItemEvent.SELECTED ? LineConfig.DEFAULT_DASH_STROKE : LineConfig.DEFAULT_LINE_STROKE;
      // Dash only some lines
      for (int i = 0; i < mRangedData.size(); i += 2) {
        RangedContinuousSeries series = mRangedData.get(i);
        mLineChart.getLineConfig(series).setStroke(stroke);
      }
    }));
    controls.add(VisualTest.createCheckbox("Adjust Dash", itemEvent -> {
      boolean isAdjustDash = itemEvent.getStateChange() == ItemEvent.SELECTED;
      for (int i = 0; i < mRangedData.size(); i += 2) {
        RangedContinuousSeries series = mRangedData.get(i);
        mLineChart.getLineConfig(series).setAdjustDash(isAdjustDash);
      }
    }));
    controls.add(VisualTest.createCheckbox("Filled lines", itemEvent -> {
      boolean isFilled = itemEvent.getStateChange() == ItemEvent.SELECTED;
      // Fill only some lines
      for (int i = 0; i < mRangedData.size(); i += 2) {
        RangedContinuousSeries series = mRangedData.get(i);
        mLineChart.getLineConfig(series).setFilled(isFilled);
      }
    }));
    controls.add(VisualTest.createCheckbox("Stacked lines", itemEvent -> {
      boolean isStacked = itemEvent.getStateChange() == ItemEvent.SELECTED;
      // Stack only some lines
      for (int i = 0; i < mRangedData.size(); i += 2) {
        RangedContinuousSeries series = mRangedData.get(i);
        mLineChart.getLineConfig(series).setStacked(isStacked);
      }
    }));

    JButton tapButton = VisualTest.createButton("Generate Duration1 (Hold)");
    tapButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Starts a new test event and give it a max duration.
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        DefaultDurationData newEvent = new DefaultDurationData(Long.MAX_VALUE);
        mDurationData1.add(nowUs, newEvent);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        // Wraps up the latest event by assigning it a duration value relative to where it was started.
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        List<SeriesData<DefaultDurationData>> allEvents = mDurationData1.getAllData();
        SeriesData<DefaultDurationData> lastEvent = allEvents.get(allEvents.size() - 1);
        lastEvent.value.setDurationUs(nowUs - lastEvent.x);
      }
    });
    controls.add(tapButton);

    tapButton = VisualTest.createButton("Generate Duration2 (Hold)");
    tapButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Starts a new test event and give it a max duration.
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        DefaultDurationData newEvent = new DefaultDurationData(Long.MAX_VALUE);
        mDurationData2.add(nowUs, newEvent);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        // Wraps up the latest event by assigning it a duration value relative to where it was started.
        long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
        List<SeriesData<DefaultDurationData>> allEvents = mDurationData2.getAllData();
        SeriesData<DefaultDurationData> lastEvent = allEvents.get(allEvents.size() - 1);
        lastEvent.value.setDurationUs(nowUs - lastEvent.x);
      }
    });
    controls.add(tapButton);

    JCheckBox checkbox = VisualTest.createCheckbox("Toggle Duration3 (unattach Duration2 Data)", new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          // Starts a new test event and give it a max duration.
          long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
          DefaultDurationData newEvent = new DefaultDurationData(Long.MAX_VALUE);
          mDurationData3.add(nowUs, newEvent);
        } else {
          // Wraps up the latest event by assigning it a duration value relative to where it was started.
          long nowUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
          List<SeriesData<DefaultDurationData>> allEvents = mDurationData3.getAllData();
          SeriesData<DefaultDurationData> lastEvent = allEvents.get(allEvents.size() - 1);
          lastEvent.value.setDurationUs(nowUs - lastEvent.x);
        }
      }
    });
    controls.add(checkbox);

    controls.add(mClickDisplayLabel);

    controls.add(
      new Box.Filler(new Dimension(0, 0), new Dimension(300, Integer.MAX_VALUE),
                     new Dimension(300, Integer.MAX_VALUE)));
  }
}
