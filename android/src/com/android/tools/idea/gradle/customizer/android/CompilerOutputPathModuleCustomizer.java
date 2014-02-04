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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
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
public class CompilerOutputPathModuleCustomizer implements ModuleCustomizer<IdeaAndroidProject> {
  private static final Logger LOG = Logger.getInstance(CompilerOutputPathModuleCustomizer.class);

  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject androidProject) {
    if (androidProject != null) {
      String modelVersion = androidProject.getDelegate().getModelVersion();
      if (Strings.isNullOrEmpty(modelVersion)) {
        // We are dealing with old model that does not have 'class' folder.
        return;
      }
      Variant selectedVariant = androidProject.getSelectedVariant();
      File outputFile = selectedVariant.getMainArtifact().getClassesFolder();
      setOutputPaths(module, outputFile, null);
    } else if (ModuleType.get(module) instanceof JavaModuleType) {
      // In order to run tests for a Java module, we need to set its classpath and test classpath.
      // Currently, this is assumed to just be at "build/classes/main" and "build/classes/test" respectively.
      // TODO: This really should come from Gradle. https://code.google.com/p/android/issues/detail?id=61946
      VirtualFile moduleFile = module.getModuleFile();
      if (moduleFile != null) {
        VirtualFile moduleRootDir = moduleFile.getParent();
        if (moduleRootDir != null) {
          File moduleRootDirPath = VfsUtilCore.virtualToIoFile(moduleRootDir);
          File mainDirPath = new File(moduleRootDirPath, FileUtil.join("build", "classes", "main"));
          File testDirPath = new File(moduleRootDirPath, FileUtil.join("build", "classes", "test"));
          setOutputPaths(module, mainDirPath, testDirPath);
        }
      }
    }
  }

  private static void setOutputPaths(@NotNull Module module, @NotNull File mainDirPath, @Nullable File testDirPath) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel moduleSettings = moduleRootManager.getModifiableModel();
    CompilerModuleExtension compilerSettings = moduleSettings.getModuleExtension(CompilerModuleExtension.class);
    if (compilerSettings == null) {
      moduleSettings.dispose();
      LOG.warn(String.format("No compiler extension is found for module '%1$s'", module.getName()));
      return;
    }
    try {
      compilerSettings.inheritCompilerOutputPath(false);
      compilerSettings.setCompilerOutputPath(toUrl(mainDirPath));
      if (testDirPath != null) {
        compilerSettings.setCompilerOutputPathForTests(toUrl(testDirPath));
      }
    } finally {
      moduleSettings.commit();
    }
  }

  @NotNull
  private static String toUrl(@NotNull File path) {
    String s = FileUtil.toSystemIndependentName(path.getPath());
    return VfsUtilCore.pathToUrl(s);
  }
}
