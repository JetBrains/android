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

import com.android.tools.idea.gradle.structure.model.PsModelNameComparator;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckboxTreeAdapter;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.util.ArrayList;
import kotlin.Unit;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.util.ui.UIUtil.getTextFieldBackground;
import static com.intellij.util.ui.UIUtil.getTextFieldBorder;

class ModuleDependenciesForm {
  @NotNull private final CheckboxTree myPossibleDependenciesTree;
  @NotNull private final Set<PsModule> mySelectedModules = Sets.newHashSet();

  private JPanel myMainPanel;
  private JBScrollPane myModulesScrollPane;
  private JXLabel myModulesLabel;

  ModuleDependenciesForm(@NotNull PsModule module) {
    myModulesLabel.setBorder(BorderFactory.createCompoundBorder(getTextFieldBorder(), JBUI.Borders.empty(2)));
    myModulesLabel.setBackground(getTextFieldBackground());
    myModulesLabel.setText(" ");

    CheckboxTree.CheckboxTreeCellRenderer cellRenderer = new CheckboxTree.CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          if (data instanceof PsModule) {
            PsModule m = (PsModule)data;
            ColoredTreeCellRenderer textRenderer = getTextRenderer();
            textRenderer.setIcon(m.getIcon());
            textRenderer.append(m.getName(), REGULAR_ATTRIBUTES);
          }
        }
      }
    };

    CheckedTreeNode root = new CheckedTreeNode(null);

    List<PsModule> modules = findAvailableModules(module);
    Collections.sort(modules, new PsModelNameComparator<>());

    modules.forEach(m -> {
      CheckedTreeNode node = new CheckedTreeNode(m);
      node.setChecked(false);
      root.add(node);
    });

    myPossibleDependenciesTree = new CheckboxTree(cellRenderer, root);
    myPossibleDependenciesTree.addCheckboxTreeListener(new CheckboxTreeAdapter() {
      @Override
      public void nodeStateChanged(@NotNull CheckedTreeNode node) {
        Object data = node.getUserObject();
        if (data instanceof PsModule) {
          PsModule m = (PsModule)data;
          if (node.isChecked()) {
            mySelectedModules.add(m);
          }
          else {
            mySelectedModules.remove(m);
          }

          PsModule[] selectedModules = myPossibleDependenciesTree.getCheckedNodes(PsModule.class, null);
          Arrays.sort(selectedModules, new PsModelNameComparator<>());

          String names = Joiner.on(", ").join(selectedModules);
          if (names.isEmpty()) {
            names = " ";
          }
          myModulesLabel.setText(names);
        }
      }
    });

    myModulesScrollPane.setViewportView(myPossibleDependenciesTree);
  }

  @NotNull
  private static List<PsModule> findAvailableModules(@NotNull PsModule module) {
    List<PsModule> modules = new ArrayList<>();
    List<PsModule> dependencies = getModuleDependencies(module);
    module.getParent().forEachModule(m -> {
      if (module != m && !dependencies.contains(m) && module.canDependOn(m)) {
        modules.add(m);
      }
    });
    return modules;
  }

  @NotNull
  private static List<PsModule> getModuleDependencies(@NotNull PsModule module) {
    PsProject project = module.getParent();
    List<PsModule> dependencies = new ArrayList<>();
    if (module instanceof PsAndroidModule) {
      ((PsAndroidModule)module).getDependencies().forEachModuleDependency(dependency -> {
        String name = dependency.getName();
        PsModule found = project.findModuleByName(name);
        if (found != null) {
          dependencies.add(found);
        }
        return Unit.INSTANCE;
      });
    }
    return dependencies;
  }

  @NotNull
  JComponent getPreferredFocusedComponent() {
    return myPossibleDependenciesTree;
  }

  @NotNull
  JPanel getPanel() {
    return myMainPanel;
  }

  @NotNull
  List<PsModule> getSelectedModules() {
    return Lists.newArrayList(mySelectedModules);
  }
}
