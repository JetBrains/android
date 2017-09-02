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
import com.android.builder.model.level2.Library;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Contains Android-Gradle related state necessary for finding classes (from build output)
 * and jars (external libraries).
 */
public class AndroidGradleClassJarProvider extends ClassJarProvider {

  @Override
  @Nullable
  public VirtualFile findModuleClassFile(@NotNull String className, @NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    if (model == null) {
      return null;
    }
    for (VirtualFile outputDir : getCompilerOutputRoots(model)) {
      VirtualFile file = ClassJarProvider.findClassFileInPath(outputDir, className);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  @NotNull
  private static Collection<VirtualFile> getCompilerOutputRoots(@NotNull AndroidModuleModel model) {
    Variant variant = model.getSelectedVariant();
    String variantName = variant.getName();
    AndroidArtifact mainArtifactInfo = model.getMainArtifact();
    File classesFolder = mainArtifactInfo.getClassesFolder();
    ImmutableList.Builder<VirtualFile> listBuilder = new ImmutableList.Builder<>();

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
      VirtualFile file = VfsUtil.findFileByIoFile(outFolder, true);
      if (file != null) {
        listBuilder.add(file);
      }
    }

    for (File additionalFolder : mainArtifactInfo.getAdditionalClassesFolders()) {
      VirtualFile file = VfsUtil.findFileByIoFile(additionalFolder, true);
      if (file != null) {
        listBuilder.add(file);
      }
    }

    return listBuilder.build();
  }

  @Override
  @NotNull
  public List<File> getModuleExternalLibraries(@NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);
    if (model == null) {
      return Lists.transform(AndroidRootUtil.getExternalLibraries(module), VfsUtilCore::virtualToIoFile);
    }

    return Stream.concat(model.getSelectedMainCompileLevel2Dependencies().getAndroidLibraries().stream()
                           .map(library -> new File(library.getJarFile())),
                         model.getSelectedMainCompileLevel2Dependencies().getJavaLibraries().stream()
                           .map(Library::getArtifact))
      .collect(ImmutableCollectors.toImmutableList());
  }
}
