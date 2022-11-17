/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.openPreparedTestProject;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static com.intellij.testFramework.UsefulTestCase.assertDoesntContain;
import static com.intellij.testFramework.UsefulTestCase.assertSize;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.android.tools.idea.testing.JavaModuleModelBuilder;
import com.google.common.collect.Collections2;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class AndroidGradleOrderEnumeratorHandlerTest {

  @Rule
  public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();

  @Test
  public void testAndroidProjectOutputCorrect() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module module = gradleModule(project, ":app");
      Collection<String> result = getAmendedPaths(module, false);

      GradleAndroidModel model = GradleAndroidModel.get(module);
      assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getClassesFolder(),
                                                            (input) -> input == null ? null : pathToIdeaUrl(input)));
      assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                                            (input) -> input == null ? null : pathToIdeaUrl(input)));

      Collection<String> unitTestClassesFolders =
        Collections2.transform(
          model.getSelectedVariant().getUnitTestArtifact().getClassesFolder(),
          (input) -> input == null ? null : pathToIdeaUrl(input));
      Collection<String> intersectionMainAndUnitTest = getIntersection(result, unitTestClassesFolders);
      // Main artifact and unit test artifact may either share the same R.jar or none at all (see bug 133326990).
      if (!intersectionMainAndUnitTest.isEmpty()) {
        Assert.assertTrue(intersectionMainAndUnitTest.size() == 1);
        Assert.assertTrue(intersectionMainAndUnitTest.iterator().next().endsWith("R.jar!/"));
      }

      assertDoesntContain(result, Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder(),
                                                         (input) -> input == null ? null : pathToIdeaUrl(input)));
      assertDoesntContain(result, Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                                         (input) -> input == null ? null : pathToIdeaUrl(input)));
    });
  }

  @Test
  public void testAndroidProjectWithTestFixtures() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.TEST_FIXTURES);
    openPreparedTestProject(preparedProject, project -> {
      Module module = gradleModule(project, ":lib");
      Set<String> result = new HashSet<>(getAmendedPaths(module, true));

      GradleAndroidModel model = GradleAndroidModel.get(module);

      Set<String> expected = new HashSet<>();
      // Unit test
      expected.addAll(Collections2.transform(model.getSelectedVariant().getUnitTestArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Android Test
      expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Test Fixtures
      expected.addAll(Collections2.transform(model.getSelectedVariant().getTestFixturesArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getTestFixturesArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Production
      expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      assertEquals(expected, result);
    });
  }

  @Test
  public void testAndroidProjectWithTestOutputCorrect() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module module = gradleModule(project, ":app");
      Set<String> result = new HashSet<>(getAmendedPaths(module, true));

      GradleAndroidModel model = GradleAndroidModel.get(module);
      Set<String> expected = new HashSet<>();
      // Unit test
      expected.addAll(Collections2.transform(model.getSelectedVariant().getUnitTestArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Android Test
      expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getAndroidTestArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      // Production
      expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      assertEquals(expected, result);
    });
  }

  @Test
  public void testJavaProjectOutputCorrect() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.KOTLIN_KAPT);
    openPreparedTestProject(preparedProject, project -> {
      Module module = gradleModule(project, ":javaLib");
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
    });
  }

  @RunsInEdt
  public static class NonGradle {
    @Rule
    public EdtAndroidProjectRule projectRule = onEdt(AndroidProjectRule.withAndroidModels());

    @Test
    public void testAndroidModulesRecursiveAndJavaModulesNot() {
      projectRule.setupProjectFrom(JavaModuleModelBuilder.getRootModuleBuilder(),
                                   new AndroidModuleModelBuilder(":app", "debug", new AndroidProjectBuilder()),
                                   new JavaModuleModelBuilder(":jav", true));

      Module appModule = gradleModule(projectRule.getProject(), ":app");
      Module libModule = gradleModule(projectRule.getProject(), ":jav");

      OrderEnumerationHandler appHandler = new AndroidGradleOrderEnumeratorHandlerFactory().createHandler(appModule);
      assertTrue(appHandler.shouldProcessDependenciesRecursively());
      OrderEnumerationHandler libHandler = new AndroidGradleOrderEnumeratorHandlerFactory().createHandler(libModule);
      assertFalse(libHandler.shouldProcessDependenciesRecursively());
    }
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
