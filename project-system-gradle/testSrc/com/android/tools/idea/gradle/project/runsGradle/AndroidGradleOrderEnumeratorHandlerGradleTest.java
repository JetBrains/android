/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.runsGradle;

import static com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject.openPreparedTestProject;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static com.intellij.testFramework.UsefulTestCase.assertDoesntContain;
import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeJavaArtifact;
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet;
import com.android.tools.idea.gradle.project.AndroidGradleOrderEnumeratorHandlerFactory;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.google.common.collect.Collections2;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.testFramework.RunsInEdt;
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
public class AndroidGradleOrderEnumeratorHandlerGradleTest {

  @Rule
  public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();

  @Test
  public void testAndroidProjectOutputCorrect() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module module = gradleModule(project, ":app", IdeModuleWellKnownSourceSet.MAIN);
      Collection<String> result = getAmendedPaths(module, false);

      GradleAndroidModel model = GradleAndroidModel.get(module);
      assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getClassesFolder(),
                                                            (input) -> input == null ? null : pathToIdeaUrl(input)));
      assertContainsElements(result, Collections2.transform(model.getSelectedVariant().getMainArtifact().getGeneratedResourceFolders(),
                                                            (input) -> input == null ? null : pathToIdeaUrl(input)));

      IdeJavaArtifact unitTestArtifact =
        model.getSelectedVariant().getHostTestArtifacts().stream().filter(it -> it.getName() == IdeArtifactName.UNIT_TEST).findFirst()
          .orElse(null);
      assertNotNull(unitTestArtifact);
      Collection<String> unitTestClassesFolders =
        Collections2.transform(unitTestArtifact.getClassesFolder(), (input) -> input == null ? null : pathToIdeaUrl(input));
      Collection<String> intersectionMainAndUnitTest = getIntersection(result, unitTestClassesFolders);
      // Main artifact and unit test artifact may either share the same R.jar or none at all (see bug 133326990).
      if (!intersectionMainAndUnitTest.isEmpty()) {
        Assert.assertTrue(intersectionMainAndUnitTest.size() == 1);
        Assert.assertTrue(intersectionMainAndUnitTest.iterator().next().endsWith("R.jar!/"));
      }

      IdeAndroidArtifact androidArtifact =
        model.getSelectedVariant().getDeviceTestArtifacts().stream().filter(it -> it.getName() == IdeArtifactName.ANDROID_TEST).toList()
          .get(0);
      assertDoesntContain(
        result, Collections2.transform(androidArtifact.getClassesFolder(), (input) -> input == null ? null : pathToIdeaUrl(input)));
      assertDoesntContain(
        result,
        Collections2.transform(androidArtifact.getGeneratedResourceFolders(), (input) -> input == null ? null : pathToIdeaUrl(input)));
    });
  }

  @Test
  public void testAndroidProjectWithTestFixtures() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.TEST_FIXTURES);
    openPreparedTestProject(preparedProject, project -> {
      Module module = gradleModule(project, ":lib", IdeModuleWellKnownSourceSet.TEST_FIXTURES);
      Set<String> result = new HashSet<>(getAmendedPaths(module, true));

      GradleAndroidModel model = GradleAndroidModel.get(module);

      Set<String> expected = new HashSet<>();
      // Test Fixtures
      expected.addAll(Collections2.transform(model.getSelectedVariant().getTestFixturesArtifact().getClassesFolder(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(Collections2.transform(model.getSelectedVariant().getTestFixturesArtifact().getGeneratedResourceFolders(),
                                             (input) -> input == null ? null : pathToIdeaUrl(input)));

      assertEquals(expected, result);
    });
  }

  @Test
  public void testAndroidProjectWithTestOutputCorrect() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    openPreparedTestProject(preparedProject, project -> {
      Module module = gradleModule(project, ":app", IdeModuleWellKnownSourceSet.ANDROID_TEST);
      Set<String> result = new HashSet<>(getAmendedPaths(module, true));

      GradleAndroidModel model = GradleAndroidModel.get(module);
      Set<String> expected = new HashSet<>();
      // Android Test
      IdeAndroidArtifact androidArtifact =
        model.getSelectedVariant().getDeviceTestArtifacts().stream().filter(it -> it.getName() == IdeArtifactName.ANDROID_TEST).toList()
          .get(0);
      expected.addAll(Collections2.transform(androidArtifact.getClassesFolder(), (input) -> input == null ? null : pathToIdeaUrl(input)));
      expected.addAll(
        Collections2.transform(androidArtifact.getGeneratedResourceFolders(), (input) -> input == null ? null : pathToIdeaUrl(input)));
      assertEquals(expected, result);
    });
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
