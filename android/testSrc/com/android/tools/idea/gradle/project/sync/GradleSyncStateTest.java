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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.messages.MessageBus;

import static com.android.tools.idea.gradle.project.sync.GradleSyncState.GRADLE_SYNC_TOPIC;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GradleSyncState}.
 */
public class GradleSyncStateTest extends IdeaTestCase {
  private GradleSyncListener mySyncListener;
  private GradleSyncState.StateChangeNotification myChangeNotification;
  private GradleSyncSummary mySummary;

  private GradleSyncState mySyncState;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    mySyncListener = mock(GradleSyncListener.class);
    myChangeNotification = mock(GradleSyncState.StateChangeNotification.class);
    mySummary = mock(GradleSyncSummary.class);

    MessageBus messageBus = mock(MessageBus.class);

    mySyncState = new GradleSyncState(myProject, GradleProjectInfo.getInstance(getProject()), messageBus, myChangeNotification, mySummary);

    when(messageBus.syncPublisher(GRADLE_SYNC_TOPIC)).thenReturn(mySyncListener);
  }

  public void testSyncStartedWithoutUserNotification() {
    assertFalse(mySyncState.isSyncInProgress());

    boolean syncStarted = mySyncState.syncStarted(false /* no user notification */);
    assertTrue(syncStarted);
    assertTrue(mySyncState.isSyncInProgress());

    // Trying to start a sync again should not work.
    assertFalse(mySyncState.syncStarted(false));

    verify(myChangeNotification, never()).notifyStateChanged();
    verify(mySummary, times(1)).reset(); // 'reset' should have been called only once.
    verify(mySyncListener, times(1)).syncStarted(myProject);
  }

  public void testSyncStartedWithUserNotification() {
    assertFalse(mySyncState.isSyncInProgress());

    boolean syncStarted = mySyncState.syncStarted(true /* user notification */);
    assertTrue(syncStarted);
    assertTrue(mySyncState.isSyncInProgress());

    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(mySummary, times(1)).reset(); // 'reset' should have been called only once.
    verify(mySyncListener, times(1)).syncStarted(myProject);
  }

  public void testSyncSkipped() {
    long timestamp = -1231231231299L; // Some random number

    mySyncState.syncSkipped(timestamp);

    verify(myChangeNotification, never()).notifyStateChanged();
    verify(mySummary, times(1)).setSyncTimestamp(timestamp);
    verify(mySyncListener, times(1)).syncSkipped(myProject);
  }

  public void testSyncSkippedAfterSyncStarted() {
    long timestamp = -1231231231299L; // Some random number

    mySyncState.syncStarted(false);
    mySyncState.syncSkipped(timestamp);
    assertFalse(mySyncState.isSyncInProgress());
  }

  public void testSyncFailed() {
    String msg = "Something went wrong";

    mySyncState.syncFailed(msg);

    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(mySummary, times(1)).setSyncTimestamp(anyLong());
    verify(mySummary, times(1)).setSyncErrorsFound(true);
    verify(mySyncListener, times(1)).syncFailed(myProject, msg);
  }

  public void testSyncEnded() {
    mySyncState.syncEnded();

    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(mySummary, times(1)).setSyncTimestamp(anyLong());
    verify(mySyncListener, times(1)).syncSucceeded(myProject);
  }

  public void testSetupStarted() {
    mySyncState.setupStarted();

    verify(mySyncListener, times(1)).setupStarted(myProject);
  }
}