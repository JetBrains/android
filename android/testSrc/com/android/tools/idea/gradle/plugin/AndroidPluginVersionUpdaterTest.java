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
package com.android.tools.idea.gradle.plugin;

import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.TextSearch;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidPluginVersionUpdater}.
 */
public class AndroidPluginVersionUpdaterTest extends IdeaTestCase {
  @Mock private GradleSyncState mySyncState;
  @Mock private GradleSyncInvoker mySyncInvoker;
  @Mock private TextSearch myTextSearch;

  private AndroidPluginVersionUpdater myVersionUpdater;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myVersionUpdater = new AndroidPluginVersionUpdater(getProject(), mySyncState, mySyncInvoker, myTextSearch);
  }

  public void testHandleUpdateResultWithPreviousSyncFailed() {
    // http://b/38487637
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(true);
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.pluginVersionUpdated();
    myVersionUpdater.handleUpdateResult(result, false);
    verify(mySyncState, never()).syncEnded();
  }

  public void testHandleUpdateResultWithPluginUpdateErrorAndInvalidatingSync() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.setPluginVersionUpdateError(new Throwable());

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(times(1));
    verifyProjectSyncRequested(never());
    verifyTextSearch(times(1));
  }

  public void testHandleUpdateResultWithGradleUpdateErrorAndInvalidatingSync() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.setGradleVersionUpdateError(new Throwable());

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(times(1));
    verifyProjectSyncRequested(never());
    verifyTextSearch(never());
  }

  public void testHandleUpdateResultWithPluginVersionUpdated() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.pluginVersionUpdated();

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(never());
    verifyProjectSyncRequested(times(1));
    verifyTextSearch(never());
  }

  public void testHandleUpdateResultWithGradleVersionUpdated() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.gradleVersionUpdated();

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(never());
    verifyProjectSyncRequested(times(1));
    verifyTextSearch(never());
  }

  public void testHandleUpdateResultWithNoVersionsUpdatedAndNoErrors() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(never());
    verifyProjectSyncRequested(never());
    verifyTextSearch(never());
  }

  private void verifyLastSyncInvalidated(@NotNull VerificationMode verificationMode) {
    verify(mySyncState, verificationMode).invalidateLastSync(any());
  }

  private void verifyProjectSyncRequested(@NotNull VerificationMode verificationMode) {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.cleanProject = true;

    verify(mySyncState, verificationMode).syncEnded();
    verify(mySyncInvoker, verificationMode).requestProjectSync(myProject, request, null);
  }

  private void verifyTextSearch(@NotNull VerificationMode verificationMode) {
    verify(myTextSearch, verificationMode).execute();
  }
}