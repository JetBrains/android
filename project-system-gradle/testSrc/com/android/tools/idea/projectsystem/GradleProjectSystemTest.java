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

import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystemSyncManager;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class GradleProjectSystemTest extends IdeaTestCase {
  private IdeComponents myIdeComponents;
  private GradleProjectInfo myGradleProjectInfo;
  private ProjectSystemSyncManager mySyncManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(myProject);

    myIdeComponents.mockProjectService(GradleDependencyManager.class);
    myIdeComponents.mockProjectService(GradleProjectBuilder.class);
    myGradleProjectInfo = myIdeComponents.mockProjectService(GradleProjectInfo.class);
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    mySyncManager = new GradleProjectSystemSyncManager(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    } finally {
      super.tearDown();
    }
  }

  public void testIsGradleProjectSystem() {
    assertThat(ProjectSystemUtil.getProjectSystem(getProject())).isInstanceOf(GradleProjectSystem.class);
  }

  public void testSyncProjectWithUninitializedProject() {
    Project project = getProject();
    StartupManagerEx startupManager = new StartupManagerImpl(project) {
      @Override
      public boolean startupActivityPassed() {
        return false; // this will make Project.isInitialized return false;
      }

      @Override
      public void runWhenProjectIsInitialized(@NotNull Runnable action) {
        action.run();
      }
    };
    myIdeComponents.replaceService(project, StartupManager.class, startupManager);
    // http://b/62543184
    when(myGradleProjectInfo.isImportedProject()).thenReturn(true);
    GradleSyncInvoker mySyncInvoker = myIdeComponents.mockService(GradleSyncInvoker.class);

    ProjectSystemUtil.getProjectSystem(project).getSyncManager().syncProject(SyncReason.PROJECT_LOADED, true);
    Mockito.verify(mySyncInvoker, never()).requestProjectSync(same(project), any(), any());
  }

  public void testBuildProject() {
    ProjectSystemUtil.getProjectSystem(getProject()).buildProject();
    verify(GradleProjectBuilder.getInstance(myProject)).compileJava();
  }

  public void testGetLastSyncResult_unknownIfNeverSynced() {
    assertThat(mySyncManager.getLastSyncResult()).isSameAs(SyncResult.UNKNOWN);
  }

  public void testGetLastSyncResult_sameAsSyncResult() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    myProject.getMessageBus().connect(myProject).subscribe(PROJECT_SYSTEM_SYNC_TOPIC, result -> latch.countDown());

    // Emulate the completion of a successful sync.
    myProject.getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS);

    latch.await();
    assertThat(mySyncManager.getLastSyncResult()).isSameAs(SyncResult.SUCCESS);
  }
}
