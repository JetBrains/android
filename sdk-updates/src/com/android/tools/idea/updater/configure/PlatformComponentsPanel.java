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

import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.AndroidVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.CardLayout;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 * Panel that shows all the packages corresponding to an AndroidVersion.
 */
public class PlatformComponentsPanel {
  private static final String PLATFORM_DETAILS_CHECKBOX_SELECTED = "updater.configure.platform.details.checkbox.selected";

  private TreeTableView myPlatformSummaryTable;
  private TreeTableView myPlatformDetailTable;
  private JPanel myPlatformPanel;
  private JCheckBox myPlatformDetailsCheckbox;
  private JCheckBox myHideObsoletePackagesCheckbox;
  private JPanel myPlatformLoadingPanel;
  private JBLabel myPlatformLoadingLabel;
  @SuppressWarnings("unused") private AsyncProcessIcon myPlatformLoadingIcon;
  @SuppressWarnings("unused") private JPanel myRootPanel;
  private boolean myModified;

  @VisibleForTesting
  UpdaterTreeNode myPlatformDetailsRootNode;
  @VisibleForTesting
  UpdaterTreeNode myPlatformSummaryRootNode;

  Set<PackageNodeModel> myStates = Sets.newHashSet();

  // map of versions to current subpackages
  private final Multimap<AndroidVersion, UpdatablePackage> myCurrentPackages = TreeMultimap.create();

  private final ChangeListener myModificationListener = new ChangeListener() {
    @Override
    public void stateChanged(ChangeEvent e) {
      refreshModified();
    }
  };
  private SdkUpdaterConfigurable myConfigurable;

  @SuppressWarnings("unused")
  PlatformComponentsPanel() {
    this(PropertiesComponent.getInstance());
  }

  @VisibleForTesting
  PlatformComponentsPanel(@NotNull PropertiesComponent propertiesComponent) {
    myPlatformSummaryTable.setColumnSelectionAllowed(false);
    myPlatformLoadingLabel.setForeground(JBColor.GRAY);

    myPlatformDetailsCheckbox.setSelected(propertiesComponent.getBoolean(PLATFORM_DETAILS_CHECKBOX_SELECTED, false));
    myPlatformDetailsCheckbox.addActionListener(e -> {
      propertiesComponent.setValue(PLATFORM_DETAILS_CHECKBOX_SELECTED, myPlatformDetailsCheckbox.isSelected());
      updatePlatformTable();
    });
    updatePlatformTable();

    myHideObsoletePackagesCheckbox.addActionListener(e -> updatePlatformItems());
  }

  private void updatePlatformTable() {
    ((CardLayout)myPlatformPanel.getLayout()).show(myPlatformPanel, myPlatformDetailsCheckbox.isSelected() ? "details" : "summary");
  }

  private void updatePlatformItems() {
    myPlatformDetailsRootNode.removeAllChildren();
    myPlatformSummaryRootNode.removeAllChildren();
    myStates.clear();
    List<AndroidVersion> versions = Lists.newArrayList(myCurrentPackages.keySet());
    versions = Lists.reverse(versions);
    // Sort in reverse API level, and then forward comparing extension level.
    versions.sort(((Comparator<AndroidVersion>)(o1, o2) -> o1.compareTo(o2.getApiLevel(), o2.getCodename()) * -1)
                    .thenComparing(AndroidVersion::compareTo));
    for (AndroidVersion version : versions) {
      // When an API level is not parsed correctly, it is given API level 0, which is undefined and we should not show the package.
      if (version.equals(AndroidVersion.VersionCodes.UNDEFINED)) {
        continue;
      }
      Set<UpdaterTreeNode> versionNodes = Sets.newHashSet();
      UpdaterTreeNode marker = new ParentTreeNode(version);
      for (UpdatablePackage info : myCurrentPackages.get(version)) {
        RepoPackage pkg = info.getRepresentative();
        if (pkg.obsolete() && myHideObsoletePackagesCheckbox.isSelected()) {
          continue;
        }
        PackageNodeModel model = new PackageNodeModel(info);
        myStates.add(model);
        UpdaterTreeNode node = new DetailsTreeNode(model, myModificationListener, myConfigurable);
        marker.add(node);
        versionNodes.add(node);
      }
      if (marker.getChildCount() > 0) {
        myPlatformDetailsRootNode.add(marker);
      }
      SummaryTreeNode node = SummaryTreeNode.createNode(version, versionNodes);
      if (node != null) {
        myPlatformSummaryRootNode.add(node);
      }
    }
    refreshModified();
    SdkUpdaterConfigPanel.resizeColumnsToFit(myPlatformDetailTable);
    SdkUpdaterConfigPanel.resizeColumnsToFit(myPlatformSummaryTable);
    myPlatformDetailTable.updateUI();
    myPlatformSummaryTable.updateUI();
    TreeUtil.expandAll(myPlatformDetailTable.getTree());
    TreeUtil.expandAll(myPlatformSummaryTable.getTree());
  }

  public void startLoading() {
    myCurrentPackages.clear();
    myPlatformLoadingPanel.setVisible(true);
  }

  public void finishLoading() {
    updatePlatformItems();
    myPlatformLoadingPanel.setVisible(false);
  }

  private void createUIComponents() {
    UpdaterTreeNode.Renderer renderer = new SummaryTreeNode.Renderer();

    myPlatformLoadingIcon = new AsyncProcessIcon("Loading...");
    myPlatformSummaryRootNode = new RootNode();
    myPlatformDetailsRootNode = new RootNode();
    ColumnInfo[] platformSummaryColumns =
      new ColumnInfo[]{new DownloadStatusColumnInfo(), new TreeColumnInfo("Name"), new ApiLevelColumnInfo(), new RevisionColumnInfo(),
        new StatusColumnInfo()};
    myPlatformSummaryTable = new TreeTableView(new ListTreeTableModelOnColumns(myPlatformSummaryRootNode, platformSummaryColumns));
    SdkUpdaterConfigPanel.setTreeTableProperties(myPlatformSummaryTable, renderer, myModificationListener);

    ColumnInfo[] platformDetailColumns =
      new ColumnInfo[]{new DownloadStatusColumnInfo(), new TreeColumnInfo("Name"), new ApiLevelColumnInfo(), new RevisionColumnInfo(),
        new StatusColumnInfo()};
    myPlatformDetailTable = new TreeTableView(new ListTreeTableModelOnColumns(myPlatformDetailsRootNode, platformDetailColumns));
    SdkUpdaterConfigPanel.setTreeTableProperties(myPlatformDetailTable, renderer, myModificationListener);
  }

  public void setPackages(@NotNull Multimap<AndroidVersion, UpdatablePackage> packages) {
    myCurrentPackages.clear();
    myCurrentPackages.putAll(packages);
    updatePlatformItems();
  }

  public boolean isModified() {
    return myModified;
  }

  public void refreshModified() {
    Enumeration items = myPlatformDetailsRootNode.breadthFirstEnumeration();
    while (items.hasMoreElements()) {
      UpdaterTreeNode node = (UpdaterTreeNode)items.nextElement();
      if (node.getInitialState() != node.getCurrentState()) {
        myModified = true;
        return;
      }
    }
    myModified = false;
  }

  public void reset() {
    for (Enumeration children = myPlatformDetailsRootNode.breadthFirstEnumeration(); children.hasMoreElements(); ) {
      UpdaterTreeNode node = (UpdaterTreeNode)children.nextElement();
      node.resetState();
    }
    refreshModified();
  }

  public void setEnabled(boolean enabled) {
    myPlatformDetailTable.setEnabled(enabled);
    myPlatformSummaryTable.setEnabled(enabled);
    myPlatformDetailsCheckbox.setEnabled(enabled);
  }

  public void setConfigurable(@NotNull SdkUpdaterConfigurable configurable) {
    myConfigurable = configurable;
  }
}