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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assert_;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.ensureCanCreateFile;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;

public abstract class GradleFileModelTestCase extends PlatformTestCase {
  protected static final String SUB_MODULE_NAME = "gradleModelTest";

  protected Module mySubModule;

  protected File mySettingsFile;
  protected File myBuildFile;
  protected File myPropertiesFile;
  protected File mySubModuleBuildFile;
  protected File mySubModulePropertiesFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String basePath = myProject.getBasePath();
    assertNotNull(basePath);
    File projectBasePath = new File(basePath);
    assert_().about(file()).that(projectBasePath).isDirectory();
    mySettingsFile = new File(projectBasePath, FN_SETTINGS_GRADLE);
    assertTrue(ensureCanCreateFile(mySettingsFile));

    File moduleFilePath = new File(myModule.getModuleFilePath());
    File moduleDirPath = moduleFilePath.getParentFile();
    assert_().about(file()).that(moduleDirPath).isDirectory();
    myBuildFile = new File(moduleDirPath, FN_BUILD_GRADLE);
    assertTrue(ensureCanCreateFile(myBuildFile));
    myPropertiesFile = new File(moduleDirPath, FN_GRADLE_PROPERTIES);
    assertTrue(ensureCanCreateFile(myPropertiesFile));

    File subModuleFilePath = new File(mySubModule.getModuleFilePath());
    File subModuleDirPath = subModuleFilePath.getParentFile();
    assert_().about(file()).that(subModuleDirPath).isDirectory();
    mySubModuleBuildFile = new File(subModuleDirPath, FN_BUILD_GRADLE);
    assertTrue(ensureCanCreateFile(mySubModuleBuildFile));
    mySubModulePropertiesFile = new File(subModuleDirPath, FN_GRADLE_PROPERTIES);
    assertTrue(ensureCanCreateFile(mySubModuleBuildFile));
  }

  @Override
  protected Module createMainModule() throws IOException {
    Module mainModule = createModule(myProject.getName());

    // Create a sub module
    final VirtualFile baseDir = myProject.getBaseDir();
    assertNotNull(baseDir);
    final File moduleFile = new File(FileUtil.toSystemDependentName(baseDir.getPath()),
                                     SUB_MODULE_NAME + File.separatorChar + SUB_MODULE_NAME + ModuleFileType.DOT_DEFAULT_EXTENSION);
    FileUtil.createIfDoesntExist(moduleFile);
    myFilesToDelete.add(moduleFile);
    mySubModule = new WriteAction<Module>() {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
        assertNotNull(virtualFile);
        Module module = ModuleManager.getInstance(myProject).newModule(virtualFile.getPath(), getModuleType().getId());
        module.getModuleFile();
        result.setResult(module);
      }
    }.execute().getResultObject();

    return mainModule;
  }

  protected void writeToSettingsFile(@NotNull String text) throws IOException {
    writeToFile(mySettingsFile, text);
  }

  protected void writeToBuildFile(@NotNull String text) throws IOException {
    writeToFile(myBuildFile, text);
  }

  protected void writeToPropertiesFile(@NotNull String text) throws IOException {
    writeToFile(myPropertiesFile, text);
  }

  protected void writeToSubModuleBuildFile(@NotNull String text) throws IOException {
    writeToFile(mySubModuleBuildFile, text);
  }

  protected void writeToSubModulePropertiesFile(@NotNull String text) throws IOException {
    writeToFile(mySubModulePropertiesFile, text);
  }

  @NotNull
  protected GradleSettingsModel getGradleSettingsModel() {
    GradleSettingsModel settingsModel = GradleSettingsModel.get(myProject);
    assertNotNull(settingsModel);
    return settingsModel;
  }

  @NotNull
  protected GradleBuildModel getGradleBuildModel() {
    GradleBuildModel buildModel = GradleBuildModel.get(myModule);
    assertNotNull(buildModel);
    return buildModel;
  }

  @NotNull
  protected GradleBuildModel getSubModuleGradleBuildModel() {
    GradleBuildModel buildModel = GradleBuildModel.get(mySubModule);
    assertNotNull(buildModel);
    return buildModel;
  }

  protected void applyChanges(@NotNull final GradleBuildModel buildModel) {
    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    assertFalse(buildModel.isModified());
  }

  protected void applyChangesAndReparse(@NotNull final GradleBuildModel buildModel) {
    applyChanges(buildModel);
    buildModel.reparse();
  }

  public static <T> void assertEquals(@NotNull String message, @NotNull T expected, @NotNull GradleNullableValue<T> actual) {
    assertEquals(message, expected, actual.value());
  }

  public static <T> void assertEquals(@NotNull T expected, @NotNull GradleNullableValue<T> actual) {
    assertEquals(expected, actual.value());
  }

  public static <T> void assertEquals(@NotNull String message, @NotNull T expected, @NotNull GradleNotNullValue<T> actual) {
    assertEquals(message, expected, actual.value());
  }

  public static <T> void assertEquals(@NotNull T expected, @NotNull GradleNotNullValue<T> actual) {
    assertEquals(expected, actual.value());
  }
}
