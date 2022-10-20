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
package com.android.tools.idea.sdk.wizard.legacy;

import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.wizard.AndroidSdkLicenseTemporaryData;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.treeStructure.Tree;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A review step for reviewing the changes about to be made and accepting the required licences.
 *
 * @deprecated Replaced by {@link com.android.tools.idea.sdk.wizard.LicenseAgreementStep}
 */
public class LicenseAgreementStep extends DynamicWizardStepWithDescription {
  private final JTextPane myLicenseTextField;
  private final Tree myChangeTree;
  private final JRadioButton myDeclineRadioButton;
  private final JRadioButton myAcceptRadioButton;

  private DefaultTreeModel myTreeModel = new DefaultTreeModel(null);
  private final Map<String, Boolean> myAcceptances = Maps.newHashMap();
  private final Set<String> myVisibleLicenses = Sets.newHashSet();
  private String myCurrentLicense;
  private final Set<License> myLicenses = Sets.newHashSet();
  private final Supplier<List<String>> myInstallRequestsProvider;
  private final Supplier<AndroidSdkHandler> mySdkHandlerSupplier;

  /**
   * @param installRequestsProvider Provides a list of {@link RepoPackage#getPath() remote package} paths. See
   *                                also {@link DetailsTypes.MavenType#getRepositoryPath(String, String, String)}
   */
  public LicenseAgreementStep(@NotNull Disposable parentDisposable,
                              @NotNull Supplier<List<String>> installRequestsProvider,
                              @NotNull Supplier<AndroidSdkHandler> sdkHandlerSupplier) {
    super(parentDisposable);

    myInstallRequestsProvider = installRequestsProvider;
    mySdkHandlerSupplier = sdkHandlerSupplier;
    Splitter splitter = new Splitter(false, .30f);
    splitter.setHonorComponentsMinimumSize(true);

    myChangeTree = new Tree();
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myChangeTree));

    myLicenseTextField = new JTextPane();
    splitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myLicenseTextField));

    myDeclineRadioButton = new JBRadioButton("Decline");
    myAcceptRadioButton = new JBRadioButton("Accept");

    ButtonGroup optionsGroup = new ButtonGroup();
    optionsGroup.add(myDeclineRadioButton);
    optionsGroup.add(myAcceptRadioButton);

    JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    optionsPanel.add(myDeclineRadioButton);
    optionsPanel.add(myAcceptRadioButton);

    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.add(splitter, BorderLayout.CENTER);
    mainPanel.add(optionsPanel, BorderLayout.SOUTH);

    setBodyComponent(mainPanel);

    initUI();
  }

  private void initUI() {
    myChangeTree.setModel(myTreeModel);
    myChangeTree.setShowsRootHandles(false);
    myLicenseTextField.setEditable(false);

    // Initialize radio buttons
    ButtonGroup group = new ButtonGroup();
    group.add(myDeclineRadioButton);
    group.add(myAcceptRadioButton);

    myDeclineRadioButton.addActionListener(e -> {
      myAcceptances.put(myCurrentLicense, Boolean.FALSE);
      invokeUpdate(null);
      myChangeTree.repaint();
    });

    myAcceptRadioButton.addActionListener(e -> {
      myAcceptances.put(myCurrentLicense, Boolean.TRUE);
      invokeUpdate(null);
      myChangeTree.repaint();
    });

    myChangeTree.addTreeSelectionListener(e -> {
      DefaultMutableTreeNode selected = (DefaultMutableTreeNode)myChangeTree.getLastSelectedPathComponent();
      if (selected != null && selected.isRoot()) {
        return;
      }
      if (selected != null && !selected.isLeaf()) {
        License license = (License)selected.getUserObject();
        myLicenseTextField.setText(license.getValue());
        myCurrentLicense = license.getId();
      }
      else if (selected != null && !selected.isRoot()) {
        Change change = (Change)selected.getUserObject();
        myLicenseTextField.setText(change.license.getValue());
        myCurrentLicense = change.license.getId();
      }
      if (myAcceptances.get(myCurrentLicense)) {
        myAcceptRadioButton.setSelected(true);
      }
      else {
        myDeclineRadioButton.setSelected(true);
      }
      myLicenseTextField.setCaretPosition(0);
    });

    myChangeTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {

        if (row == 0) {
          append("Licenses", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        if (!leaf) {
          License license = (License)node.getUserObject();
          appendLicenseText(license, license.getId());
        }
        else {
          Change change = (Change)node.getUserObject();
          if (change == null) {
            return;
          }
          appendLicenseText(change.license, change.toString());
          setIcon(change.getIcon());
        }
      }

      private void appendLicenseText(@Nullable License license, String text) {
        boolean notAccepted = license != null && !myAcceptances.get(license.getId());
        if (notAccepted) {
          append("*", SimpleTextAttributes.ERROR_ATTRIBUTES);
          append(text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else {
          append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });

    setChanges(createChangesList());
  }

  @Override
  public boolean isStepVisible() {
    return !myVisibleLicenses.isEmpty();
  }

  @Override
  public boolean validate() {
    for (String licenseRef : myVisibleLicenses) {
      if (!myAcceptances.get(licenseRef)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "License Agreement";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "License Agreement";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return "Read and agree to the licenses for the components which will be installed";
  }

  private void expandTree() {
    for (int i = 0; i < myChangeTree.getRowCount(); ++i) {
      myChangeTree.expandRow(i);
    }
  }

  private List<Change> createChangesList() {
    ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    RepoManager sdkManager = mySdkHandlerSupplier.get().getSdkManager(progress);
    sdkManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, new StudioDownloader(),
                                                         StudioSettingsController.getInstance());
    Map<String, RemotePackage> remotePackages = sdkManager.getPackages().getRemotePackages();
    List<Change> toReturn = new ArrayList<>();
    List<String> requestedPackages = myInstallRequestsProvider.get();

    if (requestedPackages != null) {
      Path sdkRoot = mySdkHandlerSupplier.get().getLocation();
      for (String path : requestedPackages) {
        RemotePackage p = remotePackages.get(path);
        License license = p.getLicense();
        if (license == null) {
            license = AndroidSdkLicenseTemporaryData.INSTANCE.getLicense(
              p.getTypeDetails() instanceof DetailsTypes.ApiDetailsType &&
              ((DetailsTypes.ApiDetailsType)p.getTypeDetails()).getAndroidVersion().isPreview());
        }
        myLicenses.add(license);
        if (!license.checkAccepted(sdkRoot)) {
          toReturn.add(new Change(ChangeType.INSTALL, p, license));
        }
      }
    }
    return toReturn;
  }

  private void setChanges(List<Change> changes) {
    Map<String, DefaultMutableTreeNode> licenseNodeMap = Maps.newHashMap();
    myVisibleLicenses.clear();

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultMutableTreeNode firstChild = null;
    for (Change change : changes) {
      String licenseRef = change.license.getId();
      myVisibleLicenses.add(licenseRef);
      if (!licenseNodeMap.containsKey(licenseRef)) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(change.license);
        if (firstChild == null) {
          firstChild = n;
        }
        licenseNodeMap.put(licenseRef, n);
        myAcceptances.put(licenseRef, Boolean.FALSE);
        root.add(n);
      }
      licenseNodeMap.get(licenseRef).add(new DefaultMutableTreeNode(change));
    }
    myTreeModel = new DefaultTreeModel(root);
    myChangeTree.setModel(myTreeModel);
    expandTree();
    if (firstChild != null) {
      myChangeTree.setSelectionPath(new TreePath(firstChild.getPath()));
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myChangeTree;
  }

  public void performFinishingActions() {
    Path sdkRoot = mySdkHandlerSupplier.get().getLocation();
    for (License license : myLicenses) {
      license.setAccepted(sdkRoot);
    }
  }

  public void reload() {
    setChanges(createChangesList());
  }

  protected enum ChangeType {
    INSTALL,
    UPDATE,
    REMOVE
  }

  protected static class Change {
    public ChangeType myType;
    public RepoPackage myPackage;
    public License license;

    public Change(@NotNull ChangeType type, @NotNull RepoPackage packageDescription, @NotNull License license) {
      this.myType = type;
      this.myPackage = packageDescription;
      this.license = license;
    }

    @Override
    public String toString() {
      return myPackage.getDisplayName();
    }

    public Icon getIcon() {
      switch (myType) {
        case INSTALL:
          return AllIcons.Actions.Download;
        case UPDATE:
          return AllIcons.Actions.Refresh;
        case REMOVE:
          return AllIcons.Actions.Cancel;
        default:
          return null;
      }
    }
  }
}
