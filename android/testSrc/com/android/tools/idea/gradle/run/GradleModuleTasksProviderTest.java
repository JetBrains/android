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
package com.android.tools.idea.gradle.run;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.android.tools.idea.testing.TestProjectPaths.JAVA_LIB;

public class GradleModuleTasksProviderTest extends AndroidGradleTestCase {

  public void testUnitTestsInDependenciesAreNotCompiled() throws Exception {
    loadProject(JAVA_LIB);
    Module app = ModuleManager.getInstance(getProject()).findModuleByName("app");
    GradleModuleTasksProvider gradleModuleTasksProvider = new GradleModuleTasksProvider(new Module[]{app});
    ListMultimap<Path, String> tasksMultiMap = gradleModuleTasksProvider.getUnitTestTasks(BuildMode.COMPILE_JAVA);
    List<String> tasks = tasksMultiMap.get(Paths.get(ExternalSystemApiUtil.getExternalRootProjectPath(app)));
    assertDoesntContain(tasks, ":lib:testClasses");
    assertContainsElements(tasks, ":app:compileDebugUnitTestSources", ":app:compileDebugSources", ":lib:compileJava");
  }
}
