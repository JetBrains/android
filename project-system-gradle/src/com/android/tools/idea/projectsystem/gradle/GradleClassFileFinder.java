/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle;

import static com.android.SdkConstants.FN_R_CLASS_JAR;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.project.ModuleBasedClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleClassFileFinder extends ModuleBasedClassFileFinder {

  public GradleClassFileFinder(@NotNull Module module) {
    super(module);
  }

  @Override
  @Nullable
  protected VirtualFile findClassFileInModule(@NotNull Module module, @NotNull String className) {
    VirtualFile file = super.findClassFileInModule(module, className);
    if (file != null) {
      return file;
    }

    AndroidModuleModel model = AndroidModuleModel.get(module);
    if (model == null) {
      return null;
    }
    for (VirtualFile outputDir : getCompilerOutputRoots(model)) {
      file = ClassFileFinderUtil.findClassFileInOutputRoot(outputDir, className);
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
    ImmutableList.Builder<VirtualFile> compilerOutputs = new ImmutableList.Builder<>();

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
        compilerOutputs.add(file);
      }
    }

    for (File additionalFolder : mainArtifactInfo.getAdditionalClassesFolders()) {
      VirtualFile file = VfsUtil.findFileByIoFile(additionalFolder, true);
      if (file != null) {
        compilerOutputs.add(file);
      }
    }

    VirtualFile rJar = findRJar(model);
    if (rJar != null) {
      compilerOutputs.add(rJar);
    }

    return compilerOutputs.build();
  }

  /**
   * Finds the R.jar file of the given {@link AndroidArtifact}, if it exists.
   */
  @Nullable
  private static VirtualFile findRJar(AndroidModuleModel model) {
    // TODO(b/133326990): read this from the model
    String variantName = model.getSelectedVariant().getName();
    File classesFolder = model.getMainArtifact().getClassesFolder();

    File p1 = classesFolder.getParentFile();
    if (p1 == null) return null;

    File p2 = p1.getParentFile();
    if (p2 == null) return null;

    File p3 = p2.getParentFile();
    if (p3 == null) return null;

    File rJar = FileUtils.join(p3, "compile_and_runtime_not_namespaced_r_class_jar", variantName, FN_R_CLASS_JAR);
    return VfsUtil.findFileByIoFile(rJar, true);
  }
}
