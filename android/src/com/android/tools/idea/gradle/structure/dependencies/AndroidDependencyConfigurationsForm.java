/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.dependencies;

import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.PsModelNameComparator;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsBuildType;
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTree.CheckboxTreeCellRenderer;
import com.intellij.ui.CheckboxTreeAdapter;
import com.intellij.ui.CheckboxTreeBase.CheckPolicy;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.*;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
import static com.intellij.util.ui.UIUtil.*;
import static com.intellij.util.ui.tree.TreeUtil.expandAll;
import static java.awt.Font.BOLD;

class AndroidDependencyConfigurationsForm implements DependencyConfigurationsForm {
  @NonNls private static final String SEPARATOR = ", ";

  private JPanel myMainPanel;
  private JCheckBox myCompileCheckBox;
  private JCheckBox myAndroidTestsCheckBox;
  private JCheckBox myUnitTestsCheckBox;
  private JBScrollPane myVariantsScrollPane;
  private JBLabel myScopesLabel;
  private CheckboxTree myVariantsTree;
  private JXLabel myConfigurationNamesLabel;

  private final List<PsBuildType> myBuildTypes = Lists.newArrayList();
  private final List<PsProductFlavor> myProductFlavors = Lists.newArrayList();

  AndroidDependencyConfigurationsForm(@NotNull PsAndroidModule module) {
    myConfigurationNamesLabel.setBorder(getTextFieldBorder());
    myConfigurationNamesLabel.setBackground(getTextFieldBackground());

    myScopesLabel.setFont(getTreeFont().deriveFont(BOLD));

    CheckboxTreeCellRenderer cellRenderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          ColoredTreeCellRenderer textRenderer = getTextRenderer();
          textRenderer.setFont(getTreeFont());

          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          if (data instanceof String) {
            textRenderer.append((String)data, REGULAR_BOLD_ATTRIBUTES);
          }
          else if (data instanceof PsModel) {
            textRenderer.append(((PsModel)data).getName());
          }
        }
      }
    };

    CheckedTreeNode buildTypesNode = new CheckedTreeNode("Build Types");
    module.forEachBuildType(myBuildTypes::add);
    Collections.sort(myBuildTypes, new PsModelNameComparator<>());

    myBuildTypes.forEach(buildType -> {
      CheckedTreeNode buildTypeNode = new CheckedTreeNode(buildType);
      buildTypesNode.add(buildTypeNode);
    });

    CheckedTreeNode productFlavorsNode = new CheckedTreeNode("Product Flavors");
    module.forEachProductFlavor(myProductFlavors::add);
    Collections.sort(myProductFlavors, new PsModelNameComparator<>());

    myProductFlavors.forEach(productFlavor -> {
      CheckedTreeNode productFlavorNode = new CheckedTreeNode(productFlavor);
      productFlavorsNode.add(productFlavorNode);
    });

    CheckedTreeNode root = new CheckedTreeNode();
    root.add(buildTypesNode);
    root.add(productFlavorsNode);

    myVariantsTree = new CheckboxTree(cellRenderer, root, new CheckPolicy(true, true, false, true));
    myVariantsTree.setFont(getTreeFont());
    myVariantsScrollPane.setViewportView(myVariantsTree);

    expandAll(myVariantsTree);

    myCompileCheckBox.addChangeListener(e -> {
      if (myCompileCheckBox.isSelected()) {
        myAndroidTestsCheckBox.setSelected(true);
        myUnitTestsCheckBox.setSelected(true);
      }
      updateConfigurationNames();
    });

    ChangeListener nonMainChangeListener = e -> {
      JCheckBox checkBox = (JCheckBox)e.getSource();
      if (!checkBox.isSelected()) {
        myCompileCheckBox.setSelected(false);
      }
      updateConfigurationNames();
    };
    myAndroidTestsCheckBox.addChangeListener(nonMainChangeListener);
    myUnitTestsCheckBox.addChangeListener(nonMainChangeListener);

    myVariantsTree.addCheckboxTreeListener(new CheckboxTreeAdapter() {
      @Override
      public void nodeStateChanged(@NotNull CheckedTreeNode node) {
        updateConfigurationNames();
      }
    });

    updateConfigurationNames();
  }

  private void updateConfigurationNames() {
    List<String> configurationNames = getConfigurationNames();
    String text = "";
    int configurationNameCount = configurationNames.size();
    if (configurationNameCount == 1) {
      text = configurationNames.get(0);
    }
    else if (configurationNameCount > 1) {
      Collections.sort(configurationNames);
      text = Joiner.on(SEPARATOR).join(configurationNames);
    }
    if (isEmpty(text)) {
      text = " ";
    }
    myConfigurationNamesLabel.setText(text);
  }

  @NotNull
  private List<String> getConfigurationNames() {
    PsBuildType[] checkedBuildTypes = getCheckedNodes(PsBuildType.class);
    boolean allBuildTypesChecked = checkedBuildTypes.length == myBuildTypes.size();

    PsProductFlavor[] checkedProductFlavors = getCheckedNodes(PsProductFlavor.class);
    boolean allProductFlavorsChecked = checkedProductFlavors.length == myProductFlavors.size();

    if (myCompileCheckBox.isSelected()) {
      if (allBuildTypesChecked && allProductFlavorsChecked) {
        return Collections.singletonList(COMPILE);
      }
      List<String> configurationNames = Lists.newArrayList();
      if (!allBuildTypesChecked) {
        configurationNames.addAll(getCompileConfigurationNames(checkedBuildTypes));
      }
      if (!allProductFlavorsChecked) {
        configurationNames.addAll(getCompileConfigurationNames(checkedProductFlavors));
      }
      return configurationNames;
    }

    List<String> configurationNames = Lists.newArrayList();

    if (myAndroidTestsCheckBox.isSelected()) {
      boolean debugBuildTypeChecked = false;
      for (PsBuildType buildType : checkedBuildTypes) {
        if (buildType.getName().equals("debug")) {
          debugBuildTypeChecked = true;
          break;
        }
      }
      if (debugBuildTypeChecked) {
        if (allProductFlavorsChecked) {
          configurationNames.add(ANDROID_TEST_COMPILE);
        }
        else {
          configurationNames.addAll(getAndroidTestConfigurationNames(checkedProductFlavors));
        }
      }
    }

    if (myUnitTestsCheckBox.isSelected()) {
      if (allBuildTypesChecked && allProductFlavorsChecked) {
        configurationNames.add(TEST_COMPILE);
      }
      else {
        if (!allBuildTypesChecked) {
          configurationNames.addAll(getUnitTestConfigurationNames(checkedBuildTypes));
        }
        if (!allProductFlavorsChecked) {
          configurationNames.addAll(getUnitTestConfigurationNames(checkedProductFlavors));
        }
      }
    }

    return configurationNames;
  }

  @NotNull
  private static List<String> getCompileConfigurationNames(@NotNull PsModel[] models) {
    return getConfigurationNames(models, "", "Compile");
  }

  @NotNull
  private static List<String> getAndroidTestConfigurationNames(@NotNull PsModel[] models) {
    return getConfigurationNames(models, "androidTest", "Compile");
  }

  @NotNull
  private static List<String> getUnitTestConfigurationNames(@NotNull PsModel[] models) {
    return getConfigurationNames(models, "test", "Compile");
  }

  @NotNull
  private static List<String> getConfigurationNames(@NotNull PsModel[] models, @NotNull String prefix, @NotNull String suffix) {
    List<String> configurationNames = Lists.newArrayList();
    for (PsModel model : models) {
      StringBuilder buffer = new StringBuilder();
      if (prefix.isEmpty()) {
        buffer.append(model.getName());
      }
      else {
        buffer.append(prefix).append(capitalize(model.getName()));
      }
      buffer.append(suffix);
      configurationNames.add(buffer.toString());
    }
    return configurationNames;
  }

  @NotNull
  private <T extends PsModel> T[] getCheckedNodes(@NotNull Class<T> nodeType) {
    return myVariantsTree.getCheckedNodes(nodeType, null);
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }

  @Override
  @NotNull
  public List<String> getSelectedConfigurations() {
    String text = myConfigurationNamesLabel.getText().trim();
    if (text.isEmpty()) {
      return Collections.emptyList();
    }
    return Splitter.on(SEPARATOR).omitEmptyStrings().trimResults().splitToList(text);
  }
}
