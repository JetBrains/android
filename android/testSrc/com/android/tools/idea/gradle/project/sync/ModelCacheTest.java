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

import com.android.tools.idea.gradle.project.sync.idea.data.DataNodeCaches;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.Collections;

import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModelCacheTest extends AndroidGradleTestCase {
  private IdeComponents myIdeComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    myIdeComponents = new IdeComponents(project);

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  // Verifies that if syncing using cached model, and if the cached model is missing data, we fall back to a full Gradle sync.
  // See: https://code.google.com/p/android/issues/detail?id=160899
  public void testWithCacheMissingModules() throws Exception {
    loadSimpleApplication();

    // Simulate data node cache is missing modules.
    //noinspection unchecked
    DataNode<ProjectData> cache = mock(DataNode.class);
    DataNodeCaches dataNodeCaches = myIdeComponents.mockProjectService(DataNodeCaches.class);
    when(dataNodeCaches.getCachedProjectData()).thenReturn(cache);
    when(dataNodeCaches.isCacheMissingModels(cache)).thenReturn(true);

    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.generateSourcesOnSuccess = false;
    request.useCachedGradleModels = true;

    // Sync again, and a full sync should occur, since the cache is missing modules.
    // 'waitForGradleProjectSyncToFinish' will never finish and test will time out and fail if the IDE never gets notified that the sync
    // finished.
    requestSyncAndWait(request);
  }
}
