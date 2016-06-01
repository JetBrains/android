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
package com.android.tools.idea.navigator.nodes;

import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class ExternalBuildFilesGroupNode extends ProjectViewNode<Project> {
  public ExternalBuildFilesGroupNode(@NotNull Project project, @NotNull ViewSettings viewSettings) {
    super(project, project, viewSettings);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return getBuildFilesWithModuleNames().containsKey(file);
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    Map<VirtualFile, String> buildFiles = getBuildFilesWithModuleNames();
    List<PsiFileNode> children = Lists.newArrayListWithExpectedSize(buildFiles.size());

    for (Map.Entry<VirtualFile, String> buildFileWithModuleName : buildFiles.entrySet()) {
      addPsiFile(children, buildFileWithModuleName.getKey(), buildFileWithModuleName.getValue());
    }

    return children;
  }

  @NotNull
  private Map<VirtualFile, String> getBuildFilesWithModuleNames() {
    Map<VirtualFile, String> buildFiles = Maps.newHashMap();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      NativeAndroidGradleModel nativeAndroidModel = NativeAndroidGradleModel.get(module);
      if (nativeAndroidModel == null) {
        continue;
      }
      for (File file : nativeAndroidModel.getNativeAndroidProject().getBuildFiles()) {
        VirtualFile virtualFile = findFileByIoFile(file, false);
        if (virtualFile != null) {
          buildFiles.put(virtualFile, module.getName());
        }
      }
    }
    return buildFiles;
  }

  private void addPsiFile(@NotNull List<PsiFileNode> psiFileNodes, @NotNull VirtualFile file, @NotNull String moduleName) {
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile != null) {
      psiFileNodes.add(new ExternalBuildFileNode(myProject, psiFile, getSettings(), moduleName));
    }
  }

  @Override
  public int getWeight() {
    return 200; // External Build Files node at the end after all the modules and Gradle Scripts node.
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setPresentableText("External Build Files");
    presentation.setIcon(AllIcons.General.ExternalToolsSmall);
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return "External Build Files";
  }
}
