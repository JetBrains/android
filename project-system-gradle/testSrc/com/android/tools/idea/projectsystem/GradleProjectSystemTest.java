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
package com.android.tools.idea.projectsystem;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.Info;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.HeavyPlatformTestCase;

public class GradleProjectSystemTest extends HeavyPlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeComponents ideComponents = new IdeComponents(myProject);
    ideComponents.mockProjectService(GradleDependencyManager.class);
    ideComponents.mockProjectService(GradleBuildInvoker.class);

    Info gradleProjectInfo = ideComponents.mockProjectService(Info.class);
    when(gradleProjectInfo.isBuildWithGradle()).thenReturn(true);
  }

  public void testIsGradleProjectSystem() {
    assertThat(ProjectSystemUtil.getProjectSystem(getProject())).isInstanceOf(GradleProjectSystem.class);
  }

  public void testCompileProject() {
    ProjectSystemUtil.getProjectSystem(getProject()).getBuildManager().compileProject();
    verify(GradleBuildInvoker.getInstance(myProject)).compileJava(any());
  }

  public void testBuildConfigurationSourceProviderIsInvalidatedOnSync() {
    GradleProjectSystem projectSystem = (GradleProjectSystem)ProjectSystemUtil.getProjectSystem(myProject);
    BuildConfigurationSourceProvider provider1 = projectSystem.getBuildConfigurationSourceProvider();

    // Simulate sync modification by notifying the message bus.
    // This is a regression test for b/519813717 where Gradle files were missing after reopening.
    // Reopening triggers a sync, which must invalidate the BuildConfigurationSourceProvider cache.
    myProject.getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS);

    BuildConfigurationSourceProvider provider2 = projectSystem.getBuildConfigurationSourceProvider();

    assertNotSame(provider1, provider2);
  }
}
