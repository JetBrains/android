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
package com.android.tools.idea.gradle.project.sync;

import static com.android.tools.idea.gradle.project.sync.NewGradleSyncIntegrationTest.verifyTaskViewPopulated;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;

/**
 * Integration test for Gradle Sync with old versions of Android plugin and the new Sync infrastructure.
 */
public class NewGradleSyncWithOlderPluginTest extends GradleSyncWithOlderPluginTest {
  @Override
  protected boolean useNewSyncInfrastructure() {
    return true;
  }

  @Override
  public void testWithInterAndroidModuleDependencies() throws Exception {
    notifySkippedTest();
  }

  @Override
  public void testWithInterJavaModuleDependencies() throws Exception {
    notifySkippedTest();
  }

  @Override
  public void testJavaLibraryDependenciesFromJavaModule() throws Exception {
    notifySkippedTest();
  }

  @Override
  public void testLocalJarDependenciesFromAndroidModule() throws Exception {
    notifySkippedTest();
  }

  @Override
  public void testJavaLibraryDependenciesFromAndroidModule() throws Exception {
    notifySkippedTest();
  }

  @Override
  public void testAndroidModuleDependenciesFromAndroidModule() throws Exception {
    notifySkippedTest();
  }

  @Override
  public void testAndroidLibraryDependenciesFromAndroidModule() throws Exception {
    notifySkippedTest();
  }

  @Override
  public void testWithPluginOneDotFive() throws Exception {
    notifySkippedTest();
  }

  private void notifySkippedTest() {
    System.out.println(String.format("Skipped '%1$s#%2$s'. See http://b/67390792 .", getClass().getSimpleName(), getName()));
  }

  // See http://b/67390792 - NPE with Gradle 2.2.1.
  public void /*test*/TaskViewPopulated() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    verifyTaskViewPopulated(getProject());
  }
}
