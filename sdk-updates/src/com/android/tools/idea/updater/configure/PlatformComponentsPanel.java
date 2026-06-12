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

import static java.util.Comparator.naturalOrder;

import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.AndroidVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.Enumeration;
import java.util.Set;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 * Panel that shows all the packages corresponding to an AndroidVersion.
 */
public class PlatformComponentsPanel {
  private static final String PLATFORM_DETAILS_CHECKBOX_SELECTED = "updater.configure.platform.details.checkbox.selected";

  private final TreeTableView myPlatformSummaryTable;
  private final TreeTableView myPlatformDetailTable;
  private final JPanel myPlatformPanel;
  private final JCheckBox myPlatformDetailsCheckbox;
  private final JCheckBox myHideObsoletePackagesCheckbox;
  private final JPanel myPlatformLoadingPanel;
  private final JPanel myRootPanel;
  private boolean myModified;

  @VisibleForTesting
  public UpdaterTreeNode myPlatformDetailsRootNode;
  @VisibleForTesting
  public UpdaterTreeNode myPlatformSummaryRootNode;

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
    UpdaterTreeNode.Renderer renderer = new SummaryTreeNode.Renderer();

    ColumnInfo[] platformSummaryColumns =
      new ColumnInfo[]{new DownloadStatusColumnInfo(), new TreeColumnInfo("Name"), new ApiLevelColumnInfo(), new RevisionColumnInfo(),
        new StatusColumnInfo()};
    myPlatformSummaryRootNode = new RootNode();
    myPlatformSummaryTable = new TreeTableView(new ListTreeTableModelOnColumns(myPlatformSummaryRootNode, platformSummaryColumns));
    SdkUpdaterConfigPanel.setTreeTableProperties(myPlatformSummaryTable, renderer, myModificationListener);
    myPlatformSummaryTable.setColumnSelectionAllowed(false);

    ColumnInfo[] platformDetailColumns =
      new ColumnInfo[]{new DownloadStatusColumnInfo(), new TreeColumnInfo("Name"), new ApiLevelColumnInfo(), new RevisionColumnInfo(),
        new StatusColumnInfo()};
    myPlatformDetailsRootNode = new RootNode();
    myPlatformDetailTable = new TreeTableView(new ListTreeTableModelOnColumns(myPlatformDetailsRootNode, platformDetailColumns));
    SdkUpdaterConfigPanel.setTreeTableProperties(myPlatformDetailTable, renderer, myModificationListener);

    final JBScrollPane summaryScrollPane = new JBScrollPane(myPlatformSummaryTable);
    final JBScrollPane detailsScrollPane = new JBScrollPane(myPlatformDetailTable);
    myPlatformPanel = new JPanel(new CardLayout());
    myPlatformPanel.add(summaryScrollPane, "summary");
    myPlatformPanel.add(detailsScrollPane, "details");

    JBLabel platformLoadingLabel = new JBLabel("Looking for updates...");
    platformLoadingLabel.setForeground(JBColor.GRAY);

    myPlatformLoadingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
    myPlatformLoadingPanel.add(platformLoadingLabel);
    myPlatformLoadingPanel.add(new AsyncProcessIcon(CommonBundle.getLoadingTreeNodeText()));

    myHideObsoletePackagesCheckbox = new JCheckBox("Hide Obsolete Packages");
    myHideObsoletePackagesCheckbox.setSelected(true);
    myHideObsoletePackagesCheckbox.addActionListener(e -> updatePlatformItems());

    myPlatformDetailsCheckbox = new JCheckBox("Show Package Details");
    myPlatformDetailsCheckbox.setSelected(propertiesComponent.getBoolean(PLATFORM_DETAILS_CHECKBOX_SELECTED, false));
    myPlatformDetailsCheckbox.addActionListener(e -> {
      propertiesComponent.setValue(PLATFORM_DETAILS_CHECKBOX_SELECTED, myPlatformDetailsCheckbox.isSelected());
      updatePlatformTable();
    });

    final JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0));
    checkboxPanel.add(myHideObsoletePackagesCheckbox);
    checkboxPanel.add(myPlatformDetailsCheckbox);

    final JPanel bottomControlsPanel = new JPanel(new BorderLayout());
    bottomControlsPanel.add(myPlatformLoadingPanel, BorderLayout.WEST);
    bottomControlsPanel.add(checkboxPanel, BorderLayout.EAST);

    myRootPanel = new JPanel(new BorderLayout(0, JBUI.scale(10)));
    final JBLabel descriptionLabel = new JBLabel(
      "<html>Each Android SDK Platform package includes the Android platform and sources pertaining to an API level by default. Once installed, the IDE will automatically check for updates. Check \"show package details\" to display individual SDK components.</html>");
    myRootPanel.add(descriptionLabel, BorderLayout.NORTH);
    myRootPanel.add(myPlatformPanel, BorderLayout.CENTER);
    myRootPanel.add(bottomControlsPanel, BorderLayout.SOUTH);

    updatePlatformTable();
  }

  private void updatePlatformTable() {
    ((CardLayout)myPlatformPanel.getLayout()).show(myPlatformPanel, myPlatformDetailsCheckbox.isSelected() ? "details" : "summary");
  }

  private void updatePlatformItems() {
    myPlatformDetailsRootNode.removeAllChildren();
    myPlatformSummaryRootNode.removeAllChildren();
    myStates.clear();
    // Sort in reverse API level, and then forward comparing extension level.
    for (AndroidVersion version :
        ImmutableList.sortedCopyOf(
            AndroidVersion.API_LEVEL_ORDERING.reversed().thenComparing(naturalOrder()),
            myCurrentPackages.keySet())) {
      // When an API level is not parsed correctly, it is given API level 0, which is undefined and we should not show the package.
      if (version.getApiLevel() < 1) {
        continue;
      }
      Set<UpdaterTreeNode> versionNodes = Sets.newHashSet();
      UpdaterTreeNode marker = new ParentTreeNode(version);
      for (UpdatablePackage info : myCurrentPackages.get(version)) {
        RepoPackage pkg = info.getRepresentative();
        if (pkg.obsolete() && myHideObsoletePackagesCheckbox.isSelected()) {
          continue;
        }
        PackageNodeModel model = new PackageNodeModel(info, false);
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

  public JComponent getRootComponent() { return myRootPanel; }
}