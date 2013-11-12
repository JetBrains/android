/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer;

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.base.Strings;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Sets the compiler output folder to a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class CompilerOutputPathModuleCustomizer implements ModuleCustomizer {
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject != null) {
      String modelVersion = ideaAndroidProject.getDelegate().getModelVersion();
      if (Strings.isNullOrEmpty(modelVersion)) {
        // We are dealing with old model that does not have 'class' folder.
        return;
      }
      Variant selectedVariant = ideaAndroidProject.getSelectedVariant();
      File outputFile = selectedVariant.getMainArtifactInfo().getClassesFolder();
      setOutputPaths(module, outputFile, null);
    } else if (ModuleType.get(module) instanceof JavaModuleType) {
      // In order to run tests for a Java module, we need to set its classpath and test classpath.
      // Currently, this is assumed to just be at "build/classes/main" and "build/classes/test" respectively.
      // TODO: This really should come from Gradle. https://code.google.com/p/android/issues/detail?id=61946
      VirtualFile moduleFile = module.getModuleFile();
      if (moduleFile != null) {
        VirtualFile moduleRoot = moduleFile.getParent();
        if (moduleRoot != null) {
          File classes = new File(VfsUtilCore.virtualToIoFile(moduleRoot), FileUtil.join("build", "classes", "main"));
          File tests = new File(VfsUtilCore.virtualToIoFile(moduleRoot), FileUtil.join("build", "classes", "test"));
          setOutputPaths(module, classes, tests);
        }
      }
    }
  }

  private void setOutputPaths(@NotNull Module module, @NotNull File classesFolder, @Nullable File testClassesFolder) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel moduleSettings = moduleRootManager.getModifiableModel();
    CompilerModuleExtension compilerSettings = moduleSettings.getModuleExtension(CompilerModuleExtension.class);
    try {
      compilerSettings.inheritCompilerOutputPath(false);
      String dirPath = FileUtil.toSystemIndependentName(classesFolder.getPath());
      compilerSettings.setCompilerOutputPath(VfsUtilCore.pathToUrl(dirPath));
      if (testClassesFolder != null) {
        dirPath = FileUtil.toSystemIndependentName(testClassesFolder.getPath());
        compilerSettings.setCompilerOutputPathForTests(VfsUtilCore.pathToUrl(dirPath));
      }
    } finally {
      compilerSettings.commit();
      moduleSettings.commit();
    }
  }
}
