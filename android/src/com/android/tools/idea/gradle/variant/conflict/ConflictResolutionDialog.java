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
package com.android.tools.idea.gradle.variant.conflict;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ModuleTypeComparator;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.tree.TreeUtil;
import icons.AndroidIcons;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.*;

class ConflictResolutionDialog extends DialogWrapper {
  private final JPanel myPanel;
  private final ConflictTree myTree;

  ConflictResolutionDialog(@NotNull Conflict conflict) {
    super(conflict.getSource().getProject());

    setTitle("Resolve Variant Selection Conflict");
    myPanel = new JPanel(new BorderLayout());
    myPanel.setPreferredSize(new Dimension(400, 400));

    init();

    CheckboxTree.CheckboxTreeCellRenderer renderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          ColoredTreeCellRenderer textRenderer = getTextRenderer();
          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          if (data instanceof String) {
            textRenderer.append((String)data, REGULAR_ITALIC_ATTRIBUTES);
            textRenderer.append(" ", REGULAR_ATTRIBUTES);
            textRenderer.append("(Variant)", GRAY_ATTRIBUTES);
            textRenderer.setIcon(AndroidIcons.Variant);
          }
          if (data instanceof Module) {
            Module module = (Module)data;
            textRenderer.append(module.getName());
            textRenderer.setIcon(GradleUtil.getModuleIcon(module));
          }
        }
      }
    };

    //noinspection ConstantConditions
    CheckedTreeNode root = new CheckedTreeNode(null);
    myTree = new ConflictTree(renderer, root);
    myTree.setRootVisible(false);

    List<String> variants = Lists.newArrayList(conflict.getVariants());
    Collections.sort(variants);

    for (String variant : variants) {
      CheckedTreeNode variantNode = new CheckedTreeNode(variant);
      variantNode.setChecked(false);
      root.add(variantNode);

      List<Module> dependents = Lists.newArrayList();
      for (Conflict.AffectedModule affected : conflict.getModulesExpectingVariant(variant)) {
        Module module = affected.getTarget();
        dependents.add(module);
      }
      if (dependents.size() > 1) {
        Collections.sort(dependents, ModuleTypeComparator.INSTANCE);
      }
      for (Module dependent : dependents) {
        DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(dependent);
        variantNode.add(moduleNode);
      }
    }

    myTree.expandAll();

    JXLabel descriptionLabel = new JXLabel();
    descriptionLabel.setLineWrap(true);
    String text = "The conflict cannot be automatically solved.\n";
    text += String.format("Module '%1$s' has variant '%2$s' selected, but multiple modules require different variants.",
                          conflict.getSource().getName(), conflict.getSelectedVariant());
    descriptionLabel.setText(text);
    // Leave some space between the description and the tree.
    descriptionLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 5, 2));

    myPanel.add(descriptionLabel, BorderLayout.NORTH);
    myPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return super.createActions();
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    if (StringUtil.isEmpty(getSelectedVariant())) {
      return new ValidationInfo("Please choose the variant to set");
    }
    return null;
  }

  @Nullable
  String getSelectedVariant() {
    return myTree.getSelectedVariant();
  }

  private static class ConflictTree extends CheckboxTree {
    @NotNull final CheckedTreeNode myRoot;

    ConflictTree(@NotNull CheckboxTreeCellRenderer cellRenderer, @NotNull CheckedTreeNode root) {
      super(cellRenderer, root);
      myRoot = root;
    }

    @Nullable
    String getSelectedVariant() {
      Enumeration moduleNodes = myRoot.children();
      while (moduleNodes.hasMoreElements()) {
        Object child = moduleNodes.nextElement();
        if (child instanceof CheckedTreeNode) {
          CheckedTreeNode node = (CheckedTreeNode)child;
          if (node.isChecked()) {
            return node.getUserObject().toString();
          }
        }
      }
      return null;
    }

    void expandAll() {
      TreeUtil.expandAll(this);
    }

    @Override
    public DefaultTreeModel getModel() {
      return (DefaultTreeModel)super.getModel();
    }

    @Override
    protected void onNodeStateChanged(CheckedTreeNode node) {
      if (!node.isChecked()) {
        return;
      }
      Enumeration moduleNodes = myRoot.children();
      while (moduleNodes.hasMoreElements()) {
        Object child = moduleNodes.nextElement();
        if (child != node && child instanceof CheckedTreeNode) {
          CheckedTreeNode childNode = (CheckedTreeNode)child;
          childNode.setChecked(false);
        }
      }
    }
  }
}
