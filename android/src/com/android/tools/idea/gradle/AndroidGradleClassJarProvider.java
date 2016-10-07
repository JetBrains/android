/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.ClassJarProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Contains Android-Gradle related state necessary for finding classes (from build output)
 * and jars (external libraries).
 */
public class AndroidGradleClassJarProvider extends ClassJarProvider {

  @Override
  @Nullable
  public VirtualFile findModuleClassFile(@NotNull String className, @NotNull Module module) {
    AndroidGradleModel model = AndroidGradleModel.get(module);
    if (model == null) {
      return null;
    }
    VirtualFile outputDir = getCompilerOutputRoot(model);
    if (outputDir == null) {
      return null;
    }
    return ClassJarProvider.findClassFileInPath(outputDir, className);
  }

  @Nullable
  private static VirtualFile getCompilerOutputRoot(@NotNull AndroidGradleModel model) {
    Variant variant = model.getSelectedVariant();
    String variantName = variant.getName();
    AndroidArtifact mainArtifactInfo = model.getMainArtifact();
    File classesFolder = mainArtifactInfo.getClassesFolder();

    // Older models may not supply it; in that case, we rely on looking relative to the .APK file location:
    //noinspection ConstantConditions
    if (classesFolder == null) {
      @SuppressWarnings("deprecation")  // For getOutput()
      AndroidArtifactOutput output = GradleUtil.getOutput(mainArtifactInfo);
      File file = output.getMainOutputFile().getOutputFile();
      File buildFolder = file.getParentFile().getParentFile();
      classesFolder = new File(buildFolder, "classes"); // See AndroidContentRoot
    }

    File outFolder = new File(classesFolder,
                              // Change variant name variant-release into variant/release directories
                              variantName.replace('-', File.separatorChar));
    if (outFolder.exists()) {
      return VfsUtil.findFileByIoFile(outFolder, true);
    }
    return null;
  }

  @Override
  @NotNull
  public List<VirtualFile> getModuleExternalLibraries(@NotNull Module module) {
    return AndroidRootUtil.getExternalLibraries(module);
  }
}
