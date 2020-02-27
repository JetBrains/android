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

import static com.android.tools.idea.gradle.util.AndroidGradleUtil.getDisplayNameForModule;

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Collection;
import java.util.Objects;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specialization of {@link ProjectViewModuleNode} for Android view.
 */
public abstract class AndroidViewModuleNode extends ProjectViewModuleNode {
  @NotNull protected final AndroidProjectViewPane myProjectViewPane;

  public AndroidViewModuleNode(@NotNull Project project,
                               @NotNull Module value,
                               @NotNull AndroidProjectViewPane projectViewPane,
                               ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myProjectViewPane = projectViewPane;
  }

  /**
   * @return module children except of its sub-modules.
   */
  @NotNull
  protected abstract Collection<AbstractTreeNode> getModuleChildren();

  /**
   * Provides access to the platform's {@link ProjectViewModuleNode#getChildren}.
   */
  @NotNull
  protected final Collection<AbstractTreeNode> platformGetChildren() {
    return super.getChildren();
  }

  /**
   * {@inheritDoc}
   * Final. Please override {@link #getModuleChildren()} }.
   */
  @NotNull
  @Override
  public final Collection<AbstractTreeNode> getChildren() {
    Project project = getProject();
    Module module = getValue();
    if (project == null || module == null) {
      return getModuleChildren();
    }
    AndroidProjectSystem projectSystem = ProjectSystemService.getInstance(getProject()).getProjectSystem();
    AndroidModuleSystem moduleSystem = projectSystem.getModuleSystem(getValue());
    return CollectionsKt.plus(
      ModuleNodeUtils
        .createChildModuleNodes(Objects.requireNonNull(getProject()), moduleSystem.getSubmodules(), myProjectViewPane, getSettings()),
      getModuleChildren());
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    Module value = getValue();
    return (value != null) ? getDisplayNameForModule(value) : "null";
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    Module module = getValue();
    if (module == null || module.isDisposed()) {
      setValue(null);
      return;
    }

    String moduleShortName = getDisplayNameForModule(module);

    presentation.setPresentableText(moduleShortName);
    presentation.addText(moduleShortName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);

    presentation.setIcon(ModuleType.get(module).getIcon());
    presentation.setTooltip(ModuleType.get(module).getName());
  }
}
