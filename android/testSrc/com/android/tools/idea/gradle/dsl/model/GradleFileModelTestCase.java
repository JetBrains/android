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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.*;
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
  @NotNull
  protected Module createMainModule() throws IOException {
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

  @NotNull
  protected String loadBuildFile() throws IOException {
    return loadFile(myBuildFile);
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

  protected void verifyPropertyModel(@NotNull GradlePropertyModel propertyModel,
                                     @NotNull String propertyName,
                                     @NotNull String propertyText) {
    verifyPropertyModel(propertyModel, propertyName, propertyText, toSystemIndependentName(myBuildFile.getPath()));
  }

  protected void verifyPropertyModel(@NotNull GradlePropertyModel propertyModel,
                                     @NotNull String propertyName,
                                     @NotNull String propertyText,
                                     @NotNull String propertyFilePath) {
    assertNotNull(propertyModel.getPsiElement());
    assertEquals(propertyText, propertyModel.getValue(STRING_TYPE));
    assertEquals(propertyFilePath, propertyModel.getGradleFile().getPath());
    assertEquals(propertyName, propertyModel.getFullyQualifiedName());
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

  public static void assertEquals(@NotNull String message, @Nullable String expected, @NotNull GradlePropertyModel actual) {
    assertEquals(message, expected, actual.getValue(STRING_TYPE));
  }

  public static void assertEquals(@NotNull String message, @Nullable Boolean expected, @NotNull GradlePropertyModel actual) {
    assertEquals(message, expected, actual.getValue(BOOLEAN_TYPE));
  }

  public static void assertEquals(@NotNull String message, @Nullable Integer expected, @NotNull GradlePropertyModel actual) {
    assertEquals(message, expected, actual.getValue(INTEGER_TYPE));
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

  public static void assertEquals(@NotNull String message, @NotNull List<Object> expected, @Nullable GradlePropertyModel propertyModel) {
    verifyListProperty(message, propertyModel, expected);
  }

  public static <T> void assertEquals(@NotNull String message,
                                  @NotNull Map<String, T> expected,
                                  @Nullable GradlePropertyModel propertyModel) {
    verifyMapProperty(message, propertyModel, new HashMap<>(expected));
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

  public static void assertMissingProperty(@NotNull GradlePropertyModel model) {
    assertEquals(NONE, model.getValueType());
  }

  public static void assertMissingProperty(@NotNull String message, @NotNull GradlePropertyModel model) {
    assertEquals(message, NONE, model.getValueType());
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

  public static void assertEquals(@NotNull GradlePropertyModel model, @NotNull GradlePropertyModel other) {
    assertTrue(model + " and " + other + " are not equal", areModelsEqual(model, other));
  }

  public static boolean areModelsEqual(@NotNull GradlePropertyModel model, @NotNull GradlePropertyModel other) {
    Object value = model.getValue(OBJECT_TYPE);
    Object otherValue = other.getValue(OBJECT_TYPE);

    if (!Objects.equals(value, otherValue)) {
      return false;
    }

    return model.getValueType().equals(other.getValueType()) &&
           model.getPropertyType().equals(other.getPropertyType()) &&
           model.getGradleFile().equals(other.getGradleFile()) &&
           model.getFullyQualifiedName().equals(other.getFullyQualifiedName());
  }


  public static <T> void verifyPropertyModel(GradlePropertyModel model, TypeReference<T> type, T value,
                                             ValueType valueType, PropertyType propertyType, int dependencies) {
    assertEquals(valueType, model.getValueType());
    assertEquals(value, model.getValue(type));
    assertEquals(propertyType, model.getPropertyType());
    assertEquals(dependencies, model.getDependencies().size());
  }

  public static void verifyPropertyModel(@NotNull String message, @NotNull GradlePropertyModel model, @NotNull Object expected) {
    switch (model.getValueType()) {
      case INTEGER:
        assertEquals(message, expected, model.getValue(INTEGER_TYPE));
        break;
      case STRING:
        assertEquals(message, expected, model.getValue(STRING_TYPE));
        break;
      case BOOLEAN:
        assertEquals(message, expected, model.getValue(BOOLEAN_TYPE));
        break;
      case REFERENCE:
        assertEquals(message, expected, model.resolve().getValue(STRING_TYPE));
        break;
      default:
        fail("Type for model: " + model + " was unexpected, " + model.getValueType());
    }
  }

  public static void verifyListProperty(@NotNull String message,
                                        @Nullable GradlePropertyModel model,
                                        @NotNull List<Object> expectedValues) {
    assertNotNull(message, model);
    assertEquals(message, LIST, model.getValueType());
    List<GradlePropertyModel> actualValues = model.getValue(LIST_TYPE);
    assertNotNull(message, actualValues);
    assertEquals(message, expectedValues.size(), actualValues.size());
    for (int i = 0; i < actualValues.size(); i++) {
      GradlePropertyModel tempModel = actualValues.get(i);
      verifyPropertyModel(message, tempModel, expectedValues.get(i));
    }
  }

  public static void verifyMapProperty(@Nullable GradlePropertyModel model,
                                       @NotNull Map<String, Object> expectedValues) {
    verifyMapProperty("verifyMapProperty", model, expectedValues);
  }

  public static void verifyMapProperty(@NotNull String message,
                                       @Nullable GradlePropertyModel model,
                                       @NotNull Map<String, Object> expectedValues) {
    assertNotNull(message, model);
    assertEquals(message, MAP, model.getValueType());
    Map<String, GradlePropertyModel> actualValues = model.getValue(MAP_TYPE);
    assertNotNull(message, actualValues);
    assertEquals(message, expectedValues.entrySet().size(), actualValues.entrySet().size());
    for (String key : actualValues.keySet()) {
      GradlePropertyModel tempModel = actualValues.get(key);
      Object expectedValue = expectedValues.get(key);
      assertNotNull(message, expectedValue);
      verifyPropertyModel(message, tempModel, expectedValue);
    }
  }

  // This method is not suitable for lists or maps in lists, these must be verified manually.
  public static void verifyListProperty(GradlePropertyModel model,
                                        List<Object> expectedValues,
                                        PropertyType propertyType,
                                        int dependencies) {
    verifyListProperty("verifyListProperty", model, expectedValues);
    assertEquals(propertyType, model.getPropertyType());
    assertEquals(dependencies, model.getDependencies().size());
  }

  public static <T> void verifyPropertyModel(GradlePropertyModel model,
                                             TypeReference<T> type,
                                             T value,
                                             ValueType valueType,
                                             PropertyType propertyType,
                                             int dependencies,
                                             String name,
                                             String fullName) {
    verifyPropertyModel(model, type, value, valueType, propertyType, dependencies);
    assertEquals(name, model.getName());
    assertEquals(fullName, model.getFullyQualifiedName());
  }
}
