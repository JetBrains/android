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
package com.android.tools.idea.navigator.nodes;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.AndroidGradleProjectData;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import icons.GradleIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AndroidBuildScriptsGroupNode extends ProjectViewNode<List<PsiDirectory>> {
  private static final Set<String> ourBuildFileNames =
    ImmutableSet.of(SdkConstants.FN_SETTINGS_GRADLE, SdkConstants.FN_GRADLE_PROPERTIES, SdkConstants.FN_BUILD_GRADLE,
                    SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES);

  public AndroidBuildScriptsGroupNode(Project project, List<PsiDirectory> psiDirectories, ViewSettings viewSettings) {
    super(project, psiDirectories, viewSettings);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    // TODO: this is not an entirely accurate contains() check when compared to the getChildren() implementation below.
    // As a result, the "Scroll to/from source" actions may not be entirely accurate for these files.
    if (!ourBuildFileNames.contains(file.getName())) {
      return false;
    }

    return true;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;

    PsiManager psiManager = PsiManager.getInstance(myProject);
    List<PsiFileNode> children = Lists.newArrayList();

    VirtualFile baseDir = myProject.getBaseDir();

    addPsiFile(myProject, psiManager, children, baseDir.findChild(SdkConstants.FN_SETTINGS_GRADLE), "Project Settings");
    addPsiFile(myProject, psiManager, children, baseDir.findChild(SdkConstants.FN_GRADLE_PROPERTIES), "Project Properties");
    addPsiFile(myProject, psiManager, children, baseDir.findFileByRelativePath(GradleUtil.GRADLEW_PROPERTIES_PATH), null);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      File userSettingsFile = AndroidGradleProjectData.getGradleUserSettingsFile();
      if (userSettingsFile != null) {
        addPsiFile(myProject, psiManager, children, VfsUtil.findFileByIoFile(userSettingsFile, false), "Global Properties");
      }
    }

    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      addPsiFile(myProject, psiManager, children, GradleUtil.getGradleBuildFile(m), m.getName());
    }

    return children;
  }

  private void addPsiFile(@NotNull Project project,
                          @NotNull PsiManager psiManager,
                          @NotNull List<PsiFileNode> psiFileNodes,
                          @Nullable VirtualFile file,
                          @Nullable String qualifier) {
    if (file != null) {
      PsiFile psiFile = psiManager.findFile(file);
      if (psiFile != null) {
        psiFileNodes.add(new AndroidBuildScriptNode(project, psiFile, getSettings(), qualifier));
      }
    }
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setPresentableText("Gradle Scripts");
    presentation.setIcon(GradleIcons.Gradle);
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return "Gradle Scripts";
  }
}
