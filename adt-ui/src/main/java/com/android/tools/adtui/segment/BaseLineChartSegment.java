package com.android.tools.adtui.segment;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.intellij.ui.components.JBLayeredPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * This class can be used for line charts with a left axis or with both left and right.
 * In former case rightAxisFormatter should be passed to {@link #BaseLineChartSegment(String, Range, BaseAxisFormatter, BaseAxisFormatter)}
 * as null value, indicating that the chart has a left axis only.
 */
public abstract class BaseLineChartSegment extends BaseSegment {
  @NotNull
  protected Range mLeftAxisRange;

  @Nullable
  protected Range mRightAxisRange;

  @NotNull
  private AxisComponent mLeftAxis;

  @Nullable
  private AxisComponent mRightAxis;

  @NotNull
  private final BaseAxisFormatter mLeftAxisFormatter;

  @Nullable
  private final BaseAxisFormatter mRightAxisFormatter;

  @NotNull
  private GridComponent mGrid;

  @NotNull
  private LineChart mLineChart;

  @NotNull
  private LegendComponent mLegendComponent;

  /**
   * @param rightAxisFormatter if it is null, chart will have a left axis only
   */
  public BaseLineChartSegment(@NotNull String name,
                              @NotNull Range xRange,
                              @NotNull BaseAxisFormatter leftAxisFormatter,
                              @Nullable BaseAxisFormatter rightAxisFormatter) {
    super(name, xRange);
    mLeftAxisFormatter = leftAxisFormatter;
    mRightAxisFormatter = rightAxisFormatter;
    mLeftAxisRange = new Range();
    if (mRightAxisFormatter != null) {
      mRightAxisRange = new Range();
    }
  }

  @Override
  public void createComponentsList(@NotNull List<Animatable> animatables) {
    // left axis
    mLeftAxis = new AxisComponent(mLeftAxisRange, mLeftAxisRange, "",
                                  AxisComponent.AxisOrientation.LEFT, 0, 0, true,
                                  mLeftAxisFormatter);
    // right axis
    if (mRightAxisRange != null) {
      mRightAxis = new AxisComponent(mRightAxisRange, mRightAxisRange, "",
                                     AxisComponent.AxisOrientation.RIGHT, 0, 0, true,
                                     mRightAxisFormatter);
    }
    //TODO Associate the grid with both AxisComponents.
    mLineChart = new LineChart();
    mGrid = new GridComponent();
    mGrid.addAxis(mLeftAxis);
    populateSeriesData(mLineChart);

    List<LegendRenderData> legendRenderDataList = new ArrayList<>();
    for (RangedContinuousSeries series : mLineChart.getRangedContinuousSeries()) {
      LineConfig lineConfig = mLineChart.getLineConfig(series);
      Color color = lineConfig.getColor();
      LegendRenderData.IconType iconType = lineConfig.isFilled() ? LegendRenderData.IconType.BOX : LegendRenderData.IconType.LINE;
      LegendRenderData renderData = new LegendRenderData(iconType, color, series);
      legendRenderDataList.add(renderData);
    }
    mLegendComponent = new LegendComponent(legendRenderDataList, LegendComponent.Orientation.HORIZONTAL, 100, MemoryAxisFormatter.DEFAULT);

    // Note: the order below is important as some components depend on
    // others to be updated first. e.g. the ranges need to be updated before the axes.
    // The comment on each line highlights why the component needs to be in that position.
    animatables.add(mLineChart); // Set y's interpolation values.
    if (mRightAxisRange != null) {
      animatables.add(mRightAxisRange); // Interpolate y1.
    }
    animatables.add(mLeftAxisRange); // Interpolate y2.
    if (mRightAxis != null) {
      animatables.add(mRightAxis); // Read ranges.
    }
    animatables.add(mLeftAxis); // Read ranges.
    animatables.add(mLegendComponent);
    animatables.add(mGrid); // No-op.
  }

  public abstract void populateSeriesData(@NotNull LineChart lineChart);

  @Override
  public void registerComponents(@NotNull List<AnimatedComponent> components) {
    components.add(mLineChart);
    if (mRightAxis != null) {
      components.add(mRightAxis);
    }
    components.add(mLeftAxis);
    components.add(mGrid);
    components.add(mLegendComponent);
  }

  @Override
  protected void setLeftContent(@NotNull JPanel panel) {
    panel.add(mLeftAxis, BorderLayout.CENTER);
  }

  @Override
  protected void setTopCenterContent(@NotNull JPanel panel) {
    panel.add(mLegendComponent, BorderLayout.EAST);
  }

  @Override
  protected void setCenterContent(@NotNull JPanel panel) {
    JBLayeredPane layeredPane = new JBLayeredPane();
    layeredPane.add(mLineChart);
    layeredPane.add(mGrid);
    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        JLayeredPane host = (JLayeredPane)e.getComponent();
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
  protected void setRightContent(@NotNull JPanel panel) {
    if (mRightAxis != null) {
      panel.add(mRightAxis, BorderLayout.CENTER);
      setRightSpacerVisible(true);
    } else {
      setRightSpacerVisible(false);
    }
  }

}
