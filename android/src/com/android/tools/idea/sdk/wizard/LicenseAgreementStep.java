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
package com.android.tools.idea.sdk.wizard;

import com.android.repository.api.License;
import com.android.repository.api.RemotePackage;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.StartupUiUtil;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ModelWizardStep} that displays all the licenses related to the packages the user is about to install
 * and prompts the user to accept them prior to installation.
 */
public class LicenseAgreementStep extends ModelWizardStep<LicenseAgreementModel> {

  private JTextPane myLicenseTextField;
  private Tree myChangeTree;
  private JBRadioButton myDeclineRadioButton;
  private JBRadioButton myAcceptRadioButton;
  private JPanel myRootPanel;
  private Splitter splitter;
  private JPanel optionsPanel;
  private JBScrollPane myTreeScroll;
  private JBScrollPane myLicensePane;

  private DefaultTreeModel myTreeModel = new DefaultTreeModel(null);
  @Nullable private String myCurrentLicense;

  // Licenses accepted by the user.
  private final Map<String, Boolean> myAcceptances = Maps.newHashMap();

  // Only licenses that have not been accepted in the past by the user are displayed.
  private final Set<String> myVisibleLicenses = Sets.newHashSet();

  // All package paths that will get installed.
  private final List<RemotePackage> myInstallRequests;

  // True when all the visible licenses have been accepted.
  private final BoolProperty myAllLicensesAreAccepted = new BoolValueProperty();

  public LicenseAgreementStep(@NotNull LicenseAgreementModel model, @NotNull List<RemotePackage> installRequests) {
    super(model, "License Agreement");
    myInstallRequests = installRequests;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    createUI();
    initUI();
  }

  private void createUI() {
    splitter.setHonorComponentsMinimumSize(true);

    ButtonGroup optionsGroup = new ButtonGroup();
    optionsGroup.add(myDeclineRadioButton);
    optionsGroup.add(myAcceptRadioButton);

    myRootPanel.add(splitter, BorderLayout.CENTER);
    myRootPanel.add(optionsPanel, BorderLayout.SOUTH);

    myLicenseTextField.setFont(StartupUiUtil.getLabelFont());
  }

  private void initUI() {
    myChangeTree.setModel(myTreeModel);
    myChangeTree.setShowsRootHandles(false);
    myLicenseTextField.setEditable(false);

    final SelectedProperty accepted = new SelectedProperty(myAcceptRadioButton);
    accepted.addListener(() -> {
      myAcceptances.put(myCurrentLicense, accepted.get());
      checkAllLicensesAreAccepted();
      myChangeTree.repaint();
    });

    myChangeTree.addTreeSelectionListener(createTreeSelectionListener());
    myChangeTree.setCellRenderer(createCellRenderer());
    setChanges(createChangesList());
  }

  private TreeSelectionListener createTreeSelectionListener() {
    return e -> {
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
    };
  }

  private ColoredTreeCellRenderer createCellRenderer() {
    return new ColoredTreeCellRenderer() {
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
          setIcon(AllIcons.Actions.Download);
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
    };
  }

  /**
   * Ensures that all the nodes in the tree are expanded and viewable.
   */
  private void expandTree() {
    for (int i = 0; i < myChangeTree.getRowCount(); ++i) {
      myChangeTree.expandRow(i);
    }
  }

  /**
   * Respond to a new set of changes, by modifying which licenses the user will need to accept to proceed,
   * and updating related UI components.
   */
  private void setChanges(List<Change> changes) {
    Map<String, DefaultMutableTreeNode> licenseNodeMap = Maps.newHashMap();
    myVisibleLicenses.clear();

    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultMutableTreeNode firstChild = null;
    //For every change in the list, if we don't have that license in our tree we add it with a declined value
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
    // We expand the tree and change the selection path to reflect changes
    expandTree();
    if (firstChild != null) {
      myChangeTree.setSelectionPath(new TreePath(firstChild.getPath()));
    }
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Override
  protected boolean shouldShow() {
    return !myVisibleLicenses.isEmpty() && !getModel().getLicenses().isEmpty();
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myChangeTree;
  }

  private void checkAllLicensesAreAccepted() {
    myAllLicensesAreAccepted.set(true);
    for (String licenseRef : myVisibleLicenses) {
      if (!myAcceptances.get(licenseRef)) {
        myAllLicensesAreAccepted.set(false);
        break;
      }
    }
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myAllLicensesAreAccepted;
  }

  private List<Change> createChangesList() {
    List<Change> toReturn = new ArrayList<>();
    if (myInstallRequests != null) {
      for (RemotePackage p : myInstallRequests) {
        License license = p.getLicense();
        if (license != null) {
          getModel().getLicenses().add(license);
          if (!license.checkAccepted(getModel().getSdkRoot().getValue())) {
            toReturn.add(new Change(p, license));
          }
        }
      }
    }
    return toReturn;
  }

  private void createUIComponents() {
    optionsPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
  }

  private final static class Change {
    public RemotePackage myPackage;
    public License license;

    public Change(@NotNull RemotePackage p, @NotNull License license) {
      this.myPackage = p;
      this.license = license;
    }

    @Override
    public String toString() {
      return myPackage.getDisplayName();
    }
  }
}

