// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS;
import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS;
import static com.android.tools.profilers.ProfilerLayout.MARKER_LENGTH;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_BORDER;
import static com.android.tools.profilers.ProfilerLayout.MONITOR_LABEL_PADDING;
import static com.android.tools.profilers.ProfilerLayout.PROFILER_LEGEND_RIGHT_PADDING;
import static com.android.tools.profilers.ProfilerLayout.PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER;
import static com.android.tools.profilers.ProfilerLayout.TOOLTIP_BORDER;
import static com.android.tools.profilers.ProfilerLayout.Y_AXIS_TOP_MARGIN;
import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.LegendComponent;
import com.android.tools.adtui.LegendConfig;
import com.android.tools.adtui.RangeSelectionComponent;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.TooltipComponent;
import com.android.tools.adtui.TooltipView;
import com.android.tools.adtui.chart.linechart.LineChart;
import com.android.tools.adtui.chart.linechart.LineConfig;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.RangeSelectionListener;
import com.android.tools.adtui.model.StreamingTimeline;
import com.android.tools.adtui.stdui.StreamingScrollbar;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerFonts;
import com.android.tools.profilers.ProfilerTooltipMouseAdapter;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtilities;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

public class EnergyProfilerStageView extends StageView<EnergyProfilerStage> {

  @NotNull private final JPanel myEventsPanel;
  @NotNull private final EnergyDetailsView myDetailsView;

  public EnergyProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull EnergyProfilerStage energyProfilerStage) {
    super(profilersView, energyProfilerStage);

    getTooltipBinder().bind(EnergyStageTooltip.class, EnergyStageTooltipView::new);
    getTooltipBinder().bind(LifecycleTooltip.class, (stageView, tooltip) -> new LifecycleTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(UserEventTooltip.class, (stageView, tooltip) -> new UserEventTooltipView(stageView.getComponent(), tooltip));

    JBSplitter verticalSplitter = new JBSplitter(true);
    verticalSplitter.getDivider().setBorder(DEFAULT_HORIZONTAL_BORDERS);
    verticalSplitter.setFirstComponent(buildMonitorUi());

    myEventsPanel = new JPanel(new TabularLayout("Fit-,Fit-,*,Fit-", "Fit-,*"));
    myEventsPanel.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
    JLabel showLabel = new JLabel("Show");
    showLabel.setBorder(new JBEmptyBorder(0, 11, 0, 8));
    showLabel.setFont(ProfilerFonts.STANDARD_FONT);
    myEventsPanel.add(showLabel, new TabularLayout.Constraint(0, 0));
    JComponent configurationComponent = getConfigurationComponent();
    configurationComponent.setFont(ProfilerFonts.STANDARD_FONT);
    myEventsPanel.add(configurationComponent, new TabularLayout.Constraint(0, 1));

    JLabel selectionTimeLabel = getSelectionTimeLabel();
    selectionTimeLabel.setBorder(new JBEmptyBorder(0, 0, 0, 8));
    myEventsPanel.add(selectionTimeLabel, new TabularLayout.Constraint(0, 3));

    JComponent eventsView = new EnergyEventsView(this).getComponent();
    myEventsPanel.add(new JBScrollPane(eventsView), new TabularLayout.Constraint(1, 0, 1, 4));
    myEventsPanel.setVisible(false);
    verticalSplitter.setSecondComponent(myEventsPanel);

    myDetailsView = new EnergyDetailsView(this);
    myDetailsView.setMinimumSize(new Dimension(JBUI.scale(450), (int)myDetailsView.getMinimumSize().getHeight()));
    myDetailsView.setVisible(false);
    JBSplitter splitter = new JBSplitter(false, 0.6f);
    splitter.setFirstComponent(verticalSplitter);
    splitter.setSecondComponent(myDetailsView);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.getDivider().setBorder(DEFAULT_VERTICAL_BORDERS);

    getComponent().add(splitter, BorderLayout.CENTER);

    getStage().getAspect().addDependency(this)
      .onChange(EnergyProfilerAspect.SELECTED_EVENT_DURATION, this::updateSelectedDurationView);
  }

  @NotNull
  private JPanel buildMonitorUi() {
    StudioProfilers profilers = getStage().getStudioProfilers();
    StreamingTimeline timeline = getStage().getTimeline();
    RangeSelectionComponent selection = new RangeSelectionComponent(getStage().getRangeSelectionModel());
    selection.setCursorSetter(AdtUiUtils::setTooltipCursor);
    RangeTooltipComponent tooltip = new RangeTooltipComponent(timeline,
                                                              getTooltipPanel(),
                                                              getProfilersView().getComponent(),
                                                              () -> selection.shouldShowSeekComponent());
    TabularLayout layout = new TabularLayout("*");
    JPanel panel = new JBPanel(layout);
    panel.setBackground(ProfilerColors.DEFAULT_STAGE_BACKGROUND);
    // Order matters, as such we want to put the tooltip component first so we draw the tooltip line on top of all other
    // components.
    panel.add(tooltip, new TabularLayout.Constraint(0, 0, 2, 1));

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    StreamingScrollbar scrollbar = new StreamingScrollbar(timeline, panel);
    panel.add(scrollbar, new TabularLayout.Constraint(4, 0));

    JComponent timeAxis = buildTimeAxis(profilers);
    panel.add(timeAxis, new TabularLayout.Constraint(3, 0));

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), getStage().getEventMonitor());
    JComponent eventsComponent = eventsView.getComponent();
    panel.add(eventsComponent, new TabularLayout.Constraint(0, 0));

    JPanel monitorPanel = new JBPanel(new TabularLayout("*", "*"));
    monitorPanel.setOpaque(false);
    monitorPanel.setBorder(MONITOR_BORDER);
    final JLabel label = new JLabel(getStage().getName());
    label.setBorder(MONITOR_LABEL_PADDING);
    label.setVerticalAlignment(SwingConstants.TOP);

    final JPanel lineChartPanel = new JBPanel(new BorderLayout());
    lineChartPanel.setOpaque(false);
    lineChartPanel.setBorder(BorderFactory.createEmptyBorder(Y_AXIS_TOP_MARGIN, 0, 0, 0));
    DetailedEnergyUsage usage = getStage().getDetailedUsage();

    final LineChart lineChart = new LineChart(usage);

    LineConfig cpuConfig = new LineConfig(ProfilerColors.ENERGY_CPU)
      .setFilled(true)
      .setStacked(true)
      .setLegendIconType(LegendConfig.IconType.BOX)
      .setDataBucketInterval(EnergyMonitorView.CHART_INTERVAL_US);
    lineChart.configure(usage.getCpuUsageSeries(), cpuConfig);
    LineConfig networkConfig = new LineConfig(ProfilerColors.ENERGY_NETWORK)
      .setFilled(true)
      .setStacked(true)
      .setLegendIconType(LegendConfig.IconType.BOX)
      .setDataBucketInterval(EnergyMonitorView.CHART_INTERVAL_US);
    lineChart.configure(usage.getNetworkUsageSeries(), networkConfig);
    LineConfig locationConfig = new LineConfig(ProfilerColors.ENERGY_LOCATION)
      .setFilled(true)
      .setStacked(true)
      .setLegendIconType(LegendConfig.IconType.BOX)
      .setDataBucketInterval(EnergyMonitorView.CHART_INTERVAL_US);
    lineChart.configure(usage.getLocationUsageSeries(), locationConfig);
    // The total usage series is only added in the LineChartModel so it can calculate the max Y value across all usages because the
    // LineChartModel currently does not calculate the max y Range value based on stacked but individual values.
    // We don't want to draw it as an extra line so we hide it by setting it to transparent.
    lineChart.configure(usage.getTotalUsageDataSeries(), new LineConfig(ProfilerColors.TRANSPARENT_COLOR));
    lineChart.setRenderOffset(0, (int)LineConfig.DEFAULT_DASH_STROKE.getLineWidth() / 2);
    lineChartPanel.add(lineChart, BorderLayout.CENTER);

    final JPanel axisPanel = new JBPanel(new BorderLayout());
    axisPanel.setOpaque(false);
    final AxisComponent leftAxis = new AxisComponent(getStage().getAxis(), AxisComponent.AxisOrientation.RIGHT);
    leftAxis.setShowAxisLine(false);
    leftAxis.setShowUnitAtMax(true);
    leftAxis.setHideTickAtMin(true);
    leftAxis.setMarkerLengths(MARKER_LENGTH, MARKER_LENGTH);
    leftAxis.setMargins(0, Y_AXIS_TOP_MARGIN);
    axisPanel.add(leftAxis, BorderLayout.WEST);

    EnergyProfilerStage.EnergyUsageLegends legends = getStage().getLegends();
    LegendComponent legend = new LegendComponent.Builder(legends).setRightPadding(PROFILER_LEGEND_RIGHT_PADDING).build();
    legend.configure(legends.getCpuLegend(), new LegendConfig(lineChart.getLineConfig(usage.getCpuUsageSeries())));
    legend.configure(legends.getNetworkLegend(), new LegendConfig(lineChart.getLineConfig(usage.getNetworkUsageSeries())));
    legend.configure(legends.getLocationLegend(), new LegendConfig(lineChart.getLineConfig(usage.getLocationUsageSeries())));

    final JPanel legendPanel = new JBPanel(new BorderLayout());
    legendPanel.setOpaque(false);
    legendPanel.add(label, BorderLayout.WEST);
    legendPanel.add(legend, BorderLayout.EAST);

    getStage().getRangeSelectionModel().addListener(new RangeSelectionListener() {
      @Override
      public void selectionCreated() {
        myEventsPanel.setVisible(true);
      }

      @Override
      public void selectionCleared() {
        myEventsPanel.setVisible(false);
      }

      @Override
      public void selectionCreationFailure() {
        myEventsPanel.setVisible(false);
      }
    });
    // Clears the selected duration when the new selection range does not overlap with it.
    selection.addSelectionUpdatedListener(selectionRange -> {
      if (getStage().getSelectedDuration() != null) {
        EnergyDuration selectedDuration = getStage().getSelectedDuration();
        long detailsStartUs = TimeUnit.NANOSECONDS.toMicros(selectedDuration.getEventList().get(0).getTimestamp());
        long detailsEndUs = detailsStartUs;
        if (detailsEndUs < selectionRange.getMin()) {
          // Updates the end timestamp when last event is not terminal at the details select time. When a new selection range happened,
          // the previous opened details could have terminated and the end time is not Long.MAX_VALUE.
          selectedDuration = getStage().updateDuration(selectedDuration);
          Common.Event lastEvent = selectedDuration.getEventList().get(selectedDuration.getEventList().size() - 1);
          detailsEndUs = lastEvent.getIsEnded() ? TimeUnit.NANOSECONDS.toMicros(lastEvent.getTimestamp()) : Long.MAX_VALUE;
        }
        if (selectionRange.getMax() < detailsStartUs || selectionRange.getMin() > detailsEndUs) {
          getStage().setSelectedDuration(null);
        }
      }
    });

    JComponent minibar = new EnergyEventMinibar(this).getComponent();

    selection.addMouseListener(new ProfilerTooltipMouseAdapter(getStage(), () -> new EnergyStageTooltip(getStage())));
    tooltip.registerListenersOn(selection);
    eventsView.registerTooltip(tooltip, getStage());

    if (!getStage().hasUserUsedEnergySelection()) {
      installProfilingInstructions(monitorPanel);
    }

    getProfilersView().installCommonMenuItems(selection);

    monitorPanel.add(axisPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(legendPanel, new TabularLayout.Constraint(0, 0));
    monitorPanel.add(lineChartPanel, new TabularLayout.Constraint(0, 0));

    JPanel stagePanel = new JPanel(new TabularLayout("*", "*,Fit"));
    stagePanel.add(monitorPanel, new TabularLayout.Constraint(0, 0));
    stagePanel.add(minibar, new TabularLayout.Constraint(1, 0));
    layout.setRowSizing(1, "*");
    stagePanel.setBackground(null);

    panel.add(selection, new TabularLayout.Constraint(1, 0));
    panel.add(stagePanel, new TabularLayout.Constraint(1, 0));

    return panel;
  }

  @Override
  public JComponent getToolbar() {
    JPanel toolBar = new JPanel(createToolbarLayout());
    JLabel textLabel = new JLabel();
    textLabel.setText("Modeled");
    textLabel.setFont(ProfilerFonts.H4_FONT);
    textLabel.setBorder(new JBEmptyBorder(4, 8, 4, 7));
    toolBar.add(textLabel);

    JLabel iconLabel = new JLabel();
    iconLabel.setIcon(StudioIcons.Common.HELP);
    toolBar.add(iconLabel);

    JTextPane textPane = new JTextPane();
    textPane.setEditable(false);
    textPane.setBorder(TOOLTIP_BORDER);
    textPane.setBackground(ProfilerColors.TOOLTIP_BACKGROUND);
    textPane.setForeground(ProfilerColors.TOOLTIP_TEXT);
    textPane.setFont(TooltipView.TOOLTIP_BODY_FONT);
    textPane.setText(
      "The Energy Profiler models your app's estimated energy usage of CPU, Network, and GPS resources of your device. " +
      "It also highlights background events that may contribute to battery drain, " +
      "such as wake locks, alarms, jobs, and location requests.");
    textPane.setPreferredSize(new Dimension(350, 0));
    TooltipComponent tooltip =
      new TooltipComponent.Builder(textPane, iconLabel, getProfilersView().getComponent()).build();
    tooltip.registerListenersOn(iconLabel);

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolBar, BorderLayout.WEST);
    return panel;
  }

  private void updateSelectedDurationView() {
    myDetailsView.setDuration(getStage().getSelectedDuration());
  }

  private void installProfilingInstructions(@NotNull JPanel parent) {
    assert parent.getLayout().getClass() == TabularLayout.class;
    InstructionsPanel panel =
      new InstructionsPanel.Builder(
        new TextInstruction(UIUtilities.getFontMetrics(parent, ProfilerFonts.H2_FONT), "Select a range to inspect energy events"))
        .setEaseOut(getStage().getInstructionsEaseOutModel(), instructionPanel -> parent.remove(instructionPanel))
        .setBackgroundCornerRadius(PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER, PROFILING_INSTRUCTIONS_BACKGROUND_ARC_DIAMETER)
        .build();
    parent.add(panel, new TabularLayout.Constraint(0, 0));
  }

  private JComponent getConfigurationComponent() {
    JComboBox<EnergyEventOrigin> comboBox = new ComboBox<>(new EnumComboBoxModel<>(EnergyEventOrigin.class));
    comboBox.getModel().setSelectedItem(getStage().getEventOrigin());
    comboBox.setRenderer(SimpleListCellRenderer.create("", EnergyEventOrigin::getLabelString));
    comboBox.addActionListener(e -> {
      Object origin = comboBox.getSelectedItem();
      if (origin instanceof EnergyEventOrigin) {
        getStage().setEventOrigin((EnergyEventOrigin)origin);
      }
    });
    return comboBox;
  }
}
