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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.*;
import com.android.tools.adtui.chart.linechart.DurationDataRenderer;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.chart.linechart.OverlayComponent;
import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.adtui.common.formatter.BaseAxisFormatter;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.common.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.DurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.android.tools.profilers.ProfilerLayout.*;

public class MemoryProfilerStageView extends StageView<MemoryProfilerStage> {

  private static final BaseAxisFormatter MEMORY_AXIS_FORMATTER = new MemoryAxisFormatter(1, 5, 5);
  private static final BaseAxisFormatter OBJECT_COUNT_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 5, "");

  @NotNull private final Icon myGcIcon = UIUtil.isUnderDarcula() ?
                                         IconLoader.findIcon("/icons/garbage-event_dark.png", MemoryProfilerStageView.class) :
                                         IconLoader.findIcon("/icons/garbage-event.png", MemoryProfilerStageView.class);

  @NotNull private final MemoryClassView myClassView = new MemoryClassView(getStage());
  @NotNull private final MemoryInstanceView myInstanceView = new MemoryInstanceView(getStage());

  @NotNull private Splitter myMainSplitter = new Splitter(false);
  @NotNull private Splitter myChartClassesSplitter = new Splitter(true);
  @NotNull private JButton myAllocationButton;

  public MemoryProfilerStageView(@NotNull MemoryProfilerStage stage) {
    super(stage);

    myChartClassesSplitter.setFirstComponent(buildMonitorUi());
    myMainSplitter.setFirstComponent(myChartClassesSplitter);
    myMainSplitter.setProportion(0.6f);
    getComponent().add(myMainSplitter, BorderLayout.CENTER);
    captureObjectChanged();

    myAllocationButton = new JButton("Record");
    myAllocationButton.addActionListener(e -> getStage().trackAllocations(!getStage().isTrackingAllocations()));

    getStage().getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication()::invokeLater)
      .onChange(MemoryProfilerAspect.CURRENT_CAPTURE, this::captureObjectChanged)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, this::classObjectChanged)
      .onChange(MemoryProfilerAspect.LEGACY_ALLOCATION, this::legacyAllocationChanged);

    legacyAllocationChanged();
  }

  @Override
  public JComponent getToolbar() {

    JButton backButton = new JButton();
    backButton.addActionListener(action -> getStage().getStudioProfilers().setMonitoringStage());
    backButton.setIcon(AllIcons.Actions.Back);

    JToolBar toolBar = new JToolBar();
    toolBar.setFloatable(false);
    toolBar.add(backButton);
    toolBar.add(myAllocationButton);

    JButton triggerHeapDumpButton = new JButton("Heap Dump");
    triggerHeapDumpButton.addActionListener(e -> getStage().requestHeapDump());
    toolBar.add(triggerHeapDumpButton);

    return toolBar;
  }

  private void legacyAllocationChanged() {
    //TODO enable/disable hprof/allocation if they cannot be performed
    myAllocationButton.setText(getStage().isTrackingAllocations() ? "Stop" : "Record");
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    ProfilerTimeline timeline = profilers.getTimeline();
    Range viewRange = getTimeline().getViewRange();
    Range dataRange = getTimeline().getDataRange();

    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.MONITOR_BACKGROUND);

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(getChoreographer(), timeline, panel);
    getChoreographer().register(sb);
    panel.add(sb, new TabularLayout.Constraint(3, 0));

    AxisComponent timeAxis = buildTimeAxis(profilers);
    getChoreographer().register(timeAxis);
    panel.add(timeAxis, new TabularLayout.Constraint(2, 0));

    EventMonitor events = new EventMonitor(profilers);
    EventMonitorView eventsView = new EventMonitorView(events);
    JComponent eventsComponent = eventsView.initialize(getChoreographer());
    panel.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    MemoryMonitor monitor = new MemoryMonitor(profilers);
    JPanel monitorPanel = new JBPanel(new GridBagLayout());
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(monitor.getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    Range leftYRange = new Range(0, 0);
    Range rightYRange = new Range(0, 0);

    RangedContinuousSeries javaSeries = new RangedContinuousSeries("Java", viewRange, leftYRange, monitor.getJavaMemory());
    RangedContinuousSeries nativeSeries = new RangedContinuousSeries("Native", viewRange, leftYRange, monitor.getNativeMemory());
    RangedContinuousSeries graphcisSeries = new RangedContinuousSeries("Graphics", viewRange, leftYRange, monitor.getGraphicsMemory());
    RangedContinuousSeries stackSeries = new RangedContinuousSeries("Stack", viewRange, leftYRange, monitor.getStackMemory());
    RangedContinuousSeries codeSeries = new RangedContinuousSeries("Code", viewRange, leftYRange, monitor.getCodeMemory());
    RangedContinuousSeries otherSeries = new RangedContinuousSeries("Others", viewRange, leftYRange, monitor.getOthersMemory());
    RangedContinuousSeries totalSeries = new RangedContinuousSeries("Total", viewRange, leftYRange, monitor.getTotalMemory());
    RangedContinuousSeries objectSeries = new RangedContinuousSeries("Allocated", viewRange, rightYRange, monitor.getObjectCount());

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final LineChart lineChart = new LineChart();
    lineChart.addLine(javaSeries, new LineConfig(ProfilerColors.MEMORY_JAVA).setFilled(true).setStacked(true));
    lineChart.addLine(nativeSeries, new LineConfig(ProfilerColors.MEMORY_NATIVE).setFilled(true).setStacked(true));
    lineChart.addLine(graphcisSeries, new LineConfig(ProfilerColors.MEMORY_GRAPHCIS).setFilled(true).setStacked(true));
    lineChart.addLine(stackSeries, new LineConfig(ProfilerColors.MEMORY_STACK).setFilled(true).setStacked(true));
    lineChart.addLine(codeSeries, new LineConfig(ProfilerColors.MEMORY_CODE).setFilled(true).setStacked(true));
    lineChart.addLine(otherSeries, new LineConfig(ProfilerColors.MEMORY_OTHERS).setFilled(true).setStacked(true));
    lineChart.addLine(totalSeries, new LineConfig(ProfilerColors.MEMORY_TOTAL).setFilled(true));
    lineChart.addLine(objectSeries, new LineConfig(ProfilerColors.MEMORY_OBJECTS).setStroke(LineConfig.DEFAULT_DASH_STROKE));

    // TODO set proper colors / icons
    DurationDataRenderer<HeapDumpDurationData> heapDumpRenderer =
      new DurationDataRenderer.Builder<>(new RangedSeries<>(viewRange, getStage().getHeapDumpSampleDurations()), Color.BLACK)
        .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
        .setStroke(new BasicStroke(2))
        .setIsBlocking(true)
        .setLabelProvider(
          data -> String.format("Dump (%s)", data.getDuration() == DurationData.UNSPECIFIED_DURATION ? "in progress" :
                                             TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDuration(), true)))
        .setClickHander(data -> getStage().setFocusedHeapDump(data.getDumpInfo()))
        .build();
    DurationDataRenderer<AllocationsDurationData> allocationRenderer =
      new DurationDataRenderer.Builder<>(new RangedSeries<>(viewRange, getStage().getAllocationInfosDurations()), Color.LIGHT_GRAY)
        .setLabelColors(Color.DARK_GRAY, Color.GRAY, Color.lightGray, Color.WHITE)
        .setStroke(new BasicStroke(2))
        .setLabelProvider(data -> String
          .format("Allocation Record (%s)", data.getDuration() == DurationData.UNSPECIFIED_DURATION ? "in progress" :
                                            TimeAxisFormatter.DEFAULT.getFormattedString(viewRange.getLength(), data.getDuration(), true)))
        .setClickHander(data -> getStage().setAllocationsTimeRange(data.getStartTimeNs(), data.getEndTimeNs()))
        .build();
    DurationDataRenderer<GcDurationData> gcRenderer =
      new DurationDataRenderer.Builder<>(new RangedSeries<>(viewRange, monitor.getGcCount()), Color.BLACK)
        .setIcon(myGcIcon)
        .setAttachLineSeries(objectSeries)
        .build();

    lineChart.addCustomRenderer(heapDumpRenderer);
    lineChart.addCustomRenderer(allocationRenderer);
    lineChart.addCustomRenderer(gcRenderer);

    SelectionComponent selection = new SelectionComponent(timeline.getSelectionRange(), timeline.getViewRange());
    final JPanel overlayPanel = new JBPanel(new BorderLayout());
    overlayPanel.setOpaque(false);
    overlayPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    final OverlayComponent overlay = new OverlayComponent(selection);
    overlay.addDurationDataRenderer(heapDumpRenderer);
    overlay.addDurationDataRenderer(allocationRenderer);
    overlay.addDurationDataRenderer(gcRenderer);
    overlayPanel.add(overlay, BorderLayout.CENTER);

    getChoreographer().register(lineChart);
    getChoreographer().register(heapDumpRenderer);
    getChoreographer().register(allocationRenderer);
    getChoreographer().register(gcRenderer);
    getChoreographer().register(overlay);
    getChoreographer().register(selection);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    AxisComponent.Builder leftBuilder = new AxisComponent.Builder(leftYRange, MEMORY_AXIS_FORMATTER,
                                                                  AxisComponent.AxisOrientation.RIGHT)
      .showAxisLine(false)
      .showMax(true)
      .showUnitAtMax(true)
      .clampToMajorTicks(true)
      .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
      .setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent leftAxis = leftBuilder.build();
    getChoreographer().register(leftAxis);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    AxisComponent.Builder rightBuilder = new AxisComponent.Builder(rightYRange, OBJECT_COUNT_AXIS_FORMATTER,
                                                                   AxisComponent.AxisOrientation.LEFT)
      .showAxisLine(false)
      .showMax(true)
      .showUnitAtMax(true)
      .clampToMajorTicks(true)
      .setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH)
      .setMargins(0, Y_AXIS_TOP_MARGIN);
    final AxisComponent rightAxis = rightBuilder.build();
    getChoreographer().register(rightAxis);
    axisPanel.add(rightAxis, BorderLayout.EAST);

    final LegendComponent legend = new LegendComponent(LegendComponent.Orientation.HORIZONTAL, LEGEND_UPDATE_FREQUENCY_MS);
    ArrayList<LegendRenderData> legendData = new ArrayList<>();
    legendData.add(lineChart.createLegendRenderData(javaSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(nativeSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(graphcisSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(stackSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(codeSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(otherSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(totalSeries, MEMORY_AXIS_FORMATTER, dataRange));
    legendData.add(lineChart.createLegendRenderData(objectSeries, OBJECT_COUNT_AXIS_FORMATTER, dataRange));
    legend.setLegendData(legendData);
    getChoreographer().register(legend);

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    monitorPanel.add(overlayPanel, GBC_FULL);
    monitorPanel.add(selection, GBC_FULL);
    monitorPanel.add(legendPanel, GBC_FULL);
    monitorPanel.add(axisPanel, GBC_FULL);
    monitorPanel.add(lineChartPanel, GBC_FULL);

    layout.setRowSizing(1, "*"); // Give monitor as much space as possible
    panel.add(monitorPanel, new TabularLayout.Constraint(1, 0));

    return panel;
  }

  private void captureObjectChanged() {
    CaptureObject captureObject = getStage().getSelectedCaptureObject();
    if (myClassView.getCurrentCapture() != captureObject) {
      myClassView.reset();
      myChartClassesSplitter.setSecondComponent(null);
      myMainSplitter.setSecondComponent(null);
      if (captureObject != null) {
        // TODO don't rebuild the component, but update it
        myChartClassesSplitter.setSecondComponent(myClassView.buildComponent(captureObject));
      }
    }
  }

  private void classObjectChanged() {
    ClassObject classObject = getStage().getSelectedClass();
    if (myInstanceView.getCurrentClassObject() != classObject) {
      myInstanceView.reset();
      myMainSplitter.setSecondComponent(null);
      if (classObject != null) {
        // TODO don't rebuild the component, but update it
        myMainSplitter.setSecondComponent(myInstanceView.buildComponent(classObject));
      }
    }

    // TODO setup instance detail view.
  }

  static class AttributeColumn {
    private final String myName;
    private final Supplier<ColoredTreeCellRenderer> myRendererSuppier;
    private final int myHeaderAlignment;
    private final int myPreferredWidth;
    private final SortOrder mySortOrder;
    private final Comparator<MemoryObjectTreeNode> myComparator;

    public AttributeColumn(@NotNull String name,
                           @NotNull Supplier<ColoredTreeCellRenderer> rendererSupplier,
                           int headerAlignment,
                           int preferredWidth,
                           @NotNull SortOrder sortOrder,
                           @NotNull Comparator<MemoryObjectTreeNode> comparator) {
      myName = name;
      myRendererSuppier = rendererSupplier;
      myHeaderAlignment = headerAlignment;
      myPreferredWidth = preferredWidth;
      mySortOrder = sortOrder;
      myComparator = comparator;
    }

    @NotNull
    public ColumnTreeBuilder.ColumnBuilder getBuilder() {
      return new ColumnTreeBuilder.ColumnBuilder()
        .setName(myName)
        .setRenderer(myRendererSuppier.get())
        .setHeaderAlignment(myHeaderAlignment)
        .setPreferredWidth(myPreferredWidth)
        .setInitialOrder(mySortOrder)
        .setComparator(myComparator);
    }
  }

  static class DetailColumnRenderer extends ColoredTreeCellRenderer {
    private final Function<MemoryObjectTreeNode, String> myTextGetter;
    private final Function<MemoryObjectTreeNode, Icon> myIconGetter;
    private final int myAlignment;

    public DetailColumnRenderer(@NotNull Function<MemoryObjectTreeNode, String> textGetter,
                                @NotNull Function<MemoryObjectTreeNode, Icon> iconGetter,
                                int alignment) {
      myTextGetter = textGetter;
      myIconGetter = iconGetter;
      myAlignment = alignment;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof MemoryObjectTreeNode) {
        append(myTextGetter.apply((MemoryObjectTreeNode)value));
        Icon icon = myIconGetter.apply((MemoryObjectTreeNode)value);
        if (icon != null) {
          setIcon(icon);
        }
        setTextAlign(myAlignment);
      }
    }
  }
}
