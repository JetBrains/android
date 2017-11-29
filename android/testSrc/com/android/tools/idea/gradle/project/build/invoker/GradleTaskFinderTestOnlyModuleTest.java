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

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;

import java.nio.file.Paths;
import java.util.List;

import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.android.tools.idea.testing.TestProjectPaths.TEST_ONLY_MODULE;
import static com.google.common.truth.Truth.assertThat;

public class GradleTaskFinderTestOnlyModuleTest extends AndroidGradleTestCase {
  private GradleTaskFinder myTaskFinder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject(TEST_ONLY_MODULE);
    myTaskFinder = GradleTaskFinder.getInstance();
  }

  public void testEmpty() {
    // placeholder for disabled tests below.
  }

  // failing after 2017.3 merge
  public void /*test*/AssembleTasksCorrect() throws Exception {
    Module[] modules = new Module[] { getModule("test") };
    List<String> tasks = myTaskFinder.findTasksToExecute(modules, ASSEMBLE, TestCompileType.ALL).get(Paths.get("project_path"));
    assertThat(tasks).containsExactly(":app:assembleDebug", ":test:assembleDebug");
  }

  // failing after 2017.3 merge
  public void /*test*/AssembleTasksNotDuplicated() throws Exception {
    Module[] modules = new Module[] { getModule("test"), getModule("app") };
    List<String> tasks = myTaskFinder.findTasksToExecute(modules, REBUILD, TestCompileType.ALL).get(Paths.get("project_path"));
    assertThat(tasks).containsExactly("clean", ":app:assembleDebug", ":test:assembleDebug");
  }
}
