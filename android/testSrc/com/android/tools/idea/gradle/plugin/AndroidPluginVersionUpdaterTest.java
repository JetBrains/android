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

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.TextSearch;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

/**
 * Tests for {@link AndroidPluginVersionUpdater}.
 */
public class AndroidPluginVersionUpdaterTest extends PlatformTestCase {
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

  @Override
  protected void tearDown() throws Exception {
    myVersionUpdater = null;
    super.tearDown();
  }

  public void testHandleUpdateResultWithPreviousSyncFailed() {
    // http://b/38487637
    when(mySyncState.lastSyncFailed()).thenReturn(true);
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.pluginVersionUpdated();
    myVersionUpdater.handleUpdateResult(result);
    verify(mySyncState, never()).syncSucceeded();
  }

  public void testHandleUpdateResultWithPluginUpdateErrorAndInvalidatingSync() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.setPluginVersionUpdateError(new Throwable());

    myVersionUpdater.handleUpdateResult(result);

    verifyLastSyncFailed(times(1));
    verifyProjectSyncRequested(never(), TRIGGER_TEST_REQUESTED);
    verifyTextSearch(times(1));
  }

  public void testHandleUpdateResultWithGradleUpdateErrorAndInvalidatingSync() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.setGradleVersionUpdateError(new Throwable());

    myVersionUpdater.handleUpdateResult(result);

    verifyLastSyncFailed(times(1));
    verifyProjectSyncRequested(never(), TRIGGER_TEST_REQUESTED);
    verifyTextSearch(never());
  }

  public void testHandleUpdateResultWithPluginVersionUpdated() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.pluginVersionUpdated();

    myVersionUpdater.handleUpdateResult(result);

    verifyLastSyncFailed(never());
    verifyProjectSyncRequested(times(1), TRIGGER_AGP_VERSION_UPDATED);
    verifyTextSearch(never());
  }

  public void testHandleUpdateResultWithGradleVersionUpdated() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.gradleVersionUpdated();

    myVersionUpdater.handleUpdateResult(result);

    verifyLastSyncFailed(never());
    verifyProjectSyncRequested(times(1), TRIGGER_AGP_VERSION_UPDATED);
    verifyTextSearch(never());
  }

  public void testHandleUpdateResultWithNoVersionsUpdatedAndNoErrors() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    myVersionUpdater.handleUpdateResult(result);

    verifyLastSyncFailed(never());
    verifyProjectSyncRequested(never(), TRIGGER_TEST_REQUESTED);
    verifyTextSearch(never());
  }

  private void verifyLastSyncFailed(@NotNull VerificationMode verificationMode) {
    verify(mySyncState, verificationMode).syncFailed(any(), any(), any());
  }

  private void verifyProjectSyncRequested(@NotNull VerificationMode verificationMode, @NotNull GradleSyncStats.Trigger trigger) {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(trigger);
    request.cleanProject = true;

    verify(mySyncState, verificationMode).syncSucceeded();
    verify(mySyncInvoker, verificationMode).requestProjectSync(myProject, request);
  }

  private void verifyTextSearch(@NotNull VerificationMode verificationMode) {
    verify(myTextSearch, verificationMode).execute();
  }
}