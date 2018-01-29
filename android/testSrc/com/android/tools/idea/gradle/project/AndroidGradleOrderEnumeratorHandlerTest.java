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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Collections2;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.TestProjectPaths.JAVA_LIB;
import static com.intellij.openapi.util.io.FileUtil.join;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AndroidGradleOrderEnumeratorHandlerTest extends AndroidGradleTestCase {

  public void testAndroidProjectOutputCorrect() throws Exception {
    loadSimpleApplication();
    Module module = getModule("app");
    Collection<String> result = getAmendedPaths(module, false);

    AndroidModuleModel model = AndroidModuleModel.get(module);
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getClassesFolder()));
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getJavaResourcesFolder()));
    assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
    assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getClassesFolder()));
    assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getJavaResourcesFolder()));
    assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder()));
    assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getJavaResourcesFolder()));
    assertDoesntContain(result, Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
  }

  public void testAndroidProjectWithTestOutputCorrect() throws Exception {
    loadSimpleApplication();
    Module module = getModule("app");
    Collection<String> result = getAmendedPaths(module, true);

    AndroidModuleModel model = AndroidModuleModel.get(module);
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getClassesFolder()));
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getJavaResourcesFolder()));
    assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getClassesFolder()));
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getJavaResourcesFolder()));
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder()));
    assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getJavaResourcesFolder()));
    assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
  }

  public void testJavaProjectOutputCorrect() throws Exception {
    loadProject(JAVA_LIB);
    Module module = getModule("lib");
    Collection<String> result = getAmendedPaths(module, false);

    JavaModuleModel model = JavaModuleModel.get(module);
    assertContainsElements(result, pathToIdeaUrl(model.getCompilerOutput().getMainClassesDir()));
    assertContainsElements(result, pathToIdeaUrl(model.getCompilerOutput().getMainResourcesDir()));
    assertContainsElements(result, pathToIdeaUrl(new File(model.getBuildFolderPath(), join("classes", "kotlin", "main"))));
    assertDoesntContain(result, pathToIdeaUrl(model.getCompilerOutput().getTestClassesDir()));
    assertDoesntContain(result, pathToIdeaUrl(model.getCompilerOutput().getTestResourcesDir()));
    assertDoesntContain(result, pathToIdeaUrl(new File(model.getBuildFolderPath(), join("classes", "kotlin", "test"))));
  }

  public void testJavaProjectWithTestOutputCorrect() throws Exception {
    loadProject(JAVA_LIB);
    Module module = getModule("lib");
    Collection<String> result = getAmendedPaths(module, true);

    JavaModuleModel model = JavaModuleModel.get(module);
    assertContainsElements(result, pathToIdeaUrl(model.getCompilerOutput().getMainClassesDir()));
    assertContainsElements(result, pathToIdeaUrl(model.getCompilerOutput().getMainResourcesDir()));
    assertContainsElements(result, pathToIdeaUrl(new File(model.getBuildFolderPath(), join("classes", "kotlin", "main"))));
    assertContainsElements(result, pathToIdeaUrl(model.getCompilerOutput().getTestClassesDir()));
    assertContainsElements(result, pathToIdeaUrl(model.getCompilerOutput().getTestResourcesDir()));
    assertContainsElements(result, pathToIdeaUrl(new File(model.getBuildFolderPath(), join("classes", "kotlin", "test"))));
  }

  private static Collection<String> getAmendedPaths(@NotNull Module module, boolean includeTests) {
    ModuleRootModel moduleRootModel = mock(ModuleRootModel.class);
    when(moduleRootModel.getModule()).thenReturn(module);
    OrderEnumerationHandler handler = new AndroidGradleOrderEnumeratorHandlerFactory().createHandler(module);

    Collection<String> result = new ArrayList<>();
    handler.addCustomModuleRoots(OrderRootType.CLASSES, moduleRootModel, result, true, includeTests);
    return result;
  }
}
