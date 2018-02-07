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
import com.android.tools.profilers.cpu.CpuMonitor;
import com.android.tools.profilers.cpu.CpuMonitorTooltip;
import com.android.tools.profilers.cpu.CpuMonitorTooltipView;
import com.android.tools.profilers.cpu.CpuMonitorView;
import com.android.tools.profilers.energy.EnergyMonitor;
import com.android.tools.profilers.energy.EnergyMonitorTooltip;
import com.android.tools.profilers.energy.EnergyMonitorTooltipView;
import com.android.tools.profilers.energy.EnergyMonitorView;
import com.android.tools.profilers.event.*;
import com.android.tools.profilers.memory.MemoryMonitor;
import com.android.tools.profilers.memory.MemoryMonitorTooltip;
import com.android.tools.profilers.memory.MemoryMonitorTooltipView;
import com.android.tools.profilers.memory.MemoryMonitorView;
import com.android.tools.profilers.network.NetworkMonitor;
import com.android.tools.profilers.network.NetworkMonitorTooltip;
import com.android.tools.profilers.network.NetworkMonitorTooltipView;
import com.android.tools.profilers.network.NetworkMonitorView;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Bird eye view displaying high-level information across all profilers.
 */
public class StudioMonitorStageView extends StageView<StudioMonitorStage> {
  @NotNull
  @SuppressWarnings("FieldCanBeLocal") // We need to keep a reference to the sub-views. If they got collected, they'd stop updating the UI.
  private final List<ProfilerMonitorView> myViews;

  public StudioMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull StudioMonitorStage stage) {
    super(profilersView, stage);

    ViewBinder<StudioProfilersView, ProfilerMonitor, ProfilerMonitorView> binder = new ViewBinder<>();
    binder.bind(NetworkMonitor.class, NetworkMonitorView::new);
    binder.bind(CpuMonitor.class, CpuMonitorView::new);
    binder.bind(MemoryMonitor.class, MemoryMonitorView::new);
    binder.bind(EventMonitor.class, EventMonitorView::new);
    boolean isEnergyProfilerEnabled = getStage().getStudioProfilers().getIdeServices().getFeatureConfig().isEnergyProfilerEnabled();
    if (isEnergyProfilerEnabled) {
      binder.bind(EnergyMonitor.class, EnergyMonitorView::new);
    }

    // The scrollbar can modify the view range - so it should be registered to the Choreographer before all other Animatables
    // that attempts to read the same range instance.
    ProfilerScrollbar sb = new ProfilerScrollbar(getTimeline(), getComponent());
    getComponent().add(sb, BorderLayout.SOUTH);

    // Create a 2-row panel. First row, all monitors; second row, the timeline. This way, the
    // timeline will always be at the bottom, even if no monitors are found (e.g. when the phone is
    // disconnected).
    JPanel topPanel = new JPanel(new TabularLayout("*", "*,Fit"));
    topPanel.setBackground(ProfilerColors.DEFAULT_BACKGROUND);

    TabularLayout layout = new TabularLayout("*");
    JPanel monitors = new JPanel(layout);

    ProfilerTimeline timeline = stage.getStudioProfilers().getTimeline();

    // Use FlowLayout instead of the usual BorderLayout since BorderLayout doesn't respect min/preferred sizes.
    getTooltipPanel().setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));

    RangeTooltipComponent
      tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(), timeline.getDataRange(), getTooltipPanel(),
                                          ProfilerLayeredPane.class);

    getTooltipBinder().bind(NetworkMonitorTooltip.class, NetworkMonitorTooltipView::new);
    getTooltipBinder().bind(CpuMonitorTooltip.class, CpuMonitorTooltipView::new);
    getTooltipBinder().bind(MemoryMonitorTooltip.class, MemoryMonitorTooltipView::new);
    getTooltipBinder().bind(EventActivityTooltip.class, EventActivityTooltipView::new);
    getTooltipBinder().bind(EventSimpleEventTooltip.class, EventSimpleEventTooltipView::new);
    if (isEnergyProfilerEnabled) {
      getTooltipBinder().bind(EnergyMonitorTooltip.class, EnergyMonitorTooltipView::new);
    }

    myViews = new ArrayList<>(stage.getMonitors().size());
    int rowIndex = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = binder.build(profilersView, monitor);
      view.registerTooltip(tooltip, stage);
      JComponent component = view.getComponent();
      component.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
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
      ProfilerContextMenu contextMenu = getProfilersView().getTimelineContextMenu();
      IdeProfilerComponents ideProfilerComponents = getIdeComponents();
      ContextMenuInstaller contextMenuInstaller = ideProfilerComponents.createContextMenuInstaller();

      ProfilerAction.Builder builder = new ProfilerAction.Builder("Open " + monitor.getName());
      ProfilerAction action =
        builder.setActionRunnable(() -> expandMonitor(monitor))
          .setKeyStrokes(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
          .setContainerComponent(component).build();
      ProfilerContextMenu.createIfAbsent(component).add(action);
      contextMenuInstaller.installGenericContextMenu(component, action);
      contextMenuInstaller.installGenericContextMenu(component, ContextMenuItem.SEPARATOR);
      for (ContextMenuItem item : contextMenu.getContextMenuItems()) {
        contextMenuInstaller.installGenericContextMenu(component, item);
      }

      int weight = (int)(view.getVerticalWeight() * 100f);
      layout.setRowSizing(rowIndex, (weight > 0) ? weight + "*" : "Fit");
      monitors.add(component, new TabularLayout.Constraint(rowIndex, 0));
      rowIndex++;
      myViews.add(view);
    }

    StudioProfilers profilers = stage.getStudioProfilers();
    JComponent timeAxis = buildTimeAxis(profilers);

    topPanel.add(tooltip, new TabularLayout.Constraint(0, 0));
    topPanel.add(monitors, new TabularLayout.Constraint(0, 0));
    topPanel.add(timeAxis, new TabularLayout.Constraint(1, 0));

    getComponent().add(topPanel, BorderLayout.CENTER);
  }

  private void expandMonitor(ProfilerMonitor monitor) {
    // Track first, so current stage is sent with the event
    getStage().getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectMonitor();
    monitor.expand();
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }
}
