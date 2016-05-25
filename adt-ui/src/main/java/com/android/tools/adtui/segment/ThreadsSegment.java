package com.android.tools.adtui.segment;

import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.model.RangedDiscreteSeries;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.EnumMap;
import java.util.List;

public class ThreadsSegment extends BaseSegment {

  private static final String SEGMENT_NAME = "Threads";

  private StateChart<Thread.State> mThreadsStateChart;

  public ThreadsSegment(@NotNull Range timeRange) {
    super(SEGMENT_NAME, timeRange);
  }

  // TODO (amaurym): change it to use the proper colors
  private static EnumMap<Thread.State, Color> getThreadStateColor() {
    EnumMap<Thread.State, Color> colors = new EnumMap<>(Thread.State.class);
    colors.put(Thread.State.RUNNABLE, JBColor.GREEN);
    colors.put(Thread.State.TIMED_WAITING, JBColor.DARK_GRAY);
    colors.put(Thread.State.WAITING, JBColor.GRAY);
    colors.put(Thread.State.BLOCKED, JBColor.RED);
    return colors;
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    mThreadsStateChart = new StateChart<>(getThreadStateColor());
    animatables.add(mThreadsStateChart);
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    panel.add(mThreadsStateChart, BorderLayout.CENTER);
  }

  @Override
  public void registerComponents(@NotNull List<AnimatedComponent> components) {
    components.add(mThreadsStateChart);
  }

  public void addThreadStateSeries(RangedDiscreteSeries<Thread.State> series) {
    mThreadsStateChart.addSeries(series);
  }
}
