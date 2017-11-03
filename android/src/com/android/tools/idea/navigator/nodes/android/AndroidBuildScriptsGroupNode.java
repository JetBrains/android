/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.android;

import com.android.tools.idea.lang.proguard.ProguardFileType;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.gradle.util.Projects.findModuleRootFolderPath;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

public class AndroidBuildScriptsGroupNode extends ProjectViewNode<List<PsiDirectory>> {
  public AndroidBuildScriptsGroupNode(@NotNull Project project, @NotNull ViewSettings settings) {
    // TODO: Should this class really be parametrized on List<PsiDirectory>?
    super(project, Collections.emptyList(), settings);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return getBuildScriptsWithQualifiers().containsKey(file);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    Map<VirtualFile, String> scripts = getBuildScriptsWithQualifiers();
    List<PsiFileNode> children = new ArrayList<>(scripts.size());

    for (Map.Entry<VirtualFile, String> scriptWithQualifier : scripts.entrySet()) {
      addPsiFile(children, scriptWithQualifier.getKey(), scriptWithQualifier.getValue());
    }

    return children;
  }

  @NotNull
  private Map<VirtualFile, String> getBuildScriptsWithQualifiers() {
    Map<VirtualFile, String> buildScripts = new HashMap<>();

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      String moduleName = getPrefixForModule(module) + module.getName();
      VirtualFile gradleBuildFile = getGradleBuildFile(module);
      if (gradleBuildFile != null) {
        buildScripts.put(gradleBuildFile, moduleName);
      }

      // include all .gradle and ProGuard files from each module
      for (VirtualFile file : findAllGradleScriptsInModule(module)) {
        if (file.getFileType() == ProguardFileType.INSTANCE) {
          buildScripts.put(file, String.format("ProGuard Rules for %1$s", module.getName()));
        }
        else {
          buildScripts.put(file, moduleName);
        }
      }
    }

    VirtualFile projectRootFolder = myProject.getBaseDir();
    if (projectRootFolder != null) {
      // Should not happen, but we have reports that there is a NPE in this area.
      findChildAndAddToMapIfFound(FN_SETTINGS_GRADLE, projectRootFolder, "Project Settings", buildScripts);
      findChildAndAddToMapIfFound(FN_GRADLE_PROPERTIES, projectRootFolder, "Project Properties", buildScripts);

      VirtualFile child = projectRootFolder.findFileByRelativePath(toSystemIndependentName(GRADLEW_PROPERTIES_PATH));
      if (child != null) {
        buildScripts.put(child, "Gradle Version");
      }

      findChildAndAddToMapIfFound(FN_LOCAL_PROPERTIES, projectRootFolder, "SDK Location", buildScripts);
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      File userSettingsFile = getGradleUserSettingsFile();
      if (userSettingsFile != null) {
        VirtualFile file = findFileByIoFile(userSettingsFile, false);
        if (file != null) {
          buildScripts.put(file, "Global Properties");
        }
      }
    }

    buildScripts.remove(null); // any of the above virtual files could have been null
    return buildScripts;
  }

  private static void findChildAndAddToMapIfFound(@NotNull String childName,
                                                  @NotNull VirtualFile parent,
                                                  @NotNull String value,
                                                  @NotNull Map<VirtualFile, String> map) {
    VirtualFile child = parent.findChild(childName);
    if (child != null) {
      map.put(child, value);
    }
  }

  @NotNull
  private static String getPrefixForModule(@NotNull Module module) {
    return isRootModuleWithNoSources(module) ? AndroidBuildScriptNode.PROJECT_PREFIX : AndroidBuildScriptNode.MODULE_PREFIX;
  }

  @NotNull
  private static List<VirtualFile> findAllGradleScriptsInModule(@NotNull Module module) {
    File moduleRootFolderPath = findModuleRootFolderPath(module);
    assert moduleRootFolderPath != null;

    VirtualFile moduleRootFolder = findFileByIoFile(moduleRootFolderPath, false);
    if (moduleRootFolder == null || moduleRootFolder.getChildren() == null) {
      return Collections.emptyList();
    }

    List<VirtualFile> files = new ArrayList<>();
    for (VirtualFile child : moduleRootFolder.getChildren()) {
      // @formatter:off
      if (!child.isValid() || child.isDirectory() || !child.getName().endsWith(EXT_GRADLE) &&
           // Consider proguard rule files as a type of build script (contains build-time configuration
           // for release builds)
           child.getFileType() != ProguardFileType.INSTANCE) {
        continue;
      }
      // @formatter:on

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
    if (file != null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      if (psiFile != null) {
        psiFileNodes.add(new AndroidBuildScriptNode(myProject, psiFile, getSettings(), qualifier));
      }
    }
  }

  @Override
  public int getWeight() {
    return 100; // Gradle scripts node should be at the end after all the modules
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setPresentableText("Gradle Scripts");
    presentation.setIcon(GradleIcons.Gradle);
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return "Gradle Scripts";
  }
}
