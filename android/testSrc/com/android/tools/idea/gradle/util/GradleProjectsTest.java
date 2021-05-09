/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.openPreparedProject;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.prepareGradleProject;
import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;

import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.android.tools.idea.testing.GradleIntegrationTest;
import com.android.tools.idea.testing.JavaModuleModelBuilder;
import com.android.tools.idea.testing.TestProjectPaths;
import com.google.common.truth.Expect;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import kotlin.Unit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link GradleProjects}.
 */
@RunWith(JUnit4.class)
@RunsInEdt
public class GradleProjectsTest implements GradleIntegrationTest {
  @Rule
  public EdtAndroidProjectRule projectRule = onEdt(AndroidProjectRule.withAndroidModels());

  @Rule
  public Expect expect = Expect.createAndEnableStackTrace();

  @Rule
  public TestName testName = new TestName();

  @Test
  public void testIsGradleProjectWithRegularProject() {
    expect.that(AndroidProjectInfo.getInstance(projectRule.getProject()).requiresAndroidModel()).isFalse();
  }

  @Test
  public void testIsGradleProject() {
    projectRule.setupProjectFrom(new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));
    AndroidFacet facet = AndroidFacet.getInstance(gradleModule(projectRule.getProject(), ":"));

    expect.that(facet.getProperties().ALLOW_USER_CONFIGURATION).isFalse();
    expect.that(AndroidProjectInfo.getInstance(projectRule.getProject()).requiresAndroidModel()).isTrue();
  }

  @Test
  public void testCompositeGradlePaths() {
    prepareGradleProject(this, TestProjectPaths.COMPOSITE_BUILD, "project");
    openPreparedProject(this, "project", project -> {
      validateModuleGradlePath(project, ":");
      validateModuleGradlePath(project, ":app");
      validateModuleGradlePath(project, ":lib");
      validateModuleGradlePath(project, "TestCompositeLib1:app");
      validateModuleGradlePath(project, "TestCompositeLib1:lib");
      validateModuleGradlePath(project, "TestCompositeLib3:app");
      validateModuleGradlePath(project, "TestCompositeLib3:lib");
      validateModuleGradlePath(project, "composite2");
      validateModuleGradlePath(project, "composite4");
      return Unit.INSTANCE;
    });
  }

  private void validateModuleGradlePath(Project project, String s) {
    Module module = gradleModule(project, s);
    expect.that(module).isNotNull();
    //noinspection ConstantConditions
    if (module != null) {
      // Note: gradleModule is implemented via `getGradleModulePath` so it should fail on `isNotNull` rather than here.
      expect.that(GradleProjects.getGradleModulePath(module)).isEqualTo(s);
    }
  }

  @NotNull
  @Override
  public @SystemDependent String getBaseTestPath() {
    return projectRule.getFixture().getTempDirPath();
  }

  @NotNull
  @Override
  public @SystemIndependent String getTestDataDirectoryWorkspaceRelativePath() {
    return TestProjectPaths.TEST_DATA_PATH;
  }

  @NotNull
  @Override
  public Collection<File> getAdditionalRepos() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public String getName() {
    return testName.getMethodName();
  }

  @NotNull
  @Override
  public File resolveTestDataPath(@NotNull @SystemIndependent String testDataPath) {
    return GradleIntegrationTest.super.resolveTestDataPath(testDataPath);
  }
}
