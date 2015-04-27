/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof;

import com.android.tools.idea.editors.hprof.tables.heaptable.HeapTableManager;
import com.android.tools.idea.editors.hprof.tables.instancestable.InstanceDetailView;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HprofViewPanel implements Disposable {
  private static final int DIVIDER_WIDTH = 4;
  @NotNull private JPanel myContainer;
  @NotNull private JBRunnerTabs myDetailTabs;
  @NotNull private HeapTableManager myHeapTableManager;

  public HprofViewPanel(@NotNull final Project project) {
    JBRunnerTabs navigationTabs = new JBRunnerTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    navigationTabs.setBorder(new EmptyBorder(0, 2, 0, 0));
    navigationTabs.setPaintBorder(0, 0, 0, 0);

    myDetailTabs = new JBRunnerTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    myDetailTabs.setBorder(new EmptyBorder(0, 2, 0, 0));
    myDetailTabs.setPaintBorder(0, 0, 0, 0);
    myDetailTabs.addTabMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          final TabInfo tabInfo = myDetailTabs.findInfo(e);
          if (tabInfo != null) {
            myDetailTabs.removeTab(tabInfo);
          }
        }
      }
    });

    myHeapTableManager = new HeapTableManager(project, this, navigationTabs);

    JBPanel treePanel = new JBPanel(new BorderLayout());
    treePanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    treePanel.setBackground(JBColor.background());
    treePanel.add(myDetailTabs, BorderLayout.CENTER);

    Wrapper treePanelWrapper = new Wrapper(treePanel);
    treePanelWrapper.setBorder(new EmptyBorder(0, 1, 0, 0));

    JBSplitter mainSplitter = new JBSplitter(true);
    mainSplitter.setFirstComponent(navigationTabs);
    mainSplitter.setSecondComponent(treePanelWrapper);
    mainSplitter.setDividerWidth(DIVIDER_WIDTH);

    myContainer = new JPanel(new BorderLayout());
    myContainer.add(mainSplitter);
  }

  public void setSnapshot(@NotNull Snapshot snapshot) {
    myHeapTableManager.setSnapshot(snapshot);
  }

  @Nullable
  public TabInfo findDetailPanel(@NotNull String title) {
    for (TabInfo tabInfo : myDetailTabs.getTabs()) {
      if (title.equals(tabInfo.getText())) {
        return tabInfo;
      }
    }
    return null;
  }

  public void createDetailPanel(@NotNull String title, @NotNull InstanceDetailView view, @NotNull JComponent sideComponent) {
    TabInfo info = new TabInfo(new JBScrollPane(view)).setText(title).setSideComponent(sideComponent);
    myDetailTabs.addTab(info);
    myDetailTabs.select(info, false);
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @Override
  public void dispose() {

  }
}
