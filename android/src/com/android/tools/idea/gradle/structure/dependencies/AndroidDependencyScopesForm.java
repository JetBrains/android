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
import com.google.android.collect.Lists;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTree.CheckboxTreeCellRenderer;
import com.intellij.ui.CheckboxTreeBase.CheckPolicy;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
import static com.intellij.util.ui.UIUtil.getTreeFont;
import static com.intellij.util.ui.tree.TreeUtil.expandAll;

class AndroidDependencyScopesForm implements ScopesForm {

  private JPanel myMainPanel;
  private JTextField myScopesTextField;
  private JCheckBox myCompileCheckBox;
  private JCheckBox myAndroidTestsCheckBox;
  private JCheckBox myUnitTestsCheckBox;
  private JBScrollPane myVariantsScrollPane;

  AndroidDependencyScopesForm(@NotNull PsAndroidModule module) {
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

    List<PsBuildType> buildTypes = Lists.newArrayList();
    CheckedTreeNode buildTypesNode = new CheckedTreeNode("Build Types");
    module.forEachBuildType(buildTypes::add);
    Collections.sort(buildTypes, new PsModelNameComparator<>());

    buildTypes.forEach(buildType -> {
      CheckedTreeNode buildTypeNode = new CheckedTreeNode(buildType);
      buildTypesNode.add(buildTypeNode);
    });

    List<PsProductFlavor> productFlavors = Lists.newArrayList();
    CheckedTreeNode productFlavorsNode = new CheckedTreeNode("Product Flavors");
    module.forEachProductFlavor(productFlavors::add);
    Collections.sort(productFlavors, new PsModelNameComparator<>());

    productFlavors.forEach(productFlavor -> {
      CheckedTreeNode productFlavorNode = new CheckedTreeNode(productFlavor);
      productFlavorsNode.add(productFlavorNode);
    });

    CheckedTreeNode root = new CheckedTreeNode();
    root.add(buildTypesNode);
    root.add(productFlavorsNode);

    CheckboxTree variantsTree = new CheckboxTree(cellRenderer, root, new CheckPolicy(true, true, false, true));
    variantsTree.setFont(getTreeFont());
    myVariantsScrollPane.setViewportView(variantsTree);

    expandAll(variantsTree);

    myCompileCheckBox.addChangeListener(e -> {
      if (myCompileCheckBox.isSelected()) {
        myAndroidTestsCheckBox.setSelected(true);
        myUnitTestsCheckBox.setSelected(true);
      }
    });

    ChangeListener nonMainChangeListener = e -> {
      JCheckBox checkBox = (JCheckBox)e.getSource();
      if (!checkBox.isSelected()) {
        myCompileCheckBox.setSelected(false);
      }
    };
    myAndroidTestsCheckBox.addChangeListener(nonMainChangeListener);
    myUnitTestsCheckBox.addChangeListener(nonMainChangeListener);
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }
}
