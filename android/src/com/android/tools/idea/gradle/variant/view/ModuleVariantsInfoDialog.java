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
package com.android.tools.idea.gradle.variant.view;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ModuleTypeComparator;
import com.android.tools.idea.gradle.variant.ui.VariantCheckboxTreeCellRenderer;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.util.ui.ToolWindowAlikePanel.createTreePanel;

/**
 * Displays the dependencies and dependents of a particular module (including build variants.)
 */
class ModuleVariantsInfoDialog extends DialogWrapper {
  @NotNull private final JPanel myPanel;

  ModuleVariantsInfoDialog(@NotNull Module module, @NotNull AndroidGradleModel androidModel) {
    super(module.getProject());
    setTitle(String.format("Dependency Details for Module '%1$s'", module.getName()));
    myPanel = new JPanel(new BorderLayout());
    myPanel.setPreferredSize(JBUI.size(600, 400));

    Splitter splitter = new Splitter(false, 0.5f);
    myPanel.add(splitter, BorderLayout.CENTER);

    splitter.setFirstComponent(createTreePanel("Dependencies", createDependenciesTree(module, androidModel)));
    splitter.setSecondComponent(createTreePanel("Dependents", createDependentsTree(module, androidModel)));

    init();
  }

  @NotNull
  private static JTree createDependenciesTree(@NotNull Module module,
                                              @NotNull AndroidGradleModel androidModel) {
    VariantCheckboxTreeCellRenderer renderer = new VariantCheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          if (data instanceof String) {
            appendVariant((String)data);
          }
          else if (data instanceof DependencyTreeElement) {
            DependencyTreeElement dependency = (DependencyTreeElement)data;
            appendModule(dependency.myModule, dependency.myVariant);
          }
        }
      }
    };

    //noinspection ConstantConditions
    CheckedTreeNode root = new CheckedTreeNode(null);

    AndroidProject androidProject = GradleUtil.getAndroidProject(module);
    assert androidProject != null;

    Multimap<String, DependencyTreeElement> dependenciesByVariant = HashMultimap.create();

    for (Variant variant : androidProject.getVariants()) {
      for (AndroidLibrary library : GradleUtil.getDirectLibraryDependencies(variant, androidModel)) {
        String gradlePath = library.getProject();
        if (gradlePath == null) {
          continue;
        }
        Module dependency = GradleUtil.findModuleByGradlePath(module.getProject(), gradlePath);
        if (dependency != null) {
          DependencyTreeElement element = new DependencyTreeElement(dependency, library.getProjectVariant());
          dependenciesByVariant.put(variant.getName(), element);
        }
      }
    }

    // Consolidate variants. This means if "debug" and "release" have the same dependencies, we show only one node as "debug, release".
    List<String> variantNames = Lists.newArrayList(dependenciesByVariant.keySet());
    Collections.sort(variantNames);

    List<String> consolidatedVariants = Lists.newArrayList();
    List<String> variantsToSkip = Lists.newArrayList();

    int variantCount = variantNames.size();
    for (int i = 0; i < variantCount; i++) {
      String variant1 = variantNames.get(i);
      if (variantsToSkip.contains(variant1)) {
        continue;
      }

      Collection<DependencyTreeElement> set1 = dependenciesByVariant.get(variant1);
      for (int j = i + 1; j < variantCount; j++) {
        String variant2 = variantNames.get(j);
        Collection<DependencyTreeElement> set2 = dependenciesByVariant.get(variant2);

        if (set1.equals(set2)) {
          variantsToSkip.add(variant2);
          if (!consolidatedVariants.contains(variant1)) {
            consolidatedVariants.add(variant1);
          }
          consolidatedVariants.add(variant2);
        }
      }

      String variantName = variant1;
      if (!consolidatedVariants.isEmpty()) {
        variantName = Joiner.on(", ").join(consolidatedVariants);
      }

      DefaultMutableTreeNode variantNode = new DefaultMutableTreeNode(variantName);
      root.add(variantNode);

      List<DependencyTreeElement> dependencies = Lists.newArrayList(set1);
      Collections.sort(dependencies);

      for (DependencyTreeElement dependency : dependencies) {
        variantNode.add(new DefaultMutableTreeNode(dependency));
      }

      consolidatedVariants.clear();
    }

    CheckboxTree tree = new CheckboxTree(renderer, root);
    tree.setRootVisible(false);
    TreeUtil.expandAll(tree);

    return tree;
  }

  @NotNull
  private static JTree createDependentsTree(@NotNull Module module,
                                            @NotNull AndroidGradleModel androidModel) {
    VariantCheckboxTreeCellRenderer renderer = new VariantCheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)value).getUserObject();
          if (data instanceof String) {
            appendVariant((String)data);
          }
          else if (data instanceof DependentTreeElement) {
            DependentTreeElement dependent = (DependentTreeElement)data;
            String variant = null;
            if (!dependent.myVariants.isEmpty()) {
              variant = Joiner.on(", ").join(dependent.myVariants);
            }
            appendModule(dependent.myModule, variant);
          }
        }
      }
    };

    //noinspection ConstantConditions
    CheckedTreeNode root = new CheckedTreeNode(null);

    AndroidProject androidProject = GradleUtil.getAndroidProject(module);
    assert androidProject != null;

    String gradlePath = GradleUtil.getGradlePath(module);
    assert gradlePath != null;

    Multimap<String, DependentTreeElement> dependentsByVariant = HashMultimap.create();

    ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
    for (Module dependent : moduleManager.getModuleDependentModules(module)) {
      AndroidProject dependentProject = GradleUtil.getAndroidProject(dependent);
      if (dependentProject == null) {
        continue;
      }

      DependentTreeElement element = new DependentTreeElement(dependent);

      for (Variant variant : dependentProject.getVariants()) {
        for (AndroidLibrary library : GradleUtil.getDirectLibraryDependencies(variant, androidModel)) {
          if (gradlePath.equals(library.getProject())) {
            element.addVariant(variant.getName());
            String projectVariant = library.getProjectVariant();
            if (StringUtil.isNotEmpty(projectVariant)) {
              dependentsByVariant.put(projectVariant, element);
            }
          }
        }
      }
    }

    List<String> variantNames = Lists.newArrayList(dependentsByVariant.keySet());
    Collections.sort(variantNames);

    for (String variantName : variantNames) {
      Collection<DependentTreeElement> dependents = dependentsByVariant.get(variantName);
      if (!dependents.isEmpty()) {
        List<DependentTreeElement> sortedDependents = Lists.newArrayList(dependents);
        Collections.sort(sortedDependents);

        DefaultMutableTreeNode variantNode = new DefaultMutableTreeNode(variantName);
        for (DependentTreeElement dependent : dependents) {
          variantNode.add(new DefaultMutableTreeNode(dependent));
        }

        root.add(variantNode);
      }
    }

    CheckboxTree tree = new CheckboxTree(renderer, root);
    tree.setRootVisible(false);
    TreeUtil.expandAll(tree);

    return tree;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private static class DependencyTreeElement implements Comparable<DependencyTreeElement> {
    @NotNull final Module myModule;
    @Nullable final String myVariant;

    DependencyTreeElement(@NotNull Module module, @Nullable String variant) {
      myModule = module;
      myVariant = variant;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DependencyTreeElement that = (DependencyTreeElement)o;
      return Objects.equal(myModule, that.myModule) && Objects.equal(myVariant, that.myVariant);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myModule.getName(), myVariant);
    }

    @Override
    public int compareTo(@NotNull DependencyTreeElement dependency) {
      return ModuleTypeComparator.INSTANCE.compare(myModule, dependency.myModule);
    }
  }

  private static class DependentTreeElement implements Comparable<DependentTreeElement> {
    @NotNull final Module myModule;
    @NotNull final List<String> myVariants = Lists.newArrayList();

    DependentTreeElement(@NotNull Module module) {
      myModule = module;
    }

    void addVariant(@NotNull String variant) {
      myVariants.add(variant);
    }

    @Override
    public int compareTo(@NotNull DependentTreeElement dependent) {
      return ModuleTypeComparator.INSTANCE.compare(myModule, dependent.myModule);
    }
  }
}
