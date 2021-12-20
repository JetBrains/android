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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Collections2;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Assert;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.TestProjectPaths.KOTLIN_KAPT;
import static com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE_GROOVY;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_FIXTURES;
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
    assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getAdditionalClassesFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
    if (model.getSelectedVariant().getMainArtifact().getJavaResourcesFolder() != null) {
      assertContainsElements(result, pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getJavaResourcesFolder()));
    }
    assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));

    assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getClassesFolder()));
    Collection<String> unitTestAdditionalClassesFolders =
      Collections2.transform(
        model.getSelectedVariant().getUnitTestArtifact().getAdditionalClassesFolders(),
        (input) -> input == null ? null : pathToIdeaUrl(input));
    Collection<String> intersectionMainAndUnitTest = getIntersection(result, unitTestAdditionalClassesFolders);
    // Main artifact and unit test artifact may either share the same R.jar or none at all (see bug 133326990).
    if (!intersectionMainAndUnitTest.isEmpty()) {
      Assert.assertTrue(intersectionMainAndUnitTest.size() == 1);
      Assert.assertTrue(intersectionMainAndUnitTest.iterator().next().endsWith("R.jar!/"));
    }
    if (model.getSelectedVariant().getUnitTestArtifact().getJavaResourcesFolder() != null) {
      assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getJavaResourcesFolder()));
    }

    assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder()));
    assertDoesntContain(result, Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getAdditionalClassesFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
    if (model.getSelectedVariant().getAndroidTestArtifact().getJavaResourcesFolder() != null) {
      assertDoesntContain(result, pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getJavaResourcesFolder()));
    }
    assertDoesntContain(result, Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
  }

  public void testAndroidProjectWithTestFixtures() throws Exception {
    StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(true);
    try {
      loadProject(TEST_FIXTURES);
      Module module = getModule("lib");
      Set<String> result = new HashSet<>(getAmendedPaths(module, true));

      AndroidModuleModel model = AndroidModuleModel.get(module);

      Set<String> expected = new HashSet<>();
      // Unit test
      expected.add(pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getClassesFolder()));

      expected.addAll(Collections2.transform(model.getSelectedVariant().getUnitTestArtifact().getAdditionalClassesFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Android Test
      expected.add(pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder()));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getAdditionalClassesFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Test Fixtures
      expected.add(pathToIdeaUrl(model.getSelectedVariant().getTestFixturesArtifact().getClassesFolder()));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getTestFixturesArtifact().getAdditionalClassesFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getTestFixturesArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Production
      expected.add(pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getClassesFolder()));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getAdditionalClassesFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      assertEquals(expected, result);
    } finally {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride();
    }
  }

  public void testAndroidProjectWithTestOutputCorrect() throws Exception {
    loadSimpleApplication();
    Module module = getModule("app");
    Set<String> result = new HashSet<>(getAmendedPaths(module, true));

    AndroidModuleModel model = AndroidModuleModel.get(module);
    Set<String> expected = new HashSet<>();
    // Unit test
    expected.add(pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getClassesFolder()));

    expected.addAll(Collections2.transform(model.getSelectedVariant().getUnitTestArtifact().getAdditionalClassesFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
    if (model.getSelectedVariant().getUnitTestArtifact().getJavaResourcesFolder() != null) {
      expected.add(pathToIdeaUrl(model.getSelectedVariant().getUnitTestArtifact().getJavaResourcesFolder()));
    }

    // Android Test
    expected.add(pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder()));
    expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getAdditionalClassesFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
    if (model.getSelectedVariant().getAndroidTestArtifact().getJavaResourcesFolder() != null) {
      expected.add(pathToIdeaUrl(model.getSelectedVariant().getAndroidTestArtifact().getJavaResourcesFolder()));
    }
    expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));

    // Production
    expected.add(pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getClassesFolder()));
    expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getAdditionalClassesFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));
    if (model.getSelectedVariant().getMainArtifact().getJavaResourcesFolder() != null) {
      expected.add(pathToIdeaUrl(model.getSelectedVariant().getMainArtifact().getJavaResourcesFolder()));
    }
    expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                                          (input) -> input == null ? null : pathToIdeaUrl(input)));

    assertEquals(expected, result);
  }

  public void testJavaProjectOutputCorrect() throws Exception {
    loadProject(KOTLIN_KAPT);
    Module module = getModule("javaLib");
    List<String> result = getAmendedPaths(module, false);

    VirtualFile baseFile = ProjectUtil.guessModuleDir(module);
    assertNotNull(baseFile);
    String baseDir = baseFile.getPath();

    assertSize(4, result);
    assertContainsElements(result,
                           pathToIdeaUrl(new File(baseDir, join("build", "classes", "java", "main"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "classes", "kotlin", "main"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "resources", "main"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "tmp", "kapt3", "classes", "main")))
    );

    result = getAmendedPaths(module, true);
    assertSize(8, result);
    assertContainsElements(result,
                           pathToIdeaUrl(new File(baseDir, join("build", "classes", "java", "main"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "classes", "kotlin", "main"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "resources", "main"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "classes", "java", "test"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "classes", "kotlin", "test"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "resources", "test"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "tmp", "kapt3", "classes", "main"))),
                           pathToIdeaUrl(new File(baseDir, join("build", "tmp", "kapt3", "classes", "test")))

    );
  }

  public void testAndroidModulesRecursiveAndJavaModulesNot() throws Exception {
    loadProject(PSD_SAMPLE_GROOVY);

    Module appModule = getModule("app");
    Module libModule = getModule("jav");

    OrderEnumerationHandler appHandler = new AndroidGradleOrderEnumeratorHandlerFactory().createHandler(appModule);
    assertTrue(appHandler.shouldProcessDependenciesRecursively());
    OrderEnumerationHandler libHandler = new AndroidGradleOrderEnumeratorHandlerFactory().createHandler(libModule);
    assertFalse(libHandler.shouldProcessDependenciesRecursively());
  }

  private static List<String> getAmendedPaths(@NotNull Module module, boolean includeTests) {
    ModuleRootModel moduleRootModel = mock(ModuleRootModel.class);
    when(moduleRootModel.getModule()).thenReturn(module);
    OrderEnumerationHandler handler = new AndroidGradleOrderEnumeratorHandlerFactory().createHandler(module);

    List<String> result = new ArrayList<>();
    handler.addCustomModuleRoots(OrderRootType.CLASSES, moduleRootModel, result, true, includeTests);
    return result;
  }

  @NotNull
  private static <E> Collection<E> getIntersection(@NotNull Collection<E> collection1, @NotNull Collection<E> collection2) {
    Collection<E> intersection = new HashSet<>(collection1);
    intersection.retainAll(collection2);
    return intersection;
  }
}
