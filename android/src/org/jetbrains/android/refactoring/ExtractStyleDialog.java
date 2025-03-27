/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.refactoring;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AndroidDependenciesCache;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import org.jetbrains.android.actions.CreateXmlResourceDialog;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

class ExtractStyleDialog extends DialogWrapper {

  private JPanel myPanel;
  private JTextField myStyleNameField;
  private JPanel myAttributeListWrapper;
  private JBLabel myAttributesLabel;
  private JBLabel myModuleLabel;
  private ModulesComboBox myModuleCombo;
  private JBCheckBox mySearchForStyleApplicationsAfter;

  private final Module myModule;
  private final String myFileName;
  private final List<String> myDirNames;

  private final CheckboxTree myTree;
  private final CheckedTreeNode myRootNode;

  private static final String SEARCH_STYLE_APPLICATIONS_PROPERTY = "AndroidExtractStyleSearchStyleApplications";

  public ExtractStyleDialog(@NotNull Module module,
                            @NotNull String fileName,
                            @Nullable String parentStyleName,
                            @NotNull List<String> dirNames,
                            @NotNull List<XmlAttribute> attributes) {
    super(module.getProject());
    setupUI();
    myFileName = fileName;
    myDirNames = dirNames;

    if (parentStyleName != null && !parentStyleName.isEmpty()) {
      myStyleNameField.setText(parentStyleName + ".");
      myStyleNameField.selectAll();
    }
    else {
      String prefix = IdeResourcesUtil.prependResourcePrefix(module, null, ResourceFolderType.VALUES);
      if (prefix != null) {
        myStyleNameField.setText(prefix);
      }
    }

    final Set<Module> modulesSet = new HashSet<Module>();
    modulesSet.add(module);

    for (AndroidFacet depFacet : AndroidDependenciesCache.getAllAndroidDependencies(module, true)) {
      modulesSet.add(depFacet.getModule());
    }

    assert !modulesSet.isEmpty();

    if (modulesSet.size() == 1) {
      myModule = module;
      myModuleLabel.setVisible(false);
      myModuleCombo.setVisible(false);
    }
    else {
      myModule = null;
      myModuleCombo.setModules(modulesSet);
      myModuleCombo.setSelectedModule(module);
    }

    myRootNode = new CheckedTreeNode(null);

    for (XmlAttribute attribute : attributes) {
      myRootNode.add(new CheckedTreeNode(attribute));
    }

    CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof CheckedTreeNode) {
          XmlAttribute attribute = (XmlAttribute)((CheckedTreeNode)value).getUserObject();
          if (attribute != null) {
            getTextRenderer().append(attribute.getLocalName());
            getTextRenderer().append(" [" + attribute.getValue() + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
        }
      }
    };
    myTree = new CheckboxTree(renderer, myRootNode) {
      @Override
      protected void installSpeedSearch() {
        TreeSpeedSearch.installOn(this, false, new Function<TreePath, String>() {
          @Override
          public String apply(TreePath path) {
            Object object = path.getLastPathComponent();
            if (object instanceof CheckedTreeNode) {
              XmlAttribute attribute = (XmlAttribute)((CheckedTreeNode)object).getUserObject();
              if (attribute != null) {
                return attribute.getLocalName();
              }
            }
            return "";
          }
        });
      }
    };
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    TreeUtil.expandAll(myTree);

    myAttributesLabel.setLabelFor(myTree);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myTree);
    decorator.setToolbarPosition(ActionToolbarPosition.RIGHT);
    decorator.setEditAction(null);
    decorator.disableUpDownActions();

    AnAction selectAll = new DumbAwareAction("Select All", null, PlatformIcons.SELECT_ALL_ICON) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        setChecked(true);
      }
    };
    decorator.addExtraAction(selectAll);

    AnAction
      unselectAll = new DumbAwareAction("Unselect All", null, PlatformIcons.UNSELECT_ALL_ICON) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        setChecked(false);
      }
    };
    decorator.addExtraAction(unselectAll);

    myAttributeListWrapper.add(decorator.createPanel());

    final String value = PropertiesComponent.getInstance().getValue(SEARCH_STYLE_APPLICATIONS_PROPERTY);
    mySearchForStyleApplicationsAfter.setSelected(Boolean.parseBoolean(value));
    init();
  }

  private void setChecked(boolean value) {
    int count = myRootNode.getChildCount();

    for (int i = 0; i < count; i++) {
      ((CheckedTreeNode)myRootNode.getChildAt(i)).setChecked(value);
    }
    myTree.repaint();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    PropertiesComponent.getInstance().setValue(
      SEARCH_STYLE_APPLICATIONS_PROPERTY, Boolean.toString(mySearchForStyleApplicationsAfter.isSelected()));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myStyleNameField;
  }

  @Override
  protected ValidationInfo doValidate() {
    final String styleName = getStyleName();

    if (styleName.isEmpty()) {
      return new ValidationInfo("specify style name", myStyleNameField);
    }
    if (!IdeResourcesUtil.isCorrectAndroidResourceName(styleName)) {
      return new ValidationInfo("incorrect style name", myStyleNameField);
    }
    final Module module = getChosenModule();
    if (module == null) {
      return new ValidationInfo("specify module", myModuleCombo);
    }
    VirtualFile resourceDir = getResourceDirectory();
    if (resourceDir == null) {
      return new ValidationInfo("specify a module with resources", myModuleCombo);
    }

    return CreateXmlResourceDialog.checkIfResourceAlreadyExists(module.getProject(), resourceDir, getStyleName(), null, ResourceType.STYLE,
                                                                myDirNames, myFileName);
  }

  @NotNull
  public String getStyleName() {
    return myStyleNameField.getText().trim();
  }

  @NotNull
  public List<XmlAttribute> getStyledAttributes() {
    List<XmlAttribute> attributes = new ArrayList<XmlAttribute>();
    int count = myRootNode.getChildCount();

    for (int i = 0; i < count; i++) {
      final CheckedTreeNode treeNode = (CheckedTreeNode)myRootNode.getChildAt(i);

      if (treeNode.isChecked()) {
        attributes.add((XmlAttribute)treeNode.getUserObject());
      }
    }
    return attributes;
  }

  @Nullable
  private Module getChosenModule() {
    return myModule != null ? myModule : myModuleCombo.getSelectedModule();
  }

  @Nullable
  public VirtualFile getResourceDirectory() {
    Module module = getChosenModule();
    if (module == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    return ResourceFolderManager.getInstance(facet).getPrimaryFolder();
  }

  public boolean isToSearchStyleApplications() {
    return mySearchForStyleApplicationsAfter.isSelected();
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Style name:");
    jBLabel1.setDisplayedMnemonic('S');
    jBLabel1.setDisplayedMnemonicIndex(0);
    myPanel.add(jBLabel1,
                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myStyleNameField = new JTextField();
    myPanel.add(myStyleNameField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                      new Dimension(150, -1), null, 0, false));
    myAttributeListWrapper = new JPanel();
    myAttributeListWrapper.setLayout(new BorderLayout(0, 0));
    myPanel.add(myAttributeListWrapper, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, new Dimension(-1, 200), null, 0, false));
    myAttributesLabel = new JBLabel();
    myAttributesLabel.setText("Attributes:");
    myAttributesLabel.setDisplayedMnemonic('A');
    myAttributesLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(myAttributesLabel,
                new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myModuleLabel = new JBLabel();
    myModuleLabel.setText("Module:");
    myModuleLabel.setDisplayedMnemonic('M');
    myModuleLabel.setDisplayedMnemonicIndex(0);
    myPanel.add(myModuleLabel,
                new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myModuleCombo = new ModulesComboBox();
    myPanel.add(myModuleCombo, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                   0, false));
    mySearchForStyleApplicationsAfter = new JBCheckBox();
    mySearchForStyleApplicationsAfter.setText("Launch 'Use Style Where Possible' refactoring after the style is extracted");
    mySearchForStyleApplicationsAfter.setMnemonic('L');
    mySearchForStyleApplicationsAfter.setDisplayedMnemonicIndex(0);
    myPanel.add(mySearchForStyleApplicationsAfter,
                new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    jBLabel1.setLabelFor(myStyleNameField);
    myModuleLabel.setLabelFor(myModuleCombo);
  }
}