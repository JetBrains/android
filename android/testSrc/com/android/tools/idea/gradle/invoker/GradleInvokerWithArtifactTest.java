/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;

import java.util.List;

public class GradleInvokerWithArtifactTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = true;
  }

  public void testInvokeUnitTest() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("AndroidJunitPatcherWithTestArtifactTest.test temporarily disabled");
      return;
    }

    loadProject("projects/sync/multiproject", false);
    Module module1 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("module1");

    List<String> compileTests =
      GradleInvoker.findTasksToExecute(new Module[]{module1}, BuildMode.COMPILE_JAVA, GradleInvoker.TestCompileType.JAVA_TESTS);
    assertContainsElements(compileTests, ":module1:compileDebugUnitTestSources");
    assertDoesntContain(compileTests, ":module1:compileDebugAndroidTestSources");

    List<String> compileAndroidTests =
      GradleInvoker.findTasksToExecute(new Module[]{module1}, BuildMode.COMPILE_JAVA, GradleInvoker.TestCompileType.ANDROID_TESTS);
    assertContainsElements(compileAndroidTests, ":module1:compileDebugAndroidTestSources");
    assertDoesntContain(compileAndroidTests, ":module1:compileDebugUnitTestSources");
  }
}
