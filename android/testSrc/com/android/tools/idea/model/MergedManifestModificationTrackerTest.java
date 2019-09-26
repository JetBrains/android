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
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.ide.common.util.PathString;
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;


public class MergedManifestModificationTrackerTest extends PlatformTestCase {
  public void testWhenProjectSync() {
    // Load service on demand
    ProjectSyncModificationTracker projectSyncTracker = ProjectSyncModificationTracker.getInstance(myProject);
    MergedManifestModificationTracker mergedManifestTracker = MergedManifestModificationTracker.getInstance(myModule);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(0);

    projectSync();

    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(1);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(1);
  }

  public void testWhenManifestChanged() {
    PathString stringPath = mock(PathString.class);
    AndroidFacetConfiguration androidFacetConfiguration = mock(AndroidFacetConfiguration.class);
    MergedManifestRefreshListener refreshListener = new MergedManifestRefreshListener(myProject);
    AndroidFacet androidFacet = new AndroidFacet(myModule, "App", androidFacetConfiguration);

    // Load service on demand
    MergedManifestModificationTracker mergedManifestTracker = MergedManifestModificationTracker.getInstance(myModule);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(0);

    updateManifest(refreshListener, stringPath, androidFacet);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(1);
  }

  public void testCombinationCases() {
    PathString stringPath = mock(PathString.class);
    AndroidFacetConfiguration androidFacetConfiguration = mock(AndroidFacetConfiguration.class);
    MergedManifestRefreshListener refreshListener = new MergedManifestRefreshListener(myProject);

    AndroidFacet androidFacet = new AndroidFacet(myModule, "App", androidFacetConfiguration);

    // Load service on demand
    ProjectSyncModificationTracker projectSyncTracker = ProjectSyncModificationTracker.getInstance(myProject);
    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(0);

    MergedManifestModificationTracker mergedManifestTracker = MergedManifestModificationTracker.getInstance(myModule);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(0);

    // update manifest
    updateManifest(refreshListener, stringPath, androidFacet);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(1);

    // sync
    projectSync();
    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(1);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(2);

    // update manifest
    updateManifest(refreshListener, stringPath, androidFacet);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(3);

    // sync
    projectSync();
    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(2);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(4);

    // sync
    projectSync();
    assertThat(projectSyncTracker.getModificationCount()).isEqualTo(3);
    assertThat(mergedManifestTracker.getModificationCount()).isEqualTo(5);
  }


  private void projectSync() {
    myProject.getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS);
  }

  private static void updateManifest(MergedManifestRefreshListener refreshListener, PathString stringPath, AndroidFacet androidFacet) {
    refreshListener.fileChanged(stringPath, androidFacet);
  }
}
