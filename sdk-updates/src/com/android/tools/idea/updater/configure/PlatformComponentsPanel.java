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
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.CardLayout;
import java.awt.Insets;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
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
    setupUI();
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

  private void setupUI() {
    createUIComponents();
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
    myRootPanel.add(panel1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                null, 0, false));
    myPlatformDetailsCheckbox = new JCheckBox();
    myPlatformDetailsCheckbox.setText("Show Package Details");
    panel1.add(myPlatformDetailsCheckbox,
               new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPlatformLoadingPanel = new JPanel();
    myPlatformLoadingPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(myPlatformLoadingPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           null, null, null, 0, false));
    myPlatformLoadingLabel = new JBLabel();
    myPlatformLoadingLabel.setText("Looking for updates...");
    myPlatformLoadingPanel.add(myPlatformLoadingLabel,
                               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                   false));
    myPlatformLoadingPanel.add(myPlatformLoadingIcon,
                               new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    myHideObsoletePackagesCheckbox = new JCheckBox();
    myHideObsoletePackagesCheckbox.setSelected(true);
    myHideObsoletePackagesCheckbox.setText("Hide Obsolete Packages");
    panel1.add(myHideObsoletePackagesCheckbox,
               new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText(
      "<html>Each Android SDK Platform package includes the Android platform and sources pertaining to an API level by default. Once installed, the IDE will automatically check for updates. Check \"show package details\" to display individual SDK components.</html>");
    myRootPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPlatformPanel = new JPanel();
    myPlatformPanel.setLayout(new CardLayout(0, 0));
    myRootPanel.add(myPlatformPanel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                         null, null, 0, false));
    final JBScrollPane jBScrollPane1 = new JBScrollPane();
    myPlatformPanel.add(jBScrollPane1, "summary");
    jBScrollPane1.setViewportView(myPlatformSummaryTable);
    final JBScrollPane jBScrollPane2 = new JBScrollPane();
    myPlatformPanel.add(jBScrollPane2, "details");
    jBScrollPane2.setViewportView(myPlatformDetailTable);
  }

  public JComponent getRootComponent() { return myRootPanel; }
}