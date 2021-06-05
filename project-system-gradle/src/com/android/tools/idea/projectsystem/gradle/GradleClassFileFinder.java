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

import static com.intellij.openapi.util.io.FileUtil.join;

import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.project.ModuleBasedClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;

public class GradleClassFileFinder extends ModuleBasedClassFileFinder {
  @NotNull private static final String CLASSES_FOLDER_NAME = "classes";
  @NotNull private static final String RESOURCES_FOLDER_NAME = "resources";
  @NotNull private static final String MAIN_FOLDER_NAME = "main";
  @NotNull private static final String TEST_FOLDER_NAME = "test";
  @NotNull private static final String KOTLIN_FOLDER_NAME = "kotlin";

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

    for (VirtualFile outputDir : getModuleCompileOutputs(module)) {
      file = ClassFileFinderUtil.findClassFileInOutputRoot(outputDir, className);
      if (file != null) {
        return file;
      }
    }
    return null;
  }

  @NotNull
  private static Collection<VirtualFile> getCompilerOutputRoots(@NotNull AndroidModuleModel model) {
    IdeVariant variant = model.getSelectedVariant();
    String variantName = variant.getName();
    IdeAndroidArtifact mainArtifactInfo = model.getMainArtifact();
    File classesFolder = mainArtifactInfo.getClassesFolder();
    ImmutableList.Builder<VirtualFile> compilerOutputs = new ImmutableList.Builder<>();

    //noinspection ConstantConditions
    if (classesFolder != null) {
      File outFolder = new File(classesFolder,
                                // Change variant name variant-release into variant/release directories
                                variantName.replace('-', File.separatorChar));
      if (outFolder.exists()) {
        VirtualFile file = VfsUtil.findFileByIoFile(outFolder, true);
        if (file != null) {
          compilerOutputs.add(file);
        }
      }
    }

    for (File additionalFolder : mainArtifactInfo.getAdditionalClassesFolders()) {
      VirtualFile file = VfsUtil.findFileByIoFile(additionalFolder, true);
      if (file != null) {
        compilerOutputs.add(file);
      }
    }

    return compilerOutputs.build();
  }

  // This method is obtained from AndroidGradleOrderEnumeratorHandlerFactory#getJavaAndKotlinCompileOutputFolders
  @NotNull
  private static Collection<File> getJavaAndKotlinCompilerOutputFolders(@NotNull JavaModuleModel javaModel,
                                                                        boolean includeProduction,
                                                                        boolean includeTests) {
    Collection<File> toAdd = new LinkedList<>();
    File mainClassesFolderPath = null;
    File mainResourcesFolderPath = null;
    File testClassesFolderPath = null;
    File testResourcesFolderPath = null;
    File mainKotlinClassesFolderPath = null;
    File testKotlinClassesFolderPath = null;

    ExtIdeaCompilerOutput compilerOutput = javaModel.getCompilerOutput();
    if (compilerOutput != null) {
      mainClassesFolderPath = compilerOutput.getMainClassesDir();
      mainResourcesFolderPath = compilerOutput.getMainResourcesDir();
      testClassesFolderPath = compilerOutput.getTestClassesDir();
      testResourcesFolderPath = compilerOutput.getTestResourcesDir();
    }

    File buildFolderPath = javaModel.getBuildFolderPath();
    if (javaModel.isBuildable()) {
      if (mainClassesFolderPath == null) {
        // Guess default output folder
        mainClassesFolderPath = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, MAIN_FOLDER_NAME));
      }
      if (mainResourcesFolderPath == null) {
        // Guess default output folder
        mainResourcesFolderPath = new File(buildFolderPath, join(RESOURCES_FOLDER_NAME, MAIN_FOLDER_NAME));
      }
      if (testClassesFolderPath == null) {
        // Guess default output folder
        testClassesFolderPath = new File(buildFolderPath, join(CLASSES_FOLDER_NAME, TEST_FOLDER_NAME));
      }
      if (testResourcesFolderPath == null) {
        // Guess default output folder
        testResourcesFolderPath = new File(buildFolderPath, join(RESOURCES_FOLDER_NAME, TEST_FOLDER_NAME));
      }
    }

    // For Kotlin models it is possible that the javaModel#isBuildable returns false since no javaCompile task exists.
    // As a result we always need to try and look for Kotlin output folder.
    if (buildFolderPath != null) {
      // We try to guess Kotlin output folders (Gradle default), since we cannot obtain that from Kotlin model for now.
      File kotlinClasses = buildFolderPath.toPath().resolve(CLASSES_FOLDER_NAME).resolve(KOTLIN_FOLDER_NAME).toFile();
      // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
      // is multiple definitions of the same class existed in both the test and the main artifact.
      if (includeTests) {
        testKotlinClassesFolderPath = new File(kotlinClasses, TEST_FOLDER_NAME);
      }
      if (includeProduction) {
        mainKotlinClassesFolderPath = new File(kotlinClasses, MAIN_FOLDER_NAME);
      }
    }

    // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
    // is multiple definitions of the same class existed in both the test and the main artifact.
    if (includeTests) {
      if (testClassesFolderPath != null) {
        toAdd.add(testClassesFolderPath);
      }
      if (testKotlinClassesFolderPath != null) {
        toAdd.add(testKotlinClassesFolderPath);
      }
      if (testResourcesFolderPath != null) {
        toAdd.add(testResourcesFolderPath);
      }
    }

    if (includeProduction) {
      if (mainClassesFolderPath != null) {
        toAdd.add(mainClassesFolderPath);
      }
      if (mainKotlinClassesFolderPath != null) {
        toAdd.add(mainKotlinClassesFolderPath);
      }
      if (mainResourcesFolderPath != null) {
        toAdd.add(mainResourcesFolderPath);
      }
    }

    return toAdd;
  }

  @NotNull
  private static Collection<VirtualFile> getModuleCompileOutputs(@NotNull Module module) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel != null) {
      return getCompilerOutputRoots(androidModel);
    }

    // The module is not an Android module. Check for regular Java outputs.
    JavaModuleModel javaModel = JavaModuleModel.get(module);
    if (javaModel != null) {
      return getJavaAndKotlinCompilerOutputFolders(javaModel, true, false).stream()
        .map(path -> VfsUtil.findFileByIoFile(path, true))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }

    return ImmutableList.of();
  }
}
