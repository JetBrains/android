/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker;

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.injectBuildOutputDumpingBuildViewManager;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for making sure that {@link org.gradle.tooling.BuildAction} is run when passed to {@link GradleBuildInvoker}.
 */
public class BuildActionInvokerTest {
  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  
  @Test
  public void testBuildWithBuildAction() throws Exception {
    projectRule.loadProject(SIMPLE_APPLICATION);
    Project project = projectRule.getProject();
    
    GradleBuildInvokerImpl invoker = (GradleBuildInvokerImpl)GradleBuildInvoker.getInstance(project);
    injectBuildOutputDumpingBuildViewManager(project, projectRule.getFixture().getTestRootDisposable());
    Object model = invoker
      .executeTasks(
        new GradleBuildInvoker.Request.Builder(
          project,
          new File(project.getBasePath()),
          ImmutableList.of("assembleDebug"),
          null
        )
          .setMode(BuildMode.ASSEMBLE)
          .build(),
        new TestBuildAction()
      ).get().getModel();

    assertThat(model).isEqualTo("test");
  }
}
