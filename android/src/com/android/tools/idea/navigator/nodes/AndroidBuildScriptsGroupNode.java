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
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.lang.proguard.ProguardFileType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AndroidBuildScriptsGroupNode extends ProjectViewNode<List<PsiDirectory>> {
  public AndroidBuildScriptsGroupNode(@NotNull Project project, @NotNull ViewSettings viewSettings) {
    // TODO: Should this class really be parametrized on List<PsiDirectory>?
    super(project, Collections.<PsiDirectory>emptyList(), viewSettings);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return getBuildScriptsWithQualifiers().containsKey(file);
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    Map<VirtualFile, String> scripts = getBuildScriptsWithQualifiers();
    List<PsiFileNode> children = Lists.newArrayListWithExpectedSize(scripts.size());

    for (Map.Entry<VirtualFile, String> scriptWithQualifier : scripts.entrySet()) {
      addPsiFile(children, scriptWithQualifier.getKey(), scriptWithQualifier.getValue());
    }

    return children;
  }

  private Map<VirtualFile, String> getBuildScriptsWithQualifiers() {
    Map<VirtualFile, String> buildScripts = Maps.newHashMap();

    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      String moduleName = getPrefixForModule(m) + m.getName();
      buildScripts.put(GradleUtil.getGradleBuildFile(m), moduleName);

      // include all .gradle and ProGuard files from each module
      for (VirtualFile f : findAllGradleScriptsInModule(m)) {
        if (f.getFileType() == ProguardFileType.INSTANCE) {
          buildScripts.put(f, String.format("ProGuard Rules for %1$s", m.getName()));
        } else {
          buildScripts.put(f, moduleName);
        }
      }
    }

    VirtualFile baseDir = myProject.getBaseDir();
    buildScripts.put(baseDir.findChild(SdkConstants.FN_SETTINGS_GRADLE), "Project Settings");
    buildScripts.put(baseDir.findChild(SdkConstants.FN_GRADLE_PROPERTIES), "Project Properties");
    buildScripts.put(baseDir.findFileByRelativePath(GradleUtil.GRADLEW_PROPERTIES_PATH.replace(File.separatorChar, '/')), "Gradle Version");
    buildScripts.put(baseDir.findChild(SdkConstants.FN_LOCAL_PROPERTIES), "SDK Location");

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      File userSettingsFile = GradleUtil.getGradleUserSettingsFile();
      if (userSettingsFile != null) {
        buildScripts.put(VfsUtil.findFileByIoFile(userSettingsFile, false), "Global Properties");
      }
    }

    buildScripts.remove(null); // any of the above virtual files could have been null
    return buildScripts;
  }

  private static String getPrefixForModule(Module m) {
    return GradleUtil.isRootModuleWithNoSources(m) ? AndroidBuildScriptNode.PROJECT_PREFIX : AndroidBuildScriptNode.MODULE_PREFIX;
  }

  @NotNull
  private static List<VirtualFile> findAllGradleScriptsInModule(@NotNull Module m) {
    File moduleDir = new File(m.getModuleFilePath()).getParentFile();
    VirtualFile dir = VfsUtil.findFileByIoFile(moduleDir, false);
    if (dir == null || dir.getChildren() == null) {
      return Collections.emptyList();
    }

    List<VirtualFile> files = Lists.newArrayList();
    for (VirtualFile child : dir.getChildren()) {
      if (!child.isValid() || child.isDirectory() || !child.getName().endsWith(SdkConstants.EXT_GRADLE) &&
          // Consider proguard rule files as a type of build script (contains build-time configuration
          // for release builds)
          child.getFileType() != ProguardFileType.INSTANCE) {
        continue;
      }

      // TODO: When a project is imported via unit tests, there is a ijinitXXXX.gradle file created somehow, exclude that.
      if (ApplicationManager.getApplication().isUnitTestMode() &&
          (child.getName().startsWith("ijinit") || child.getName().startsWith("asLocalRepo"))) {
        continue;
      }

      files.add(child);
    }

    return files;
  }

  private void addPsiFile(@NotNull List<PsiFileNode> psiFileNodes, @Nullable VirtualFile file, @Nullable String qualifier) {
    if (file == null) {
      return;
    }

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile != null) {
      psiFileNodes.add(new AndroidBuildScriptNode(myProject, psiFile, getSettings(), qualifier));
    }
  }

  @Override
  public int getWeight() {
    return 100; // Gradle scripts node should be at the end after all the modules
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
