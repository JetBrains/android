/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.google.common.truth.Expect;
import com.intellij.testFramework.RunsInEdt;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link GradleProjects}.
 */
@RunWith(JUnit4.class)
@RunsInEdt
public class GradleProjectsTest {
  @Rule
  public EdtAndroidProjectRule projectRule = onEdt(AndroidProjectRule.withAndroidModels());

  @Rule
  public Expect expect = Expect.createAndEnableStackTrace();

  @Test
  public void testIsGradleProjectWithRegularProject() {
    expect.that(ProjectSystemUtil.requiresAndroidModel(projectRule.getProject())).isFalse();
  }

  @Test
  public void testIsGradleProject() {
    projectRule.setupProjectFrom(new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));
    AndroidFacet facet = AndroidFacet.getInstance(gradleModule(projectRule.getProject(), ":"));

    expect.that(facet.getProperties().ALLOW_USER_CONFIGURATION).isFalse();
    expect.that(ProjectSystemUtil.requiresAndroidModel(projectRule.getProject())).isTrue();
  }
}
