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

import static com.android.repository.util.RepoPackageUtilKt.getRepoPackagePrefix;
import static com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated;
import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.repository.installer.MavenInstallListener;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.install.patch.PatchInstallerUtil;
import com.android.tools.idea.welcome.install.Aehd;
import com.android.tools.idea.welcome.install.Haxm;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.ui.treeStructure.treetable.TreeColumnInfo;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.CardLayout;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 * Panel that shows all packages not associated with an AndroidVersion.
 */
public class ToolComponentsPanel {
  private static final Set<String> MULTI_VERSION_PREFIXES =
    ImmutableSet.of(SdkConstants.FD_BUILD_TOOLS, SdkConstants.FD_LLDB, SdkConstants.FD_CMAKE,
                    SdkConstants.FD_NDK_SIDE_BY_SIDE, SdkConstants.FD_CMDLINE_TOOLS,
                    String.join(String.valueOf(RepoPackage.PATH_SEPARATOR),
                                SdkConstants.FD_EXTRAS,
                                SdkConstants.FD_ANDROID_EXTRAS,
                                SdkConstants.FD_GAPID));

  // TODO: Add more fine-grained support for ChromeOS to SDK repo infrastructure (b/131738330)
  private static final Set<String> CHROME_OS_INCOMPATIBLE_PATHS =
    ImmutableSet.of(SdkConstants.FD_EMULATOR, Haxm.InstallerInfo.getRepoPackagePath(), Aehd.InstallerInfo.getRepoPackagePath());

  private static final String TOOLS_DETAILS_CHECKBOX_SELECTED = "updater.configure.tools.details.checkbox.selected";

  private TreeTableView myToolsSummaryTable;
  private JCheckBox myToolsDetailsCheckbox;
  private JCheckBox myHideObsoletePackagesCheckbox;
  private JPanel myToolsPanel;
  private TreeTableView myToolsDetailTable;
  private JPanel myToolsLoadingPanel;
  @SuppressWarnings("unused") private AsyncProcessIcon myToolsLoadingIcon;
  @SuppressWarnings("unused") private JPanel myRootPanel;
  private final Set<UpdatablePackage> myToolsPackages = Sets.newTreeSet((o1, o2) -> {
    // Since we won't have added these packages if they don't have something we care about.
    return ComparisonChain.start()
      .compare(o1.getRepresentative().getDisplayName(), o2.getRepresentative().getDisplayName())
      .compare(o1.getRepresentative().getPath(), o2.getRepresentative().getPath())
      .result();
  });
  private final Multimap<String, UpdatablePackage> myMultiVersionPackages = HashMultimap.create();
  private final Multimap<String, UpdatablePackage> myMavenPackages = HashMultimap.create();

  @VisibleForTesting
  UpdaterTreeNode myToolsDetailsRootNode;
  @VisibleForTesting
  UpdaterTreeNode myToolsSummaryRootNode;

  Set<PackageNodeModel> myStates = Sets.newHashSet();

  private boolean myModified = false;
  private final ChangeListener myModificationListener = e -> refreshModified();
  private SdkUpdaterConfigurable myConfigurable;

  @SuppressWarnings("unused")
  ToolComponentsPanel() {
    this(PropertiesComponent.getInstance());
  }

  @VisibleForTesting
  ToolComponentsPanel(@NotNull PropertiesComponent propertiesComponent) {
    myToolsDetailsCheckbox.setSelected(propertiesComponent.getBoolean(TOOLS_DETAILS_CHECKBOX_SELECTED, false));
    myToolsDetailsCheckbox.addActionListener(e -> {
      propertiesComponent.setValue(TOOLS_DETAILS_CHECKBOX_SELECTED, myToolsDetailsCheckbox.isSelected());
      updateToolsTable();
    });
    updateToolsTable();

    myHideObsoletePackagesCheckbox.addActionListener(e -> updateToolsItems());
  }

  private void updateToolsTable() {
    ((CardLayout)myToolsPanel.getLayout()).show(myToolsPanel, myToolsDetailsCheckbox.isSelected() ? "details" : "summary");
  }

  private void updateToolsItems() {
    myToolsDetailsRootNode.removeAllChildren();
    myToolsSummaryRootNode.removeAllChildren();
    myStates.clear();

    for (String prefix : myMultiVersionPackages.keySet()) {
      Collection<UpdatablePackage> versions = myMultiVersionPackages.get(prefix);
      // TODO: maybe support "latest" in the repo infrastructure?
      Set<DetailsTreeNode> detailsNodes =
        new TreeSet<>(Comparator.<DetailsTreeNode, Revision>comparing(node -> node.getPackage().getVersion())
                        .thenComparing(node -> node.getPackage().getPath().endsWith("latest"))
                        .reversed());
      for (UpdatablePackage info : versions) {
        RepoPackage representative = info.getRepresentative();
        if (representative.getTypeDetails() instanceof DetailsTypes.MavenType) {
          // Maven repository in the SDK manager is deprecated. So we should not visualise it here.
          // TODO: Mark the packages as deprecated on the server side.
          continue;
        }

        PackageNodeModel model = new PackageNodeModel(info);
        if (model.obsolete() && myHideObsoletePackagesCheckbox.isSelected()) {
          continue;
        }
        myStates.add(model);

        detailsNodes.add(new DetailsTreeNode(model, myModificationListener, myConfigurable));
      }
      if (!detailsNodes.isEmpty()) {
        MultiVersionTreeNode summaryNode = new MultiVersionTreeNode(detailsNodes);
        myToolsSummaryRootNode.add(summaryNode);

        UpdaterTreeNode multiVersionParent = new ParentTreeNode(summaryNode.getDisplayName());
        detailsNodes.forEach(multiVersionParent::add);
        myToolsDetailsRootNode.add(multiVersionParent);
      }
    }
    for (UpdatablePackage info : myToolsPackages) {
      boolean isMaven = info.getPath().endsWith(RepoPackage.PATH_SEPARATOR + MavenInstallListener.MAVEN_DIR_NAME);
      if (isMaven) {
        continue;
      }

      PackageNodeModel holder = new PackageNodeModel(info);
      if (holder.obsolete() && myHideObsoletePackagesCheckbox.isSelected()) {
        continue;
      }
      myStates.add(holder);
      UpdaterTreeNode node = new DetailsTreeNode(holder, myModificationListener, myConfigurable);
      myToolsDetailsRootNode.add(node);
      UpdaterTreeNode summaryNode = new DetailsTreeNode(holder, myModificationListener, myConfigurable);
      myToolsSummaryRootNode.add(summaryNode);
    }

    refreshModified();
    SdkUpdaterConfigPanel.resizeColumnsToFit(myToolsDetailTable);
    SdkUpdaterConfigPanel.resizeColumnsToFit(myToolsSummaryTable);
    myToolsDetailTable.updateUI();
    myToolsSummaryTable.updateUI();
    TreeUtil.expandAll(myToolsDetailTable.getTree());
    TreeUtil.expandAll(myToolsSummaryTable.getTree());
  }

  public void setPackages(@NotNull Set<UpdatablePackage> toolsPackages) {
    myMultiVersionPackages.clear();
    myToolsPackages.clear();
    for (UpdatablePackage p : toolsPackages) {
      String path = p.getRepresentative().getPath();
      if (shouldAlwaysHide(path)) {
        continue;
      }

      boolean found = false;
      String prefix = getRepoPackagePrefix(path);
      if (MULTI_VERSION_PREFIXES.contains(prefix) || p.getRepresentative().getTypeDetails() instanceof DetailsTypes.MavenType) {
        myMultiVersionPackages.put(prefix, p);
        found = true;
      }
      if (!found) {
        myToolsPackages.add(p);
      }
    }
    updateToolsItems();
  }

  private static boolean shouldAlwaysHide(@NotNull String path) {
    String prefix = getRepoPackagePrefix(path);
    if (prefix.equals(PatchInstallerUtil.PATCHER_PATH_PREFIX)) {
      // We don't want to show the patcher in the UI
      return true;
    }
    if (isChromeOSAndIsNotHWAccelerated() && CHROME_OS_INCOMPATIBLE_PATHS.contains(path)) {
      return true;
    }
    return false;
  }

  public void startLoading() {
    myToolsPackages.clear();
    myMultiVersionPackages.clear();
    myMavenPackages.clear();
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

  public void setConfigurable(@NotNull SdkUpdaterConfigurable configurable) {
    myConfigurable = configurable;
  }
}
