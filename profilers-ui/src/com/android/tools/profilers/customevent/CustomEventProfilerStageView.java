/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.customevent;


import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.trackgroup.TrackGroupModel;
import com.android.tools.adtui.trackgroup.TrackGroup;
import com.android.tools.profilers.ProfilerColors;
import com.android.tools.profilers.ProfilerScrollbar;
import com.android.tools.profilers.ProfilerTimeline;
import com.android.tools.profilers.ProfilerTrackRendererFactory;
import com.android.tools.profilers.StageView;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents the view of all the custom events that users have chosen to track for Custom Event Visualization.
 */
public class CustomEventProfilerStageView extends StageView<CustomEventProfilerStage> {
  private static final ProfilerTrackRendererFactory TRACK_RENDERER_FACTORY = new ProfilerTrackRendererFactory();

  @NotNull
  private final JComponent myTrackGroupList;
  @NotNull private final StudioProfilers myStudioProfilers;

  public CustomEventProfilerStageView(@NotNull StudioProfilersView profilersView, @NotNull CustomEventProfilerStage stage) {
    super(profilersView, stage);

    myTrackGroupList = createTrackGroups(stage.getTrackGroupModels());
    myStudioProfilers = stage.getStudioProfilers();

    // Add a dependency for when the range changes so the track group list has to be repainted as the timeline moves.
    myStudioProfilers.getTimeline().getViewRange().addDependency(this).onChange(Range.Aspect.RANGE, this::updateTrackGroupList);

    buildUI();
  }

  @Override
  public JComponent getToolbar() {
    // Currently an empty toolbar
    JPanel toolBar = new JPanel(createToolbarLayout());
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolBar, BorderLayout.WEST);
    return panel;
  }

  @VisibleForTesting
  @NotNull
  protected final JComponent getTrackGroupList() {
    return myTrackGroupList;
  }

  private void buildUI() {
    ProfilerTimeline timeline = myStudioProfilers.getTimeline();

    // The scrollbar can modify the view range of timeline and the tracks.
    getComponent().add(new ProfilerScrollbar(timeline, getComponent()), BorderLayout.SOUTH);

    // Two row panel:
    // 1. first row contains the EventMonitor and the tracks for each user event.
    // 2. second row contains the time axis. Time axis will stay pinned when the view window is resized.
    JPanel container = new JPanel(new TabularLayout("*", "*,Fit-"));

    // Main panel containing the EventMonitor and user counter tracks.
    JPanel mainPanel = new JPanel(new TabularLayout("*"));
    mainPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
    RangeTooltipComponent
      tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(), timeline.getDataRange(), getTooltipPanel(),
                                          getProfilersView().getComponent(), () -> true);
    getTooltipBinder().bind(LifecycleTooltip.class, LifecycleTooltipView::new);
    getTooltipBinder().bind(UserEventTooltip.class, UserEventTooltipView::new);

    EventMonitorView eventsView = new EventMonitorView(getProfilersView(), new EventMonitor(myStudioProfilers));
    eventsView.registerTooltip(tooltip, getStage());
    JComponent eventsComponent = eventsView.getComponent();

    mainPanel.add(eventsComponent, new TabularLayout.Constraint(0, 0));
    mainPanel.add(myTrackGroupList, new TabularLayout.Constraint(1, 0));

    JComponent timeAxis = buildTimeAxis(myStudioProfilers);

    // Add all components to the main panel.
    container.add(tooltip, new TabularLayout.Constraint(0, 0));
    container.add(new JBScrollPane(mainPanel), new TabularLayout.Constraint(0, 0));
    container.add(timeAxis, new TabularLayout.Constraint(1, 0));

    getComponent().add(container, BorderLayout.CENTER);
  }

  private void updateTrackGroupList() {
    // Force track group list to validate its children.
    myTrackGroupList.updateUI();
  }

  /**
   * Creates the JComponent containing all the track groups in the stage.
   */
  private static JComponent createTrackGroups(@NotNull List<TrackGroupModel> trackGroupModels) {
    JPanel panel = new JPanel(new TabularLayout("*", "Fit"));
    for (int i = 0; i < trackGroupModels.size(); ++i) {
      panel.add(new TrackGroup(trackGroupModels.get(i), TRACK_RENDERER_FACTORY).getComponent(),
                new TabularLayout.Constraint(i, 0));
    }
    return panel;
  }
}
