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

import com.android.tools.idea.editors.hprof.heaptable.HeapTableManager;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class HprofViewPanel implements Disposable {
  @NotNull private JPanel myContainer;
  @NotNull private JBRunnerTabs myNavigationTabs;

  private static final int DIVIDER_WIDTH = 4;

  @NotNull private HeapTableManager myHeapTableManager;

  public HprofViewPanel(@NotNull final Project project) {
    myNavigationTabs = new JBRunnerTabs(project, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    myNavigationTabs.setBorder(new EmptyBorder(0, 2, 0, 0));
    myNavigationTabs.setPaintBorder(0, 0, 0, 0);

    myHeapTableManager = new HeapTableManager(myNavigationTabs);

    JBPanel treePanel = new JBPanel();
    treePanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    treePanel.setBackground(JBColor.background());

    Wrapper treePanelWrapper = new Wrapper(treePanel);
    treePanelWrapper.setBorder(new EmptyBorder(0, 1, 0, 0));

    JBSplitter mainSplitter = new JBSplitter(true);
    mainSplitter.setFirstComponent(myNavigationTabs);
    mainSplitter.setSecondComponent(treePanelWrapper);
    mainSplitter.setDividerWidth(DIVIDER_WIDTH);

    myContainer = new JPanel(new BorderLayout());
    myContainer.add(mainSplitter);
  }

  public void setSnapshot(@NotNull Snapshot snapshot) {
    myHeapTableManager.setSnapshot(snapshot);
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @Override
  public void dispose() {

  }
}
