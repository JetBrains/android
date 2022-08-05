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

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import java.io.File;

/**
 * Tests for making sure that {@link org.gradle.tooling.BuildAction} is run when passed to {@link GradleBuildInvoker}.
 */
public class BuildActionInvokerTest extends AndroidGradleTestCase {
  public void testBuildWithBuildAction() throws Exception {
    loadSimpleApplication();

    GradleBuildInvokerImpl invoker = (GradleBuildInvokerImpl)GradleBuildInvoker.getInstance(getProject());
    injectBuildOutputDumpingBuildViewManager(getProject(), getProject());
    Object model = invoker
      .executeTasks(
        new GradleBuildInvoker.Request.Builder(
          getProject(),
          new File(getProject().getBasePath()),
          ImmutableList.of("assembleDebug")
        )
          .setMode(BuildMode.ASSEMBLE)
          .build(),
        new TestBuildAction()
      ).get().getModel();

    assertEquals("test", model);
  }
}
