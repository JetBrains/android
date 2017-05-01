/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.*;
import java.util.List;

class MonitorPanel extends JPanel {
  @NotNull private List<BaseMonitorView> myMonitors;
  @NotNull private Map<Component, Component> myHiddenMonitorLookup; // This is a bi-directional lookup.
  @NotNull private Map<Component, GridBagConstraints> myConstraintsLookup;

  MonitorPanel(@NotNull BaseMonitorView[] monitors) {
    super(new GridBagLayout());

    myMonitors = Arrays.asList(monitors);

    // Sort the monitors according to their ordering.
    Collections.sort(myMonitors, new Comparator<BaseMonitorView>() {
      @Override
      public int compare(BaseMonitorView left, BaseMonitorView right) {
        return left.getPosition() - right.getPosition();
      }
    });

    // Fixup the positions in case the saved data is in an invalid state.
    for (int i = 0; i < monitors.length; ++i) {
      monitors[i].setPosition(i);
    }

    // Find the longest title label to align to.
    JLabel titleLengthLabel = new JLabel();
    int largestLabelWidth = 0;
    for (BaseMonitorView monitor : monitors) {
      titleLengthLabel.setText(monitor.getTitleName());
      titleLengthLabel.setIcon(monitor.getTitleIcon());
      largestLabelWidth = Math.max(titleLengthLabel.getPreferredSize().width, largestLabelWidth);
    }

    myHiddenMonitorLookup = new HashMap<Component, Component>(monitors.length);
    myConstraintsLookup = new HashMap<Component, GridBagConstraints>(monitors.length);

    for (BaseMonitorView monitor : monitors) {
      ComponentWithActions monitorComponentWithActions = monitor.createComponent();
      ActionGroup actions = monitorComponentWithActions.getToolbarActions();
      assert actions != null;

      layoutMonitor(monitorComponentWithActions.getComponent(), actions, monitor, largestLabelWidth);
    }

    JPanel spacerPanel = new JPanel();
    spacerPanel.setMinimumSize(new Dimension(0, 0));
    spacerPanel.setPreferredSize(new Dimension(0, 0));
    spacerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    // Use a tiny value for the vertical weight so when everything is minimized (implies 0 weight on those components), this component will
    // take up the rest of the panel.
    add(spacerPanel,
        new GridBagConstraints(0, getComponentCount(), 1, 1, 1.0, 0.000000001, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                               new Insets(0, 0, 0, 0), 0, 0));
  }

  boolean canMove(@NotNull BaseMonitorView monitor, int delta) {
    int destination = monitor.getPosition() + delta;
    return destination >= 0 && destination < myMonitors.size();
  }

  /**
   * Converts the {@code BaseMonitorView} to the index within this component. The toolbar is always at this index, and the monitor itself
   * is at this index+1.
   */
  private static int getToolbarIndex(@NotNull BaseMonitorView monitor) {
    return monitor.getPosition() * 2;
  }

  private GridBagLayout getMonitorLayout() {
    return (GridBagLayout)getLayout();
  }

  void moveMonitorUp(@NotNull BaseMonitorView monitor) {
    if (!canMove(monitor, -1)) {
      assert myMonitors.get(0) == monitor;
      return;
    }

    assert myMonitors.contains(monitor);
    swapMonitors(myMonitors.get(monitor.getPosition() - 1), monitor);
  }

  void moveMonitorDown(@NotNull BaseMonitorView monitor) {
    if (!canMove(monitor, 1)) {
      assert myMonitors.get(myMonitors.size() - 1) == monitor;
      return;
    }

    assert myMonitors.contains(monitor);
    swapMonitors(monitor, myMonitors.get(monitor.getPosition() + 1));
  }

  private void swapMonitors(@NotNull BaseMonitorView a, @NotNull BaseMonitorView b) {
    assert a != b;

    int baseIndexA = getToolbarIndex(a);
    int baseIndexB = getToolbarIndex(b);

    if (baseIndexA > baseIndexB) {
      int temp = baseIndexA;
      baseIndexA = baseIndexB;
      baseIndexB = temp;
    }

    Component toolbarB = getComponent(baseIndexB);
    GridBagConstraints toolbarBConstraints = myConstraintsLookup.get(toolbarB);
    Component monitorB = getComponent(baseIndexB + 1);
    GridBagConstraints monitorBConstraints = myConstraintsLookup.get(monitorB);

    remove(baseIndexB + 1);
    remove(baseIndexB);

    // Don't need to modify the hidden monitor panel's constraints (gridy, in particular). We'll do that in minimizeMonitor.
    toolbarBConstraints.gridy = baseIndexA;
    monitorBConstraints.gridy = baseIndexA + 1;

    Component toolbarA = getComponent(baseIndexA);
    GridBagConstraints toolbarAConstraints = myConstraintsLookup.get(toolbarA);
    Component monitorA = getComponent(baseIndexA + 1);
    GridBagConstraints monitorAConstraints = myConstraintsLookup.get(monitorA);

    remove(baseIndexA + 1);
    remove(baseIndexA);

    toolbarAConstraints.gridy = baseIndexB;
    monitorAConstraints.gridy = baseIndexB + 1;

    add(toolbarB, toolbarBConstraints, baseIndexA);
    add(monitorB, monitorBConstraints, baseIndexA + 1);
    add(toolbarA, toolbarAConstraints, baseIndexB);
    add(monitorA, monitorAConstraints, baseIndexB + 1);

    int positionA = a.getPosition();
    int positionB = b.getPosition();

    a.setPosition(positionB);
    b.setPosition(positionA);
    myMonitors.set(positionA, b);
    myMonitors.set(positionB, a);
    assert myMonitors.indexOf(a) == a.getPosition();
    assert myMonitors.indexOf(b) == b.getPosition();

    revalidate();
    repaint();
  }

  void setMonitorMinimized(@NotNull BaseMonitorView monitor, boolean isMinimized) {
    int monitorIndex = getToolbarIndex(monitor) + 1;
    monitor.setIsMinimized(isMinimized);

    Component componentAtMonitorIndex = getComponent(monitorIndex);
    GridBagConstraints componentConstraintsAtMonitorIndex = getMonitorLayout().getConstraints(componentAtMonitorIndex);

    assert myHiddenMonitorLookup.containsKey(componentAtMonitorIndex);
    Component complementComponent = myHiddenMonitorLookup.get(componentAtMonitorIndex);
    GridBagConstraints complementComponentConstraints = myConstraintsLookup.get(complementComponent);
    complementComponentConstraints.gridy = componentConstraintsAtMonitorIndex.gridy;

    remove(monitorIndex);
    add(complementComponent, complementComponentConstraints, monitorIndex);

    revalidate();
    repaint();
  }

  @NotNull
  private static GridBagConstraints getMinimizedPanelConstraints(@NotNull BaseMonitorView monitor) {
    final GridBagConstraints minimizedPanelConstraints = new GridBagConstraints();
    minimizedPanelConstraints.fill = GridBagConstraints.BOTH;
    minimizedPanelConstraints.weightx = 1.0;
    minimizedPanelConstraints.weighty = 0.0;
    minimizedPanelConstraints.gridx = 0;
    minimizedPanelConstraints.gridy = getToolbarIndex(monitor) + 1;
    return minimizedPanelConstraints;
  }

  @NotNull
  private static GridBagConstraints getMonitorConstraints(int componentIndex) {
    // Set the monitorComponent to span across the whole panel, and expand as much as possible.
    final GridBagConstraints monitorComponentConstraints = new GridBagConstraints();
    monitorComponentConstraints.fill = GridBagConstraints.BOTH;
    monitorComponentConstraints.weightx = 1.0;
    monitorComponentConstraints.weighty = 1.0;
    monitorComponentConstraints.gridx = 0;
    monitorComponentConstraints.gridy = componentIndex;
    return monitorComponentConstraints;
  }

  @NotNull
  private ActionToolbarImpl setupToolbar(@NotNull ActionGroup actions, @NotNull BaseMonitorView monitor) {
    DefaultActionGroup viewActions = new DefaultActionGroup();
    viewActions.addAll(actions);
    viewActions.add(new MonitorMoveAction(this, monitor, -1));
    viewActions.add(new MonitorMoveAction(this, monitor, 1));
    viewActions.add(new MinimizeAction(this, monitor));
    ActionToolbarImpl toolbar =
      new ActionToolbarImpl(ActionPlaces.UNKNOWN, viewActions, true, false, DataManager.getInstance(), ActionManagerEx.getInstanceEx(),
                            KeymapManagerEx.getInstanceEx());
    toolbar.setBorder(BorderFactory.createEmptyBorder());
    toolbar.setMinimumSize(new Dimension(0, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height));
    toolbar.setPreferredSize(new Dimension(Integer.MAX_VALUE, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height));
    toolbar.setMaximumSize(toolbar.getPreferredSize());
    return toolbar;
  }

  private void layoutMonitor(@NotNull final JComponent monitorComponent,
                             @NotNull ActionGroup actions,
                             @NotNull BaseMonitorView monitor,
                             int titleSize) {
    // Set a minimum size on the monitorComponent so that it doesn't squish when the container is made small.
    monitorComponent.setMinimumSize(new Dimension(100, 100));
    monitorComponent.setPreferredSize(new Dimension(Integer.MAX_VALUE, 100));

    int toolbarIndex = getToolbarIndex(monitor);
    assert toolbarIndex == getComponentCount();
    final int componentIndex = toolbarIndex + 1;

    // Create a blank panel to show when the view's component is minimized.
    final JPanel minimizedPanel = new JPanel();
    minimizedPanel.setBackground(monitor.getViewBackgroundColor());
    minimizedPanel.setMinimumSize(new Dimension(Integer.MAX_VALUE, 2));
    minimizedPanel.setPreferredSize(minimizedPanel.getMinimumSize());
    minimizedPanel.setMaximumSize(minimizedPanel.getMinimumSize());

    myHiddenMonitorLookup.put(monitorComponent, minimizedPanel);
    myHiddenMonitorLookup.put(minimizedPanel, monitorComponent);

    ActionToolbarImpl toolbar = setupToolbar(actions, monitor);

    JBLabel titleLabel = new JBLabel(monitor.getTitleName(), monitor.getTitleIcon(), SwingConstants.LEFT);
    Border border = BorderFactory.createEmptyBorder(0, 5, 0, 20);
    titleLabel.setBorder(border);
    titleLabel.setBackground(toolbar.getBackground());
    Insets borderInsets = border.getBorderInsets(titleLabel);
    titleLabel.setMinimumSize(
      new Dimension(titleSize + borderInsets.left + borderInsets.right, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height));
    titleLabel.setPreferredSize(titleLabel.getMinimumSize());
    titleLabel.setMaximumSize(titleLabel.getMinimumSize());

    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.add(titleLabel, BorderLayout.WEST);
    titlePanel.add(toolbar, BorderLayout.CENTER);
    GridBagConstraints titlePanelConstraints =
      new GridBagConstraints(0, toolbarIndex, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                             new Insets(0, 0, 0, 0), 0, 0);

    add(titlePanel, titlePanelConstraints);
    final GridBagConstraints monitorComponentConstraints = getMonitorConstraints(componentIndex);
    final GridBagConstraints minimizedPanelConstraints = getMinimizedPanelConstraints(monitor);
    myConstraintsLookup.put(minimizedPanel, minimizedPanelConstraints);

    if (monitor.getIsMinimized()) {
      add(minimizedPanel, minimizedPanelConstraints);
    }
    else {
      add(monitorComponent, monitorComponentConstraints);
    }

    myConstraintsLookup.put(monitorComponent, monitorComponentConstraints);
    myConstraintsLookup.put(titlePanel, titlePanelConstraints);
  }
}
