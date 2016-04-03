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

import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsVariant;
import com.android.tools.idea.gradle.variant.ui.VariantCheckboxTreeCellRenderer;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.UIUtil.getTreeFont;

class AndroidScopesForm implements ScopesForm {
  @NotNull private final PsAndroidModule myModule;

  private JPanel myMainPanel;

  private JTextField myScopeTextField;
  private JPanel myScopesPanel;

  AndroidScopesForm(@NotNull PsAndroidModule module) {
    myModule = module;
    myScopeTextField.setText("compile");

    CheckboxTree.CheckboxTreeCellRenderer cellRenderer = new VariantCheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          ColoredTreeCellRenderer textRenderer = getTextRenderer();

          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          if (data instanceof PsAndroidArtifact) {
            PsAndroidArtifact artifact = (PsAndroidArtifact)data;

            PsVariant variant = artifact.getParent();
            String name = variant.getName() + artifact.getName();

            textRenderer.setIcon(artifact.getIcon());
            textRenderer.append(name);
          }
        }
      }
    };

    CheckedTreeNode root = new CheckedTreeNode();

    final List<PsAndroidArtifact> artifacts = Lists.newArrayList();
    myModule.forEachVariant(variant -> {
      if (variant == null) {
        return false;
      }
      variant.forEachArtifact(artifact -> {
        if (artifact == null) {
          return false;
        }
        artifacts.add(artifact);
        return true;
      });
      return true;
    });

    if (artifacts.size() > 1) {
      Collections.sort(artifacts, (a1, a2) -> a1.getName().compareTo(a2.getName()));
    }

    for (PsAndroidArtifact artifact : artifacts) {
      CheckedTreeNode newChild = new CheckedTreeNode(artifact);
      newChild.setChecked(true);
      root.add(newChild);
    }

    CheckboxTree scopeTree = new CheckboxTree(cellRenderer, root);

    scopeTree.setRootVisible(false);
    BasicTreeUI basicTreeUI = (BasicTreeUI) scopeTree.getUI();
    basicTreeUI.setRightChildIndent(0);
    scopeTree.setFont(getTreeFont());
    scopeTree.setBorder(IdeBorderFactory.createBorder());

    JScrollPane scrollPane = createScrollPane(scopeTree);
    scrollPane.setPreferredSize(new Dimension(520, 220));
    myScopesPanel.add(scrollPane, BorderLayout.CENTER);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AnAction("Check All", null, AllIcons.Actions.Selectall) {
      @Override
      public void actionPerformed(AnActionEvent e) {

      }
    });
    group.add(new AnAction("Uncheck All", null, AllIcons.Actions.Unselectall) {
      @Override
      public void actionPerformed(AnActionEvent e) {

      }
    });
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("", group, true);
    JComponent toolbarComponent = actionToolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.LEFT | SideBorder.TOP | SideBorder.RIGHT));
    myScopesPanel.add(toolbarComponent, BorderLayout.NORTH);
  }

  @Override
  @NotNull
  public JPanel getPanel() {
    return myMainPanel;
  }
}
