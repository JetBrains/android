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
package com.android.tools.idea.updater.configure;

import com.android.repository.api.UpdatablePackage;
import com.google.common.collect.Sets;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Set;

/**
 * Panel that shows all packages not associated with an AndroidVersion.
 */
public class ToolComponentsPanel {
  private TreeTableView myToolsSummaryTable;
  private JCheckBox myToolsDetailsCheckbox;
  private JPanel myToolsPanel;
  private TreeTableView myToolsDetailTable;
  private JPanel myToolsLoadingPanel;
  @SuppressWarnings("unused") private AsyncProcessIcon myToolsLoadingIcon;
  @SuppressWarnings("unused") private JPanel myRootPanel;
  private final Set<UpdatablePackage> myToolsPackages = Sets.newTreeSet(new Comparator<UpdatablePackage>() {
    @Override
    public int compare(UpdatablePackage o1, UpdatablePackage o2) {
      // Since we won't have added these packages if they don't have something we care about.
      //noinspection ConstantConditions
      return o1.getRepresentative().getDisplayName().compareTo(o2.getRepresentative().getDisplayName());
    }
  });
  private final Set<UpdatablePackage> myBuildToolsPackages = Sets.newTreeSet();

  private UpdaterTreeNode myToolsDetailsRootNode;
  private UpdaterTreeNode myToolsSummaryRootNode;

  Set<NodeStateHolder> myStates = Sets.newHashSet();

  private boolean myModified = false;
  private boolean myIncludePreview;
  private final ChangeListener myModificationListener = new ChangeListener() {
    @Override
    public void stateChanged(ChangeEvent e) {
      refreshModified();
    }
  };

  public ToolComponentsPanel() {
    myToolsDetailsCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateToolsTable();
      }
    });
  }

  private void updateToolsTable() {
    ((CardLayout)myToolsPanel.getLayout()).show(myToolsPanel, myToolsDetailsCheckbox.isSelected() ? "details" : "summary");
  }

  private void updateToolsItems() {
    myToolsDetailsRootNode.removeAllChildren();
    myToolsSummaryRootNode.removeAllChildren();
    myStates.clear();

    Set<UpdaterTreeNode> buildToolsNodes = Sets.newHashSet();
    UpdaterTreeNode buildToolsParent = new ParentTreeNode(null) {
      @Override
      public void customizeRenderer(Renderer renderer,
                                    JTree tree,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
        renderer.getTextRenderer().append("Android SDK Build Tools");
      }
    };
    for (UpdatablePackage info : myBuildToolsPackages) {
      NodeStateHolder holder = new NodeStateHolder(info);
      myStates.add(holder);
      UpdaterTreeNode node = new PlatformDetailsTreeNode(holder, myModificationListener);
      buildToolsParent.add(node);
      buildToolsNodes.add(node);
    }
    myToolsDetailsRootNode.add(buildToolsParent);

    myToolsSummaryRootNode.add(new BuildToolsSummaryTreeNode(buildToolsNodes));

    for (UpdatablePackage info : myToolsPackages) {
      NodeStateHolder holder = new NodeStateHolder(info);
      myStates.add(holder);
      UpdaterTreeNode node = new PlatformDetailsTreeNode(holder, myModificationListener);
      myToolsDetailsRootNode.add(node);
      if (!info.getRepresentative().obsolete()) {
        myToolsSummaryRootNode.add(new PlatformDetailsTreeNode(holder, myModificationListener));
      }
    }
    refreshModified();
    SdkUpdaterConfigPanel.resizeColumnsToFit(myToolsDetailTable);
    SdkUpdaterConfigPanel.resizeColumnsToFit(myToolsSummaryTable);
    myToolsDetailTable.updateUI();
    myToolsSummaryTable.updateUI();
    TreeUtil.expandAll(myToolsDetailTable.getTree());
    TreeUtil.expandAll(myToolsSummaryTable.getTree());
  }

  public void setPackages(@NotNull Set<UpdatablePackage> toolsPackages, @NotNull Set<UpdatablePackage> buildToolsPackages) {
    myBuildToolsPackages.clear();
    myToolsPackages.clear();
    myBuildToolsPackages.addAll(buildToolsPackages);
    myToolsPackages.addAll(toolsPackages);
    updateToolsItems();
  }

  public void startLoading() {
    myToolsPackages.clear();
    myBuildToolsPackages.clear();
    myToolsLoadingPanel.setVisible(true);
  }

  public void finishLoading() {
    updateToolsItems();
    myToolsLoadingPanel.setVisible(false);
  }

  public void reset() {
    for (Enumeration children = myToolsDetailsRootNode.breadthFirstEnumeration(); children.hasMoreElements(); ) {
      UpdaterTreeNode node = (UpdaterTreeNode)children.nextElement();
      node.resetState();
    }
    refreshModified();
  }

  public boolean isModified() {
    return myModified;
  }

  public void refreshModified() {
    Enumeration items = myToolsDetailsRootNode.breadthFirstEnumeration();
    while (items.hasMoreElements()) {
      UpdaterTreeNode node = (UpdaterTreeNode)items.nextElement();
      if (node.getInitialState() != node.getCurrentState()) {
        myModified = true;
        return;
      }
    }
    myModified = false;
  }

  private void createUIComponents() {
    myToolsLoadingIcon = new AsyncProcessIcon("Loading...");

    myToolsSummaryRootNode = new RootNode();
    myToolsDetailsRootNode = new RootNode();

    UpdaterTreeNode.Renderer renderer = new SummaryTreeNode.Renderer();

    ColumnInfo[] toolsSummaryColumns =
      new ColumnInfo[]{new DownloadStatusColumnInfo(), new TreeColumnInfo("Name"), new VersionColumnInfo(), new StatusColumnInfo()};
    myToolsSummaryTable = new TreeTableView(new ListTreeTableModelOnColumns(myToolsSummaryRootNode, toolsSummaryColumns));

    SdkUpdaterConfigPanel.setTreeTableProperties(myToolsSummaryTable, renderer, myModificationListener);

    ColumnInfo[] toolsDetailColumns =
      new ColumnInfo[]{new DownloadStatusColumnInfo(), new TreeColumnInfo("Name"), new VersionColumnInfo(), new StatusColumnInfo()};
    myToolsDetailTable = new TreeTableView(new ListTreeTableModelOnColumns(myToolsDetailsRootNode, toolsDetailColumns));
    SdkUpdaterConfigPanel.setTreeTableProperties(myToolsDetailTable, renderer, myModificationListener);
  }

  public void clearState() {
    myStates.clear();
  }
}
