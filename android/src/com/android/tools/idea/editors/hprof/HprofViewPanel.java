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

import com.android.tools.idea.editors.hprof.tables.gcroottable.GcRootTable;
import com.android.tools.idea.editors.hprof.tables.heaptable.HeapTableManager;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabInfo;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HprofViewPanel implements Disposable {
  @NotNull private Project myProject;

  @NotNull private JPanel myContainer;
  @NotNull private JBRunnerTabs myNavigationTabs;

  private static final int DIVIDER_WIDTH = 4;

  @NotNull private HeapTableManager myHeapTableManager;
  private GcRootTable myGcRootTable;
  private Snapshot mySnapshot;

  public HprofViewPanel(@NotNull final Project project) {
    myProject = project;
    myNavigationTabs = new JBRunnerTabs(myProject, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
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
    mySnapshot = snapshot;

    myGcRootTable = new GcRootTable(mySnapshot);
    myNavigationTabs.addTab(new TabInfo(HeapTableManager.createNavigationSplitter(myGcRootTable, null)).setText("GC Roots")
                              .setSideComponent(createToolBar(myProject)));

    myHeapTableManager.setSnapshot(snapshot);
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @Override
  public void dispose() {

  }

  private static class ComputeDominatorIndicator extends Task.Backgroundable {
    @NotNull private final CountDownLatch myLatch;

    public ComputeDominatorIndicator(@NotNull Project project) {
      super(project, "Computing dominators...", true);
      myLatch = new CountDownLatch(1);
    }

    public void exit() {
      myLatch.countDown();
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);

      while (myLatch.getCount() > 0) {
        try {
          myLatch.await(200, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
          exit();
          break;
        }
      }
    }
  }

  private JToolBar createToolBar(@NotNull final Project project) {
    final JToggleButton dominatorButton = new JToggleButton(AndroidIcons.Ddms.AllocationTracker, false);
    dominatorButton.addItemListener(new ItemListener() {
      private boolean myDominatorsComputed = false;

      @Override
      public void itemStateChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() == ItemEvent.SELECTED && !myDominatorsComputed) {
          myDominatorsComputed = true;
          final ComputeDominatorIndicator indicator = new ComputeDominatorIndicator(project);
          ProgressManager.getInstance().run(indicator);

          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              mySnapshot.computeDominators();

              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  myHeapTableManager.notifyDominatorsComputed();
                  myGcRootTable.notifyDominatorsComputed();
                  indicator.exit();
                }
              });
            }
          });
        }
        else if (itemEvent.getStateChange() == ItemEvent.DESELECTED) {
          dominatorButton.setSelected(true);
        }
      }
    });
    JToolBar toolBar = new JToolBar();
    toolBar.add(dominatorButton);
    toolBar.setFloatable(false);

    return toolBar;
  }
}
