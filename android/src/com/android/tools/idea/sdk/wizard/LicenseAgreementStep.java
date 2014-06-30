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
package com.android.tools.idea.sdk.wizard;

import com.android.sdklib.internal.repository.packages.License;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.wizard.ConfigureAndroidProjectPath;
import com.android.tools.idea.wizard.DynamicWizardStepWithHeaderAndDescription;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.wizard.ConfigureAndroidProjectPath.INSTALL_REQUESTS_KEY;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * A review step for reviewing the changes about to be made and accepting the required licences.
 */
public class LicenseAgreementStep extends DynamicWizardStepWithHeaderAndDescription {
  private JTextPane myLicenseTextField;
  private Tree myChangeTree;
  private JPanel myComponent;
  private JRadioButton myDeclineRadioButton;
  private JRadioButton myAcceptRadioButton;

  private DefaultTreeModel myTreeModel = new DefaultTreeModel(null);

  private Map<String, Boolean> myAcceptances = Maps.newHashMap();

  private String myCurrentLicense;

  public LicenseAgreementStep(@NotNull Disposable disposable) {
    super("License Agreement", "Read and agree to the licenses for the components which will be installed", null, disposable);
    setBodyComponent(myComponent);
  }

  @Override
  public void init() {
    super.init();
    myChangeTree.setModel(myTreeModel);
    myChangeTree.setShowsRootHandles(false);
    myLicenseTextField.setEditable(false);

    // Initialize radio buttons
    ButtonGroup group = new ButtonGroup();
    group.add(myDeclineRadioButton);
    group.add(myAcceptRadioButton);

    myDeclineRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAcceptances.put(myCurrentLicense, Boolean.FALSE);
        invokeUpdate(null);
      }
    });

    myAcceptRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAcceptances.put(myCurrentLicense, Boolean.TRUE);
        invokeUpdate(null);
      }
    });

    myChangeTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode)myChangeTree.getLastSelectedPathComponent();
        if (selected != null && selected.isRoot()) {
          return;
        }
        if (selected != null && !selected.isLeaf()) {
          License license = (License)selected.getUserObject();
          myLicenseTextField.setText(license.getLicense());
          myCurrentLicense = license.getLicenseRef();
        }
        else if (selected != null && !selected.isRoot()) {
          Change change = (Change)selected.getUserObject();
          myLicenseTextField.setText(change.license.getLicense());
          myCurrentLicense = change.license.getLicenseRef();
        }
        if (myAcceptances.get(myCurrentLicense)) {
          myAcceptRadioButton.setSelected(true);
        } else {
          myDeclineRadioButton.setSelected(true);
        }
        myLicenseTextField.setCaretPosition(0);
      }
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
          append(license.getLicenseRef(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        } else {
          Change change = (Change)node.getUserObject();
          if (change == null) {
            return;
          }
          append(change.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          setIcon(change.getIcon());
        }
      }
    });

    setChanges(createChangesList());
  }

  @Override
  public boolean isStepVisible() {
    return myState.listSize(INSTALL_REQUESTS_KEY) > 0;
  }

  @Override
  public boolean validate() {
    for (String s : myAcceptances.keySet()) {
      if (!myAcceptances.get(s)) {
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

  private void expandTree() {
    for (int i = 0; i < myChangeTree.getRowCount(); ++i) {
      myChangeTree.expandRow(i);
    }
  }

  private List<Change> createChangesList() {
    List<Change> toReturn = Lists.newArrayList();
    List requestedPackageNames = myState.get(INSTALL_REQUESTS_KEY);
    if (requestedPackageNames != null) {
      for (Object o : requestedPackageNames) {
        IPkgDesc desc = (IPkgDesc)o;
        if (desc.getAndroidVersion() != null && desc.getAndroidVersion().isPreview()) {
          toReturn.add(new Change(ChangeType.INSTALL, (IPkgDesc)o, AndroidSdkLicenseTemporaryData.HARDCODED_ANDROID_PREVIEW_SDK_LICENSE));
        } else {
          toReturn.add(new Change(ChangeType.INSTALL, (IPkgDesc)o, AndroidSdkLicenseTemporaryData.HARDCODED_ANDROID_SDK_LICENSE));
        }
      }
    }
    return toReturn;
  }

  public void setChanges(List<Change> changes) {
    Map<String, DefaultMutableTreeNode> licenseNodeMap = Maps.newHashMap();

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultMutableTreeNode firstChild = null;
    for (Change change : changes) {
      if (!licenseNodeMap.containsKey(change.license.getLicenseRef())) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(change.license);
        if (firstChild == null) {
          firstChild = n;
        }
        String licenceRef = change.license.getLicenseRef();
        licenseNodeMap.put(licenceRef, n);
        myAcceptances.put(licenceRef, Boolean.FALSE);
        root.add(n);
      }
      licenseNodeMap.get(change.license.getLicenseRef()).add(new DefaultMutableTreeNode(change));
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

  protected enum ChangeType {
    INSTALL,
    UPDATE,
    REMOVE
  }

  protected static class Change {
    public ChangeType type;
    public IPkgDesc packageDescription;
    public License license;

    public Change(@NotNull ChangeType type, @NotNull IPkgDesc packageDescription, @NotNull License license) {
      this.type = type;
      this.packageDescription = packageDescription;
      this.license = license;
    }

    @Override
    public String toString() {
      if (packageDescription.getListDescription() != null) {
        return packageDescription.getListDescription();
      } else {
        return "INCORRECT PACKAGE DESCRIPTION";
      }
    }

    public Icon getIcon() {
      switch (type) {
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
