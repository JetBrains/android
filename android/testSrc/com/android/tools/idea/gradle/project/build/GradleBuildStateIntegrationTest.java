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

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.project.build.BuildStatus.SUCCESS;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleBuildState}.
 */
public class GradleBuildStateIntegrationTest extends AndroidGradleTestCase {

  public void testEventsReceived() throws Exception {
    loadSimpleApplication();

    BuildContext[] contexts = new BuildContext[2];
    Ref<BuildStatus> statusRef = new Ref<>();

    Project project = getProject();
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

    invokeGradle(project, GradleBuildInvoker::generateSources);

    GradleBuildState buildState = GradleBuildState.getInstance(project);
    assertFalse(buildState.isBuildInProgress());
    assertNull(buildState.getCurrentContext());

    BuildContext context1 = contexts[0];
    assertNotNull(context1);

    assertSame(SOURCE_GEN, context1.getBuildMode());
    assertSame(project, context1.getProject());
    assertThat(context1.getGradleTasks()).contains(":app:generateDebugSources");

    assertSame(context1, contexts[1]); // initial context and final context should be the same,

    BuildSummary summary = buildState.getSummary();
    assertNotNull(summary);
    assertSame(context1, summary.getContext());
    assertSame(SUCCESS, summary.getStatus());
  }
}