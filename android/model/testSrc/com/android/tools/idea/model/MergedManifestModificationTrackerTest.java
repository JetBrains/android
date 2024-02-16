/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model;

import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.mockito.AdditionalAnswers;
import org.mockito.stubbing.Answer;

public class MergedManifestModificationTrackerTest extends HeavyPlatformTestCase {
  public void testWhenProjectSync() {
    // Load service on demand
    ProjectSyncModificationTracker projectSyncTracker = ProjectSyncModificationTracker.getInstance(myProject);
    long baseProjectSyncTrackerCount = projectSyncTracker.getModificationCount();

    MergedManifestModificationTracker mergedManifestTracker = MergedManifestModificationTracker.getInstance(myModule);
    long baseMergedManifestTrackerCount = mergedManifestTracker.getModificationCount();

    projectSync();

    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(baseProjectSyncTrackerCount + 1);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(baseMergedManifestTrackerCount + 1);
  }

  public void testWhenManifestChanged() throws Exception {
    PathString stringPath = mock(PathString.class);
    AndroidFacetConfiguration androidFacetConfiguration = mock(AndroidFacetConfiguration.class);
    MergedManifestModificationListener modificationListener = new MergedManifestModificationListener(myProject);
    AndroidFacet androidFacet = new AndroidFacet(myModule, "App", androidFacetConfiguration);

    // Load service on demand
    MergedManifestModificationTracker mergedManifestTracker = MergedManifestModificationTracker.getInstance(myModule);
    long baseMergedManifestTrackerCount = mergedManifestTracker.getModificationCount();

    updateManifestAndWaitWithTimeout(modificationListener, stringPath, androidFacet);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(baseMergedManifestTrackerCount + 1);
  }

  public void testWhenManifestChangedActively() throws Exception {
    // Introduce "dependencyModule": "myModule" depends on "dependencyModule".
    Path path = createFolderInProjectRoot(myProject, "app").toNioPath();
    Module dependencyModule = createModuleAt("app", myProject, JavaModuleType.getModuleType(), path);
    ModuleRootModificationUtil.addDependency(myModule, dependencyModule);

    AndroidModuleSystem moduleSystemMock = spy(AndroidModuleSystem.class);

    doAnswer(AdditionalAnswers.answersWithDelay(1000, (Answer<List<Module>>)invocation -> Collections.singletonList(myModule)))
      .when(moduleSystemMock).getDirectResourceModuleDependents();

    AndroidProjectSystem projectSystemMock = mock(AndroidProjectSystem.class);
    when(projectSystemMock.getModuleSystem(dependencyModule)).thenReturn(moduleSystemMock);

    ProjectSystemService projectSystemService = mock(ProjectSystemService.class);
    when(projectSystemService.getProjectSystem()).thenReturn(projectSystemMock);
    ServiceContainerUtil.replaceService(dependencyModule.getProject(),
                                        ProjectSystemService.class,
                                        projectSystemService,
                                        getTestRootDisposable()
    );

    AndroidFacetConfiguration androidFacetConfiguration = mock(AndroidFacetConfiguration.class);
    MergedManifestModificationListener modificationListener = new MergedManifestModificationListener(myProject);
    AndroidFacet androidFacet = new AndroidFacet(dependencyModule, "App", androidFacetConfiguration);
    PathString stringPath = mock(PathString.class);

    MergedManifestModificationTracker myModuleTracker = MergedManifestModificationTracker.getInstance(myModule);
    MergedManifestModificationTracker dependencyModuleTracker = MergedManifestModificationTracker.getInstance(dependencyModule);
    long myModuleTrackerBaseCount = myModuleTracker.getModificationCount();
    long dependencyModuleTrackerBaseCount = dependencyModuleTracker.getModificationCount();

    for (int i = 0; i < 20; i++) {
      // Since "myModule" depends on "dependencyModule", manifest file updates in "dependencyModule" will increment
      // the tracker of "myModule". However, considering our fake long running operation when getting transitive
      // dependents, the "in progress" task to update modification trackers is likely to be cancelled if the same
      // task comes. Here, the task is always to update the tracker of "dependencyModule" and "myModule".
      updateManifest(modificationListener, stringPath, androidFacet);
      Thread.sleep(50);
    }

    modificationListener.waitAllUpdatesCompletedWithTimeout(2, TimeUnit.SECONDS);

    assertThat(dependencyModuleTracker.getModificationCount()).isEqualTo(dependencyModuleTrackerBaseCount + 20);
    assertThat(myModuleTracker.getModificationCount()).isEqualTo(myModuleTrackerBaseCount + 1);
  }

  public void testCombinationCases() throws Exception {
    PathString stringPath = mock(PathString.class);
    AndroidFacetConfiguration androidFacetConfiguration = mock(AndroidFacetConfiguration.class);
    MergedManifestModificationListener modificationListener = new MergedManifestModificationListener(myProject);

    AndroidFacet androidFacet = new AndroidFacet(myModule, "App", androidFacetConfiguration);

    // Load service on demand
    ProjectSyncModificationTracker projectSyncTracker = ProjectSyncModificationTracker.getInstance(myProject);
    long baseProjectSyncTrackerCount = projectSyncTracker.getModificationCount();

    MergedManifestModificationTracker mergedManifestTracker = MergedManifestModificationTracker.getInstance(myModule);
    long baseMergedManifestTrackerCount = mergedManifestTracker.getModificationCount();

    // update manifest
    updateManifestAndWaitWithTimeout(modificationListener, stringPath, androidFacet);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(baseMergedManifestTrackerCount + 1);

    // sync
    projectSync();
    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(baseProjectSyncTrackerCount + 1);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(baseMergedManifestTrackerCount + 2);

    // update manifest
    updateManifestAndWaitWithTimeout(modificationListener, stringPath, androidFacet);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(baseMergedManifestTrackerCount + 3);

    // sync
    projectSync();
    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(baseProjectSyncTrackerCount + 2);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(baseMergedManifestTrackerCount + 4);

    // sync
    projectSync();
    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(baseProjectSyncTrackerCount + 3);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(baseMergedManifestTrackerCount + 5);
  }


  private void projectSync() {
    myProject.getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS);
  }

  private static void updateManifest(MergedManifestModificationListener modificationListener,
                                     PathString stringPath,
                                     AndroidFacet androidFacet) {
    modificationListener.fileChanged(stringPath, androidFacet);
  }

  private static void updateManifestAndWaitWithTimeout(@NotNull MergedManifestModificationListener modificationListener,
                                                       PathString stringPath,
                                                       AndroidFacet androidFacet) throws Exception {
    modificationListener.fileChanged(stringPath, androidFacet);
    modificationListener.waitAllUpdatesCompletedWithTimeout(3, TimeUnit.SECONDS);
  }
}