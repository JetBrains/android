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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AndroidPluginVersionUpdater}.
 */
public class AndroidPluginVersionUpdaterTest {
  private Project myProject;
  private GradleSyncState mySyncState;
  private GradleSyncInvoker mySyncInvoker;
  private TextSearch myTextSearch;

  private AndroidPluginVersionUpdater myVersionUpdater;

  @Before
  public void setUp() {
    myProject = mock(Project.class);
    mySyncState = mock(GradleSyncState.class);
    mySyncInvoker = mock(GradleSyncInvoker.class);
    myTextSearch = mock(TextSearch.class);

    myVersionUpdater = new AndroidPluginVersionUpdater(myProject, mySyncState, mySyncInvoker, myTextSearch);
  }

  @Test
  public void handleUpdateResultWithPluginUpdateErrorAndInvalidatingSync() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.setPluginVersionUpdateError(new Throwable());

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(times(1));
    verifyProjectSyncRequested(never());
    verifyTextSearch(times(1));
  }

  @Test
  public void handleUpdateResultWithGradleUpdateErrorAndInvalidatingSync() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.setGradleVersionUpdateError(new Throwable());

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(times(1));
    verifyProjectSyncRequested(never());
    verifyTextSearch(never());
  }

  @Test
  public void handleUpdateResultWithPluginVersionUpdated() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.pluginVersionUpdated();

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(never());
    verifyProjectSyncRequested(times(1));
    verifyTextSearch(never());
  }

  @Test
  public void handleUpdateResultWithGradleVersionUpdated() {
    AndroidPluginVersionUpdater.UpdateResult result = new AndroidPluginVersionUpdater.UpdateResult();
    result.gradleVersionUpdated();

    myVersionUpdater.handleUpdateResult(result, true);

    verifyLastSyncInvalidated(never());
    verifyProjectSyncRequested(times(1));
    verifyTextSearch(never());
  }

  @Test
  public void handleUpdateResultWithNoVersionsUpdatedAndNoErrors() {
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
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request().setCleanProject(true);
    verify(mySyncState, verificationMode).syncEnded();
    verify(mySyncInvoker, verificationMode).requestProjectSync(myProject, request, null);
  }

  private void verifyTextSearch(@NotNull VerificationMode verificationMode) {
    verify(myTextSearch, verificationMode).execute();
  }
}