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
package com.android.tools.idea.gradle.project.sync.idea;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link IdeaSyncPopulateProjectTask}.
 */
public class IdeaSyncPopulateProjectTaskTest extends IdeaTestCase {
  @Mock private PostSyncProjectSetup myProjectSetup;
  @Mock private GradleSyncState mySyncState;
  @Mock private ProjectDataManager myDataManager;

  private Collection<DataNode<ModuleData>> myActiveModules;
  private IdeaSyncPopulateProjectTask myTask;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myActiveModules = new ArrayList<>();
    myActiveModules.add(new DataNode<>(MODULE, mock(ModuleData.class), null));

    myTask = new IdeaSyncPopulateProjectTask(getProject(), myProjectSetup, mySyncState, myDataManager);
  }

  // See https://code.google.com/p/android/issues/detail?id=268806
  public void testDoSelectiveImportWithErrorAndCachedModels() {
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request().setUsingCachedGradleModels(true);

    Project project = getProject();
    // Simulate an error when loading models from disk cache
    doThrow(new RuntimeException("test")).when(myDataManager).importData(myActiveModules, project, true);

    myTask.doSelectiveImport(myActiveModules, project, request);

    // Verify we start a new sync if loading models from the cache failed.
    verify(myProjectSetup).onCachedModelsSetupFailure(request);
    verify(mySyncState, never()).syncFailed(any());
  }

  // See https://code.google.com/p/android/issues/detail?id=268806
  public void testDoSelectiveImportWithErrorAndNonCachedModels() {
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request().setUsingCachedGradleModels(false);

    Project project = getProject();
    // Simulate an error when loading models from disk cache
    String error = "test";
    doThrow(new RuntimeException(error)).when(myDataManager).importData(myActiveModules, project, true);

    myTask.doSelectiveImport(myActiveModules, project, request);

    // Since a real sync failed, just notify the failure.
    verify(mySyncState).syncFailed(error);
    verify(myProjectSetup, never()).onCachedModelsSetupFailure(request);
  }
}