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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.RangeTooltipComponent;
import com.android.tools.profilers.cpu.CpuMonitor;
import com.android.tools.profilers.cpu.CpuMonitorTooltipView;
import com.android.tools.profilers.cpu.CpuMonitorView;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.event.EventMonitorTooltipView;
import com.android.tools.profilers.event.EventMonitorView;
import com.android.tools.profilers.memory.MemoryMonitor;
import com.android.tools.profilers.memory.MemoryMonitorTooltipView;
import com.android.tools.profilers.memory.MemoryMonitorView;
import com.android.tools.profilers.network.NetworkMonitor;
import com.android.tools.profilers.network.NetworkMonitorTooltipView;
import com.android.tools.profilers.network.NetworkMonitorView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  private ProfilerTooltipView myMonitorTooltipView;
  @NotNull
  private final ViewBinder<StudioMonitorStageView, ProfilerMonitor, ProfilerTooltipView> myTooltipBinder;
  @NotNull
  private final JPanel myTooltip;

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

    myTooltip = new JPanel(new BorderLayout());
    myTooltip.setMinimumSize(new Dimension(100, 10));

    RangeTooltipComponent
      tooltip = new RangeTooltipComponent(timeline.getTooltipRange(), timeline.getViewRange(), timeline.getDataRange(), myTooltip,
                                          ProfilerLayeredPane.class);

    myTooltipBinder = new ViewBinder<>();
    myTooltipBinder.bind(NetworkMonitor.class, NetworkMonitorTooltipView::new);
    myTooltipBinder.bind(CpuMonitor.class, CpuMonitorTooltipView::new);
    myTooltipBinder.bind(MemoryMonitor.class, MemoryMonitorTooltipView::new);
    myTooltipBinder.bind(EventMonitor.class, EventMonitorTooltipView::new);

    stage.getAspect().addDependency(this).onChange(StudioMonitorStage.Aspect.TOOLTIP, this::tooltipChanged);

    myViews = new ArrayList<>(stage.getMonitors().size());
    int rowIndex = 0;
    for (ProfilerMonitor monitor : stage.getMonitors()) {
      ProfilerMonitorView view = binder.build(profilersView, monitor);
      JComponent component = view.getComponent();
      tooltip.registerListenersOn(component);
      component.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          if (monitor.isEnabled()) {
            stage.setTooltip(monitor);
          } else {
            stage.setTooltip(null);
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          stage.setTooltip(null);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          expandMonitor(monitor);
        }
      });
      component.addKeyListener(new KeyAdapter() {
        @Override
        public void keyTyped(KeyEvent e) {
          // On Windows we don't get a KeyCode so checking the getKeyCode doesn't work. Instead we get the code from the char
          // we are given.
          int keyCode = KeyEvent.getExtendedKeyCodeForChar(e.getKeyChar());
          if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
            if (monitor.isFocused()) {
              expandMonitor(monitor);
            }
          }
        }
      });
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

  private void tooltipChanged() {
    if (myMonitorTooltipView != null) {
      myMonitorTooltipView.dispose();
      myMonitorTooltipView = null;
    }
    myTooltip.removeAll();
    ProfilerMonitor tooltip = getStage().getTooltip();
    if (tooltip != null) {
      myMonitorTooltipView = myTooltipBinder.build(this, tooltip);
      Component component = myMonitorTooltipView.createComponent();
      myTooltip.add(component, BorderLayout.CENTER);
    }
    myTooltip.repaint();
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
