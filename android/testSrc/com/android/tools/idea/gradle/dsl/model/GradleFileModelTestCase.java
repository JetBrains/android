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
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.ensureCanCreateFile;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static org.fest.assertions.Assertions.assertThat;

public abstract class GradleFileModelTestCase extends PlatformTestCase {
  protected static final String SUB_MODULE_NAME = "gradleModelTest";

  protected Module mySubModule;

  protected File mySettingsFile;
  protected File myBuildFile;
  protected File mySubModuleBuildFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String basePath = myProject.getBasePath();
    assertNotNull(basePath);
    File projectBasePath = new File(basePath);
    assertThat(projectBasePath).isDirectory();
    mySettingsFile = new File(projectBasePath, FN_SETTINGS_GRADLE);
    assertTrue(ensureCanCreateFile(mySettingsFile));

    File moduleFilePath = new File(myModule.getModuleFilePath());
    File moduleDirPath = moduleFilePath.getParentFile();
    assertThat(moduleDirPath).isDirectory();
    myBuildFile = new File(moduleDirPath, FN_BUILD_GRADLE);
    assertTrue(ensureCanCreateFile(myBuildFile));

    File subModuleFilePath = new File(mySubModule.getModuleFilePath());
    File subModuleDirPath = subModuleFilePath.getParentFile();
    assertThat(subModuleDirPath).isDirectory();
    mySubModuleBuildFile = new File(subModuleDirPath, FN_BUILD_GRADLE);
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

  @Override
  protected void checkForSettingsDamage(@NotNull List<Throwable> exceptions) {
    // for this test we don't care for this check
  }

  protected void writeToSettingsFile(@NotNull String text) throws IOException {
    writeToFile(mySettingsFile, text);
  }

  protected void writeToBuildFile(@NotNull String text) throws IOException {
    writeToFile(myBuildFile, text);
  }

  protected void writeToSubModuleBuildFile(@NotNull String text) throws IOException {
    writeToFile(mySubModuleBuildFile, text);
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
}
