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
package com.android.tools.idea.gradle.project.build;

import static com.android.tools.idea.gradle.project.build.BuildStatus.SUCCESS;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link GradleBuildState}.
 */
public class GradleBuildStateIntegrationTest {
  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Test
  public void testEventsReceived() throws Exception {
    projectRule.loadProject(SIMPLE_APPLICATION);

    BuildContext[] contexts = new BuildContext[2];
    Ref<BuildStatus> statusRef = new Ref<>();

    Project project = projectRule.getProject();
    GradleBuildState.subscribe(project, new GradleBuildListener.Adapter() {
      @Override
      public void buildStarted(@NotNull BuildContext context) {
        contexts[0] = context;
      }

      @Override
      public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
        statusRef.set(status);
        contexts[1] = context;
      }
    });

    projectRule.generateSources();

    GradleBuildState buildState = GradleBuildState.getInstance(project);
    assertThat(buildState.isBuildInProgress()).isFalse();
    assertThat(buildState.getRunningBuildContext()).isNull();

    BuildContext context1 = contexts[0];
    assertThat(context1).isNotNull();

    assertThat(context1.getBuildMode()).isSameAs(SOURCE_GEN);
    assertThat(context1.getProject()).isSameAs(project);
    assertThat(context1.getGradleTasks()).contains(":app:generateDebugSources");

    assertThat(contexts[1]).isSameAs(context1); // initial context and final context should be the same,

    BuildSummary summary = buildState.getLastFinishedBuildSummary();
    assertThat(summary).isNotNull();
    assertThat(summary.getContext()).isSameAs(context1);
    assertThat(summary.getStatus()).isSameAs(SUCCESS);
  }
}