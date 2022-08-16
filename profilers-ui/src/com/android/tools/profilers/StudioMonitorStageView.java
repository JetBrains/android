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
package com.android.tools.profilers;

import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.adtui.stdui.DefaultContextMenuItem;
import com.android.tools.adtui.stdui.StreamingScrollbar;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.profilers.cpu.CpuMonitor;
import com.android.tools.profilers.cpu.CpuMonitorTooltip;
import com.android.tools.profilers.cpu.CpuMonitorTooltipView;
import com.android.tools.profilers.cpu.CpuMonitorView;
import com.android.tools.profilers.customevent.CustomEventMonitor;
import com.android.tools.profilers.customevent.CustomEventMonitorTooltip;
import com.android.tools.profilers.customevent.CustomEventMonitorTooltipView;
import com.android.tools.profilers.customevent.CustomEventMonitorView;
import com.android.tools.profilers.energy.EnergyMonitor;
import com.android.tools.profilers.energy.EnergyMonitorTooltip;
import com.android.tools.profilers.energy.EnergyMonitorTooltipView;
import com.android.tools.profilers.energy.EnergyMonitorView;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.event.LifecycleTooltip;
import com.android.tools.profilers.event.LifecycleTooltipView;
import com.android.tools.profilers.event.UserEventTooltip;
import com.android.tools.profilers.event.UserEventTooltipView;
import com.android.tools.profilers.memory.MemoryMonitor;
import com.android.tools.profilers.memory.MemoryMonitorTooltip;
import com.android.tools.profilers.memory.MemoryMonitorTooltipView;
import com.android.tools.profilers.memory.MemoryMonitorView;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * Bird eye view displaying high-level information across all profilers.
 */
public class StudioMonitorStageView extends StageView<StudioMonitorStage> {
  private static final String SHOW_DEBUGGABLE_MESSAGE = "debuggable.monitor.message";
  private static final String SHOW_PROFILEABLE_MESSAGE = "profileable.monitor.message";
  @NotNull
  @SuppressWarnings("FieldCanBeLocal") // We need to keep a reference to the sub-views. If they got collected, they'd stop updating the UI.
  private final List<ProfilerMonitorView> myViews;

  public StudioMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull StudioMonitorStage stage) {
    super(profilersView, stage);

    ViewBinder<StudioProfilersView, ProfilerMonitor, ProfilerMonitorView> binder = new ViewBinder<>();
    binder.bind(CpuMonitor.class, CpuMonitorView::new);
    binder.bind(MemoryMonitor.class, MemoryMonitorView::new);
    binder.bind(EventMonitor.class, EventMonitorView::new);
    boolean isEnergyProfilerEnabled = getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isEnergyProfilerEnabled();
    if (isEnergyProfilerEnabled) {
      binder.bind(EnergyMonitor.class, EnergyMonitorView::new);
    }

    boolean isCustomEventVisualizationEnabled =
      getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isCustomEventVisualizationEnabled();
    if (isCustomEventVisualizationEnabled) {
      binder.bind(CustomEventMonitor.class, CustomEventMonitorView::new);
    }

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    StreamingScrollbar sb = new StreamingScrollbar(getStage().getTimeline(), getComponent());
    getComponent().add(sb, BorderLayout.SOUTH);

    // Create a 2-row panel. First row, all monitors; second row, the timeline. This way, the
    // timeline will always be at the bottom, even if no monitors are found (e.g. when the phone is
    // disconnected).
    JPanel topPanel = new JPanel(new TabularLayout("*", "*,Fit-"));
    topPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    TabularLayout layout = new TabularLayout("*");
    JPanel monitors = new JPanel(layout);

    // Use FlowLayout instead of the usual BorderLayout since BorderLayout doesn't respect min/preferred sizes.
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    RangeTooltipComponent tooltipComponent =
      new RangeTooltipComponent(getStage().getTimeline(), getTooltipPanel(), getProfilersView().getComponent(), () -> true);

    getTooltipBinder().bind(CpuMonitorTooltip.class, CpuMonitorTooltipView::new);
    getTooltipBinder().bind(MemoryMonitorTooltip.class, MemoryMonitorTooltipView::new);
    getTooltipBinder().bind(LifecycleTooltip.class, (stageView, tooltip) -> new LifecycleTooltipView(stageView.getComponent(), tooltip));
    getTooltipBinder().bind(UserEventTooltip.class, (stageView, tooltip) -> new UserEventTooltipView(stageView.getComponent(), tooltip));
    if (isEnergyProfilerEnabled) {
      getTooltipBinder().bind(EnergyMonitorTooltip.class, EnergyMonitorTooltipView::new);
    }
    if (isCustomEventVisualizationEnabled) {
      getTooltipBinder().bind(CustomEventMonitorTooltip.class, CustomEventMonitorTooltipView::new);
    }

    myViews = new ArrayList<>(stage.getMonitors().size());
    int rowIndex = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = binder.build(profilersView, monitor);
      view.registerTooltip(tooltipComponent, stage);
      JComponent component = view.getComponent();
      component.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          // Sets the focus on the stage UI. This prevents the sessions UI from maintaining focus when the users starts navigating through
          // the profilers main UI.
          getProfilersView().getStageComponent().requestFocusInWindow();
          if (SwingUtilities.isLeftMouseButton(e)) {
            expandMonitor(monitor);
          }
        }
      });
      component.addKeyListener(new KeyAdapter() {
        @Override
        public void keyTyped(KeyEvent e) {
          // On Windows we don't get a KeyCode so checking the getKeyCode doesn't work. Instead we get the code from the char
          // we are given.
          int keyCode = KeyEvent.getExtendedKeyCodeForChar(e.getKeyChar());
          if (keyCode == KeyEvent.VK_ENTER) {
            if (monitor.isFocused()) {
              expandMonitor(monitor);
            }
          }
        }
      });

      // Configure Context Menu
      IdeProfilerComponents ideProfilerComponents = getIdeComponents();
      ContextMenuInstaller contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();

      DefaultContextMenuItem.Builder builder = new DefaultContextMenuItem.Builder("Open " + monitor.getName());
      DefaultContextMenuItem action =
        builder.setActionRunnable(() -> expandMonitor(monitor))
          .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
          .setContainerComponent(component).build();
      ProfilerContextMenu.createIfAbsent(component).add(action);
      contextMenuInstaller.installGenericContextMenu(component, action);
      contextMenuInstaller.installGenericContextMenu(component, ContextMenuItem.SEPARATOR);
      profilersView.installCommonMenuItems(component);

      layout.setRowSizing(rowIndex, rowSizeString(view));
      monitors.add(component, new TabularLayout.Constraint(rowIndex, 0));
      rowIndex++;
      myViews.add(view);
    }

    StudioProfilers profilers = stage.getStudioProfilers();
    JComponent timeAxis = buildTimeAxis(profilers);

    topPanel.add(tooltipComponent, new TabularLayout.Constraint(0, 0));
    topPanel.add(monitors, new TabularLayout.Constraint(0, 0));
    topPanel.add(timeAxis, new TabularLayout.Constraint(1, 0));

    getComponent().add(topPanel, BorderLayout.CENTER);
  }

  private String rowSizeString(@NotNull ProfilerMonitorView<ProfilerMonitor> view) {
    int weight = (int)(view.getVerticalWeight() * 100f);
    return (weight > 0) ? weight + "*" : "Fit-";
  }

  private void expandMonitor(ProfilerMonitor monitor) {
    // Track first, so current stage is sent with the event
    getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectMonitor();
    monitor.expand();
  }

  @Override
  public JComponent getToolbar() {
    switch (getStage().getStudioProfilers().getSelectedSessionSupportLevel()) {
      case DEBUGGABLE:
        return DismissibleMessage.of(getStage().getStudioProfilers(),
                                     SHOW_DEBUGGABLE_MESSAGE,
                                     "Profiling with complete data. This does not represent app performance in production." +
                                     " Consider profiling with low overhead.",
                                     SupportLevel.DOC_LINK);
      case PROFILEABLE:
        return DismissibleMessage.of(getStage().getStudioProfilers(),
                                     SHOW_PROFILEABLE_MESSAGE,
                                     "Profiling with low overhead. Certain profiler features will be unavailable in this mode.",
                                     SupportLevel.DOC_LINK);
    }
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }
}
