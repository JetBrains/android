/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleValueImpl;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestCase;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.dsl.api.values.GradleValue.getValues;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.*;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.*;

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
    assertAbout(file()).that(projectBasePath).isDirectory();
    mySettingsFile = new File(projectBasePath, FN_SETTINGS_GRADLE);
    assertTrue(ensureCanCreateFile(mySettingsFile));

    File moduleFilePath = new File(myModule.getModuleFilePath());
    File moduleDirPath = moduleFilePath.getParentFile();
    assertAbout(file()).that(moduleDirPath).isDirectory();
    myBuildFile = new File(moduleDirPath, FN_BUILD_GRADLE);
    assertTrue(ensureCanCreateFile(myBuildFile));
    myPropertiesFile = new File(moduleDirPath, FN_GRADLE_PROPERTIES);
    assertTrue(ensureCanCreateFile(myPropertiesFile));

    File subModuleFilePath = new File(mySubModule.getModuleFilePath());
    File subModuleDirPath = subModuleFilePath.getParentFile();
    assertAbout(file()).that(subModuleDirPath).isDirectory();
    mySubModuleBuildFile = new File(subModuleDirPath, FN_BUILD_GRADLE);
    assertTrue(ensureCanCreateFile(mySubModuleBuildFile));
    mySubModulePropertiesFile = new File(subModuleDirPath, FN_GRADLE_PROPERTIES);
    assertTrue(ensureCanCreateFile(mySubModuleBuildFile));
  }

  @Override
  protected void tearDown() throws Exception {
    mySubModule = null;
    super.tearDown();
  }

  @NotNull
  @Override
  protected Module createMainModule() {
    Module mainModule = createModule(myProject.getName());

    // Create a sub module
    final VirtualFile baseDir = myProject.getBaseDir();
    assertNotNull(baseDir);
    final File moduleFile = new File(toSystemDependentName(baseDir.getPath()),
                                     SUB_MODULE_NAME + File.separatorChar + SUB_MODULE_NAME + ModuleFileType.DOT_DEFAULT_EXTENSION);
    createIfDoesntExist(moduleFile);
    myFilesToDelete.add(moduleFile);
    mySubModule = new WriteAction<Module>() {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
        Assert.assertNotNull(virtualFile);
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
    GradleSettingsModel settingsModel = GradleSettingsModelImpl.get(myProject);
    assertNotNull(settingsModel);
    return settingsModel;
  }

  @NotNull
  protected GradleBuildModel getGradleBuildModel() {
    GradleBuildModel buildModel = GradleBuildModelImpl.get(myModule);
    assertNotNull(buildModel);
    return buildModel;
  }

  @NotNull
  protected GradleBuildModel getSubModuleGradleBuildModel() {
    GradleBuildModel buildModel = GradleBuildModelImpl.get(mySubModule);
    assertNotNull(buildModel);
    return buildModel;
  }

  protected void applyChanges(@NotNull final GradleBuildModel buildModel) {
    runWriteCommandAction(myProject, buildModel::applyChanges);
    assertFalse(buildModel.isModified());
  }

  protected void applyChangesAndReparse(@NotNull final GradleBuildModel buildModel) {
    applyChanges(buildModel);
    buildModel.reparse();
  }


  protected void verifyGradleValue(@NotNull GradleNullableValue gradleValue,
                                   @NotNull String propertyName,
                                   @NotNull String propertyText) {
    verifyGradleValue(gradleValue, propertyName, propertyText, toSystemIndependentName(myBuildFile.getPath()));
  }

  public static void verifyGradleValue(@NotNull GradleNullableValue value,
                                       @NotNull String propertyName,
                                       @NotNull String propertyText,
                                       @NotNull String propertyFilePath) {
    if (!(value instanceof GradleValueImpl)) {
      fail("Gradle value implementation unknown!");
    }
    GradleValueImpl gradleValue = (GradleValueImpl)value;
    PsiElement psiElement = gradleValue.getPsiElement();
    assertNotNull(psiElement);
    assertEquals(propertyText, psiElement.getText());
    assertEquals(propertyFilePath, toSystemIndependentName(gradleValue.getFile().getPath()));
    assertEquals(propertyName, gradleValue.getPropertyName());
    assertEquals(propertyText, gradleValue.getDslText());
  }

  public static <T> void assertEquals(@NotNull String message, @Nullable T expected, @NotNull GradleValue<T> actual) {
    assertEquals(message, expected, actual.value());
  }

  public static <T> void assertEquals(@Nullable T expected, @NotNull GradleValue<T> actual) {
    assertEquals(expected, actual.value());
  }

  public static <T> void assertEquals(@NotNull String message, @NotNull List<T> expected, @Nullable List<? extends GradleValue<T>> actual) {
    assertNotNull(message, actual);
    assertWithMessage(message).that(getValues(actual)).containsExactlyElementsIn(expected);
  }

  public static <T> void assertEquals(@NotNull List<T> expected, @Nullable List<? extends GradleValue<T>> actual) {
    assertNotNull(actual);
    assertThat(getValues(actual)).containsExactlyElementsIn(expected);
  }

  public static <T> void assertEquals(@NotNull String message,
                                      @NotNull Map<String, T> expected,
                                      @Nullable Map<String, ? extends GradleValue<T>> actual) {
    assertNotNull(message, actual);
    assertWithMessage(message).that(ImmutableMap.copyOf(getValues(actual))).containsExactlyEntriesIn(ImmutableMap.copyOf(expected));
  }

  public static <T> void assertEquals(@NotNull Map<String, T> expected, @Nullable Map<String, ? extends GradleValue<T>> actual) {
    assertNotNull(actual);
    assertThat(ImmutableMap.copyOf(getValues(actual))).containsExactlyEntriesIn(ImmutableMap.copyOf(expected));
  }

  public static <T> void assertNull(@NotNull String message, @NotNull GradleNullableValue<T> nullableValue) {
    assertNull(message, nullableValue.value());
  }

  public static <T> void assertNull(@NotNull GradleNullableValue<T> nullableValue) {
    assertNull(nullableValue.value());
  }

  public static <T> void checkForValidPsiElement(@NotNull T object, @NotNull Class<? extends GradleDslBlockModel> clazz) {
    assertThat(object).isInstanceOf(clazz);
    GradleDslBlockModel model = clazz.cast(object);
    assertTrue(model.hasValidPsiElement());
  }

  public static <T> void checkForInValidPsiElement(@NotNull T object, @NotNull Class<? extends GradleDslBlockModel> clazz) {
    assertThat(object).isInstanceOf(clazz);
    GradleDslBlockModel model = clazz.cast(object);
    assertFalse(model.hasValidPsiElement());
  }

  public static <T> boolean hasPsiElement(@NotNull T object) {
    assertThat(object).isInstanceOf(GradleDslBlockModel.class);
    GradleDslBlockModel model = GradleDslBlockModel.class.cast(object);
    return model.hasValidPsiElement();
  }
}
