package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.*;
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.ReportingSeriesRenderer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

public abstract class BasicTwoAxisSegment extends SegmentBase {

  @NonNull
  private AxisComponent mRightAxis;

  @NonNull
  private AxisComponent mLeftAxis;

  @NonNull
  private GridComponent mGrid;

  @NonNull
  private LineChart mLineChart;

  @NonNull
  private LegendComponent mLegendComponent;

  @NonNull
  private final List<RangedContinuousSeries> mData;

  @NonNull
  private final BaseAxisDomain mLeftAxisDomain;

  @NonNull
  private final BaseAxisDomain mRightAxisDomain;

  public BasicTwoAxisSegment(@NonNull String name,
                             @NonNull Range scopedRange,
                             @NonNull List<RangedContinuousSeries> data,
                             @NonNull BaseAxisDomain leftAxisDomain,
                             @NonNull BaseAxisDomain rightAxisDomain) {
    super(name, scopedRange);
    mData = data;
    mLeftAxisDomain = leftAxisDomain;
    mRightAxisDomain = rightAxisDomain;
  }

  @Override
  public void createComponentsList(@NonNull List<Animatable> animatables) {

    // left axis
    Range leftAxisRange = new Range();
    mLeftAxis = new AxisComponent(leftAxisRange, leftAxisRange, "",
                                  AxisComponent.AxisOrientation.LEFT, 0, 0, true,
                                  mLeftAxisDomain);

    // right axis
    Range rightAxisRange = new Range();
    mRightAxis = new AxisComponent(rightAxisRange, rightAxisRange, "",
                                   AxisComponent.AxisOrientation.RIGHT, 0, 0, true,
                                   mRightAxisDomain);

    //TODO Associate the grid with both AxisComponents.
    mLineChart = new LineChart();
    mGrid = new GridComponent();
    mGrid.addAxis(mLeftAxis);

    //Call into our child types to populate the control data.
    populateSeriesData(mData, leftAxisRange, rightAxisRange);

    //TODO move this to populateSeriesData.
    for (RangedContinuousSeries series : mData) {
      mLineChart.addLine(series);
    }

    List<LegendRenderData> legendRenderData = createLegendData(mLineChart);
    mLegendComponent = new LegendComponent(legendRenderData, LegendComponent.Orientation.HORIZONTAL, 100, MemoryAxisDomain.DEFAULT);


    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    animatables.add(mLineChart); // Set y's interpolation values.
    animatables.add(rightAxisRange); // Interpolate y1.
    animatables.add(leftAxisRange); // Interpolate y2.
    animatables.add(mRightAxis); // Read ranges.
    animatables.add(mLeftAxis); // Read ranges.
    animatables.add(mLegendComponent);
    animatables.add(mGrid); // No-op.
  }

  public abstract List<LegendRenderData> createLegendData(@NonNull ReportingSeriesRenderer renderer);

  public abstract void populateSeriesData(@NonNull List<RangedContinuousSeries> data,
                                          @NonNull Range leftAxisRange,
                                          @NonNull Range rightAxisRange);

  @Override
  public void registerComponents(@NonNull List<AnimatedComponent> components) {
    components.add(mLineChart);
    components.add(mRightAxis);
    components.add(mLeftAxis);
    components.add(mGrid);
    components.add(mLegendComponent);
  }

  @Override
  protected void setLeftContent(@NonNull JPanel panel) {
    panel.add(mLeftAxis, BorderLayout.CENTER);
  }

  @Override
  protected void setCenterContent(@NonNull JPanel panel) {
    JLayeredPane layeredPane = new JLayeredPane();
    layeredPane.add(mLegendComponent);
    layeredPane.add(mLineChart);
    layeredPane.add(mGrid);
    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
        if (host != null) {
          Dimension dim = host.getSize();
          for (Component c : host.getComponents()) {
            if (c instanceof LegendComponent) {
              c.setBounds((int)(dim.width * .5), 0, dim.width, dim.height);
            }
            else {
              c.setBounds(0, 0, dim.width, dim.height);
            }
          }
        }
      }
    });
    panel.add(layeredPane, BorderLayout.CENTER);
  }

  @Override
  protected void setRightContent(@NonNull JPanel panel) {
    panel.add(mRightAxis, BorderLayout.CENTER);
  }
}
