/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.project.idea;

import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.validation.common.CommonModuleValidator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.FAILED_TO_SET_UP_SDK;
import static com.android.tools.idea.gradle.project.sync.setup.project.idea.PostSyncProjectSetup.Request.DEFAULT_REQUEST;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PostSyncProjectSetup}.
 */
public class PostSyncProjectSetupTest extends IdeaTestCase {
  @Mock private AndroidSdks myAndroidSdks;
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock private GradleSyncState mySyncState;
  @Mock private GradleSyncSummary mySyncSummary;
  @Mock private SyncMessages mySyncMessages;
  @Mock private VersionCompatibilityChecker myVersionCompatibilityChecker;
  @Mock private GradleProjectBuilder myProjectBuilder;
  @Mock private CommonModuleValidator.Factory myModuleValidatorFactory;
  @Mock private CommonModuleValidator myModuleValidator;

  private PostSyncProjectSetup mySetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    when(mySyncState.getSummary()).thenReturn(mySyncSummary);
    when(myModuleValidatorFactory.create(project)).thenReturn(myModuleValidator);

    mySetup = new PostSyncProjectSetup(project, myAndroidSdks, mySyncInvoker, mySyncState, mySyncMessages, myVersionCompatibilityChecker,
                                       myProjectBuilder, myModuleValidatorFactory);
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncWithCachedModelsFinishedWithSyncIssues() {
    simulateSyncFinishedWithIssues();

    long lastSyncTimestamp = 2L;
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();
    // @formatter:off
    request.setUsingCachedGradleModels(true)
           .setLastSyncTimestamp(lastSyncTimestamp);
    // @formatter:on

    mySetup.setUpProject(request);

    verify(mySyncState, times(1)).syncSkipped(lastSyncTimestamp);
    verify(mySyncInvoker, times(1)).requestProjectSyncAndSourceGeneration(getProject(), null);
  }

  // See: https://code.google.com/p/android/issues/detail?id=225938
  public void testSyncFinishedWithSyncIssues() {
    simulateSyncFinishedWithIssues();

    // Avoid adding a hyperlink to install any missing platforms.
    when(mySyncMessages.getMessageCount(FAILED_TO_SET_UP_SDK)).thenReturn(0);

    // Avoid check SDK tools version
    PostSyncProjectSetup.ourNewSdkVersionToolsInfoAlreadyShown = true;

    mySetup.setUpProject(DEFAULT_REQUEST);
  }

  private void simulateSyncFinishedWithIssues() {
    when(mySyncState.lastSyncFailed()).thenReturn(false);
    when(mySyncSummary.hasSyncErrors()).thenReturn(true);
    PostSyncProjectSetup.Request request = new PostSyncProjectSetup.Request();

    // @formatter:off
    request.setGenerateSourcesAfterSync(true)
           .setCleanProjectAfterSync(true);
    // @formatter:on

    mySetup.setUpProject(request);

    verify(mySyncState, times(1)).syncEnded();

    // Source generation should not be invoked if sync failed.
    verify(myProjectBuilder, never()).generateSourcesOnly(true);
  }
}