/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.toolwindow;

import com.google.idea.common.ui.properties.ChangeListener;
import com.google.idea.common.ui.properties.ObservableValue;
import com.google.idea.common.ui.properties.Property;
import com.google.idea.common.ui.templates.AbstractView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBPanelWithEmptyText;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** View of the combination of the tree and output consoles as a single panel. */
final class TasksTreeConsoleView extends AbstractView<JPanel> {
  private final TasksTreeConsoleModel model;
  private final Disposable parentDisposable;

  private final ChangeListener<Task> treeSelectionListener = this::treeSelectionChanged;

  private final JPanel consolePanel = new JPanel(new BorderLayout());

  private final JBPanelWithEmptyText noSelectionPanel =
      new JBPanelWithEmptyText().withEmptyText("Select a task to view its output");

  TasksTreeConsoleView(TasksTreeConsoleModel model, Disposable parentDisposable) {
    this.model = model;
    this.parentDisposable = parentDisposable;
  }

  @Override
  protected JPanel createComponent() {
    return createContentPanel();
  }

  @Override
  protected void bind() {
    Property<Task> selected = model.getTreeModel().selectedTaskProperty();
    selected.addListener(treeSelectionListener);
    treeSelectionChanged(selected, null, selected.getValue());
  }

  @Override
  protected void unbind() {
    model.getTreeModel().selectedTaskProperty().removeListener(treeSelectionListener);
  }

  private JPanel createContentPanel() {
    SimpleToolWindowPanel contentPanel = new SimpleToolWindowPanel(false);
    OnePixelSplitter splitter = new OnePixelSplitter(false, 0.25f);
    splitter.setFirstComponent(setupTreePanel());
    splitter.setSecondComponent(setupConsolePanel());
    contentPanel.setContent(splitter);
    return contentPanel;
  }

  private JPanel setupTreePanel() {
    // similar to ServiceViewTreeUi(...) and ServiceViewTreeUi.setMasterComponent(...)
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(
        ScrollPaneFactory.createScrollPane(
            new TasksTreeView(model.getTreeModel(), parentDisposable).getComponent(),
            SideBorder.NONE),
        BorderLayout.CENTER);
    return panel;
  }

  private JPanel setupConsolePanel() {
    // similar to ServiceViewTreeUi(...) and ServiceViewTreeUi.setDetailsComponent(...)
    consolePanel.add(noSelectionPanel, BorderLayout.CENTER);
    return consolePanel;
  }

  private void treeSelectionChanged(
      ObservableValue<? extends Task> property, Task oldTask, Task newTask) {
    setConsoleComponent(
        newTask == null ? noSelectionPanel : model.getConsolesOfTasks().get(newTask).getContent());
  }

  private void setConsoleComponent(JComponent component) {
    if (component.getParent() == consolePanel) {
      return;
    }
    consolePanel.removeAll();
    consolePanel.add(component, BorderLayout.CENTER);
    consolePanel.revalidate();
    consolePanel.repaint();
  }
}
