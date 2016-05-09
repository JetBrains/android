package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.GridComponent;
import com.android.tools.adtui.LineChart;
import com.android.tools.adtui.MemoryAxisDomain;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

public class NetworkSegment extends SegmentBase {

  private static final String SEGMENT_NAME = "Network";

  @NonNull
  private Range mTimeGlobalRange;

  @NonNull
  private AxisComponent mConnectionsAxis;

  @NonNull
  private AxisComponent mBandwidthAxis;

  @NonNull
  private GridComponent mGrid;

  @NonNull
  private LineChart mLineChart;

  @NonNull
  List<RangedContinuousSeries> mData;

  //TODO Add labels for series data.

  public NetworkSegment(@NonNull Range scopedRange, @NonNull List<RangedContinuousSeries> incommingData) {
    super(SEGMENT_NAME, scopedRange);
    mData = incommingData;
  }

  @Override
  public void createComponentsList(@NonNull List<Animatable> animatables) {
    mTimeGlobalRange = new Range(0, 0);

    // right memory data + axis
    Range yRange1Animatable = new Range(0, 0);
    mConnectionsAxis = new AxisComponent(yRange1Animatable, yRange1Animatable, "MEMORY1",
                                         AxisComponent.AxisOrientation.RIGHT, 0, 0, true,
                                         MemoryAxisDomain.DEFAULT);
    RangedContinuousSeries ranged1 = new RangedContinuousSeries(mScopedRange, yRange1Animatable);
    mData.add(ranged1);

    // left memory data + axis
    Range yRange2Animatable = new Range(0, 0);
    mBandwidthAxis = new AxisComponent(yRange2Animatable, yRange2Animatable, "MEMORY2",
                                       AxisComponent.AxisOrientation.LEFT, 0, 0, true,
                                       MemoryAxisDomain.DEFAULT);
    RangedContinuousSeries ranged2 = new RangedContinuousSeries(mScopedRange, yRange2Animatable);
    mData.add(ranged2);

    mGrid = new GridComponent();
    mGrid.addAxis(mConnectionsAxis);
    mLineChart = new LineChart(getName(), mData);

    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    animatables.add(mLineChart); // Set y's interpolation values.
    animatables.add(yRange1Animatable); // Interpolate y1.
    animatables.add(yRange2Animatable); // Interpolate y2.
    animatables.add(mConnectionsAxis); // Read ranges.
    animatables.add(mBandwidthAxis); // Read ranges.
    animatables.add(mGrid); // No-op.
    animatables.add(mTimeGlobalRange); // Reset flags.
  }

  @Override
  public void registerComponents(@NonNull List<AnimatedComponent> components) {
    components.add(mLineChart);
    components.add(mConnectionsAxis);
    components.add(mBandwidthAxis);
    components.add(mGrid);
  }

  @Override
  protected void setLeftContent(@NonNull JPanel panel) {
    panel.add(mBandwidthAxis, BorderLayout.CENTER);
  }

  @Override
  protected void setCenterContent(@NonNull JPanel panel) {
    JLayeredPane layeredPane = new JLayeredPane();
    layeredPane.add(mLineChart);
    layeredPane.add(mGrid);
    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane) e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
            c.setBounds(0, 0, dim.width, dim.height);
          }
        }
      }
    });
    panel.add(layeredPane, BorderLayout.CENTER);
  }

  @Override
  protected void setRightContent(@NonNull JPanel panel) {
    panel.add(mConnectionsAxis, BorderLayout.CENTER);
  }
}
