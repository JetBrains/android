// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.navigator.nodes;

import static com.intellij.util.containers.ContainerUtil.emptyList;

import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specialization of {@link ProjectViewModuleNode} for Android view.
 */
public abstract class AndroidViewModuleNode extends ProjectViewModuleNode {

  public AndroidViewModuleNode(@NotNull Project project,
                               @NotNull Module value,
                               ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  /**
   * @return module children except of its sub-modules.
   */
  @NotNull
  protected abstract Collection<AbstractTreeNode<?>> getModuleChildren();

  /**
   * Provides access to the platform's {@link ProjectViewModuleNode#getChildren}.
   */
  @NotNull
  protected final Collection<AbstractTreeNode<?>> platformGetChildren() {
    return super.getChildren();
  }

  /**
   * {@inheritDoc}
   * Final. Please override {@link #getModuleChildren()} }.
   */
  @NotNull
  @Override
  public final Collection<AbstractTreeNode<?>> getChildren() {
    Project project = getProject();
    Module module = getValue();
    if (project == null || module == null) {
      return getModuleChildren();
    }
    return CollectionsKt.plus(createSubmoduleNodes(), getModuleChildren());
  }

  @Nullable
  private AndroidModuleSystem getAndroidModuleSystem() {
    if (getProject() == null || getValue() == null) {
      return null;
    }
    AndroidProjectSystem projectSystem = ProjectSystemService.getInstance(getProject()).getProjectSystem();
    return projectSystem.getModuleSystem(getValue());
  }

  @NotNull
  private List<AbstractTreeNode<?>> createSubmoduleNodes() {
    AndroidModuleSystem androidModuleSystem = getAndroidModuleSystem();
    if (androidModuleSystem == null) return emptyList();
    return ModuleNodeUtils
      .createChildModuleNodes(
        Objects.requireNonNull(getProject()),
        androidModuleSystem.getSubmodules(),
        getSettings());
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    if (super.contains(file)) return true;

    // We also need to check extra content roots from the source set modules since the super method is only based off the
    // holders roots.
    Module module = getValue();
    if (module.isDisposed()) {
      return false;
    }
    List<Module> sourceSetModules = ModuleSystemUtil.getAllLinkedModules(module);
    for (Module m : sourceSetModules) {
      // The module from getValue() has already been checked by super.contains
      if (m == module) {
        continue;
      }
      if (m.isDisposed()) {
        continue;
      }
      for (VirtualFile root : ModuleRootManager.getInstance(m).getContentRoots()) {
        if (VfsUtilCore.isAncestor(root, file, false)) return true;
      }
    }

    // This is relative slow and could be replaced with a better module system based implementation. However, this code
    // is usually invoked on relatively small number of roots of the incoming file-system-change-notification.
    return createSubmoduleNodes().stream().anyMatch(it -> (it instanceof ProjectViewNode) && ((ProjectViewNode<?>)it).contains(file));
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    Module value = getValue();
    if (value == null) return "null";
    return ProjectSystemUtil.getModuleSystem(value).getDisplayNameForModule();
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      setValue(null);
      return;
    }

    String moduleShortName = ProjectSystemUtil.getModuleSystem(module).getDisplayNameForModule();

    presentation.setPresentableText(moduleShortName);
    presentation.addText(moduleShortName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    presentation.setIcon(ModuleType.get(module).getIcon());
    presentation.setTooltip(ModuleType.get(module).getName());
  }
}
