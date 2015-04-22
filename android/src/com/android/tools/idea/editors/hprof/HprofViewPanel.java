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
import com.android.tools.idea.editors.hprof.tables.instancestable.InstanceDetailView;
import com.android.tools.perflib.heap.Snapshot;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class HprofViewPanel implements Disposable {
  private static final int DIVIDER_WIDTH = 4;
  @NotNull private Project myProject;
  @NotNull private JPanel myContainer;
  @NotNull private JBRunnerTabs myNavigationTabs;
  @NotNull private JBRunnerTabs myDetailTabs;
  @NotNull private HeapTableManager myHeapTableManager;
  private GcRootTable myGcRootTable;
  private Snapshot mySnapshot;

  public HprofViewPanel(@NotNull final Project project) {
    myProject = project;

    myNavigationTabs = new JBRunnerTabs(myProject, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    myNavigationTabs.setBorder(new EmptyBorder(0, 2, 0, 0));
    myNavigationTabs.setPaintBorder(0, 0, 0, 0);

    myDetailTabs = new JBRunnerTabs(myProject, ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
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

    myHeapTableManager = new HeapTableManager(myProject, this, myNavigationTabs);

    JBPanel treePanel = new JBPanel(new BorderLayout());
    treePanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    treePanel.setBackground(JBColor.background());
    treePanel.add(myDetailTabs, BorderLayout.CENTER);

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
                              .setActions(new DefaultActionGroup(new ComputeDominatorAction(myProject)), ActionPlaces.UNKNOWN));

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

  private class ComputeDominatorAction extends ToggleAction {
    @NotNull Project myProject;
    private boolean myDominatorsComputed;

    private ComputeDominatorAction(@NotNull Project project) {
      super(null, "Compute Dominators", AndroidIcons.Ddms.AllocationTracker);
      myProject = project;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (!myDominatorsComputed && e.getInputEvent() != null) {
        myDominatorsComputed = true;
        final ComputeDominatorIndicator indicator = new ComputeDominatorIndicator(myProject);
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
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myDominatorsComputed;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myDominatorsComputed |= state;
    }
  }
}
