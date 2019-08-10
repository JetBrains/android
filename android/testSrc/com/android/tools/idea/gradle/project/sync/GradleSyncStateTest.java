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

import static com.android.tools.idea.gradle.project.sync.GradleSyncState.GRADLE_SYNC_TOPIC;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBus;
import org.mockito.Mock;

/**
 * Tests for {@link GradleSyncState}.
 */
public class GradleSyncStateTest extends PlatformTestCase {
  @Mock private GradleSyncListener myGradleSyncListener;
  @Mock private StateChangeNotification myChangeNotification;
  @Mock private GradleFiles myGradleFiles;
  @Mock private ProjectStructure myProjectStructure;
  @Mock private ExternalSystemTaskId myTaskId;

  private GradleSyncState mySyncState;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    MessageBus messageBus = mock(MessageBus.class);

    new IdeComponents(myProject).replaceProjectService(GradleFiles.class, myGradleFiles);
    mySyncState = new GradleSyncState(myProject, AndroidProjectInfo.getInstance(myProject), GradleProjectInfo.getInstance(myProject),
                                      messageBus, myProjectStructure, myChangeNotification);

    when(messageBus.syncPublisher(GRADLE_SYNC_TOPIC)).thenReturn(myGradleSyncListener);
  }

  public void testSyncStartedUserNotification() {
    assertFalse(mySyncState.isSyncInProgress());

    boolean syncStarted = mySyncState.syncStarted(new GradleSyncInvoker.Request(TRIGGER_TEST_REQUESTED), null);
    assertTrue(syncStarted);
    assertTrue(mySyncState.isSyncInProgress());

    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(myGradleSyncListener, times(1)).syncStarted(myProject, true);
  }

  public void testSyncSkipped() {
    long timestamp = -1231231231299L; // Some random number

    mySyncState.syncSkipped(timestamp, null);

    assertThat(mySyncState.getLastSyncFinishedTimeStamp()).isNotEqualTo(-1L);
    verify(myChangeNotification, never()).notifyStateChanged();
    verify(myGradleSyncListener, times(1)).syncSkipped(myProject);
  }

  public void testSyncSkippedAfterSyncStarted() {
    long timestamp = -1231231231299L; // Some random number

    mySyncState.syncStarted(new GradleSyncInvoker.Request(TRIGGER_TEST_REQUESTED), null);
    mySyncState.syncSkipped(timestamp, null);
    assertFalse(mySyncState.isSyncInProgress());
  }

  public void testSyncFailed() {
    String msg = "Something went wrong";
    mySyncState.setSyncStartedTimeStamp(0, TRIGGER_TEST_REQUESTED);
    mySyncState.syncFailed(msg, null, null);

    assertThat(mySyncState.getLastSyncFinishedTimeStamp()).isNotEqualTo(-1L);
    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(myGradleSyncListener, times(1)).syncFailed(myProject, msg);
    verify(myProjectStructure, times(1)).clearData();
  }

  public void testSyncFailedWithoutSyncStarted() {
    String msg = "Something went wrong";
    mySyncState.setSyncStartedTimeStamp(-1, TRIGGER_TEST_REQUESTED);
    mySyncState.syncFailed(msg, null, null);
    verify(myGradleSyncListener, never()).syncFailed(myProject, msg);
  }

  public void testSyncEnded() {
    mySyncState.setSyncStartedTimeStamp(0, TRIGGER_TEST_REQUESTED);
    mySyncState.syncSucceeded();

    assertThat(mySyncState.getLastSyncFinishedTimeStamp()).isNotEqualTo(-1L);
    verify(myChangeNotification, times(1)).notifyStateChanged();
    verify(myGradleSyncListener, times(1)).syncSucceeded(myProject);
  }

  public void testSyncEndedWithoutSyncStarted() {
    mySyncState.setSyncStartedTimeStamp(-1, TRIGGER_TEST_REQUESTED);
    mySyncState.syncSucceeded();
    verify(myGradleSyncListener, never()).syncSucceeded(myProject);
  }

  public void testSetupStarted() {
    mySyncState.setupStarted();

    verify(myGradleSyncListener, times(1)).setupStarted(myProject);
  }

  public void testGetSyncTimesSuccess() {
    // Random time when this was written
    long base = 1493320159894L;
    // Time for Gradle
    long gradleTimeMs = 10000;
    // Time for Ide
    long ideTimeMs = 20000;
    long totalTimeMs = gradleTimeMs + ideTimeMs;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_TEST_REQUESTED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not finished)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Gradle part finished
    mySyncState.setSyncSetupStartedTimeStamp(base + gradleTimeMs);
    assertEquals("Total time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not finished)", -1L, mySyncState.getSyncIdeTimeMs());

    // Ide part finished
    mySyncState.setSyncEndedTimeStamp(base + totalTimeMs);
    assertEquals("Total time should be " + totalTimeMs, totalTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be " + ideTimeMs, ideTimeMs, mySyncState.getSyncIdeTimeMs());
  }

  public void testGetSyncTimesFailedGradle() {
    // Random time when this was written
    long base = 1493321162360L;
    // Time for failure
    long failTimeMs = 30000;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_TEST_REQUESTED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not finished)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Gradle part failed
    mySyncState.setSyncFailedTimeStamp(base + failTimeMs);
    assertEquals("Total time should be " + failTimeMs, failTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (failed)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());
  }

  public void testGetSyncTimesFailedIde() {
    // Random time when this was written
    long base = 1493321769342L;
    // Time for Gradle
    long gradleTimeMs = 40000;
    // Time for Ide
    long failTimeMs = 50000;
    long totalTimeMs = gradleTimeMs + failTimeMs;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_TEST_REQUESTED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not finished)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Gradle part finished
    mySyncState.setSyncSetupStartedTimeStamp(base + gradleTimeMs);
    assertEquals("Total time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not finished)", -1L, mySyncState.getSyncIdeTimeMs());

    // Ide part failed
    mySyncState.setSyncFailedTimeStamp(base + totalTimeMs);
    assertEquals("Total time should be " + totalTimeMs, totalTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be " + gradleTimeMs, gradleTimeMs, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (failed)", -1L, mySyncState.getSyncIdeTimeMs());
  }

  public void testGetSyncTimesSkipped() {
    // Random time when this was written
    long base = 1493322274878L;
    // Time it took
    long skippedTimeMs = 60000;

    // Sync started but nothing else
    mySyncState.setSyncStartedTimeStamp(base, TRIGGER_TEST_REQUESTED);
    assertEquals("Total time should be 0 as nothing has finished", 0L, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not started)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());

    // Sync was skipped
    mySyncState.setSyncEndedTimeStamp(base + skippedTimeMs);
    assertEquals("Total time should be " + skippedTimeMs, skippedTimeMs, mySyncState.getSyncTotalTimeMs());
    assertEquals("Gradle time should be -1 (not started)", -1L, mySyncState.getSyncGradleTimeMs());
    assertEquals("IDE time should be -1 (not started)", -1L, mySyncState.getSyncIdeTimeMs());
  }

  public void testIsSyncNeeded_IfNeverSynced() {
    when(myGradleFiles.areGradleFilesModified()).thenAnswer((invocation) -> true);
    assertThat(mySyncState.isSyncNeeded()).isSameAs(ThreeState.YES);
  }

  public void testSyncNotNeeded_IfNothingModified() {
    when(myGradleFiles.areGradleFilesModified()).thenAnswer((invocation -> false));
    assertThat(mySyncState.isSyncNeeded()).isSameAs(ThreeState.NO);
  }

  /**
   * Check that myExternalSystemTaskId is set to null (if it was ever set) when sync finishes
   */
  public void testExternalSystemTaskIdEnded() {
    mySyncState.syncStarted(new GradleSyncInvoker.Request(TRIGGER_TEST_REQUESTED), null);
    mySyncState.setExternalSystemTaskId(myTaskId);
    assertEquals(myTaskId, mySyncState.getExternalSystemTaskId());
    mySyncState.syncSucceeded();
    assertNull(mySyncState.getExternalSystemTaskId());
  }

  /**
   * Check that myExternalSystemTaskId is set to null (if it was ever set) when sync finishes
   */
  public void testExternalSystemTaskIdSkipped() {
    long timestamp = -1231231231299L; // Some random number

    // TODO Add trigger for testing?
    mySyncState.syncStarted(new GradleSyncInvoker.Request(TRIGGER_TEST_REQUESTED), null);
    mySyncState.setExternalSystemTaskId(myTaskId);
    assertEquals(myTaskId, mySyncState.getExternalSystemTaskId());
    mySyncState.syncSkipped(timestamp, null);
    assertNull(mySyncState.getExternalSystemTaskId());
  }

  public void testSourceGenerationFinished() {
    mySyncState.sourceGenerationFinished();

    verify(myGradleSyncListener, times(1)).sourceGenerationFinished(myProject);
  }

  public void testSyncStateFailedWithMessage() {
    mySyncState.setSyncStartedTimeStamp(1, TRIGGER_TEST_REQUESTED);
    mySyncState.syncFailed("Some Message", new RuntimeException("Runtime Message"), null);

    verify(myGradleSyncListener).syncFailed(myProject, "Some Message");
  }

  public void testSyncStateFailedWithThrowableMessage() {
    mySyncState.setSyncStartedTimeStamp(1, TRIGGER_TEST_REQUESTED);
    mySyncState.syncFailed(null, new RuntimeException("Runtime Message"), null);

    verify(myGradleSyncListener).syncFailed(myProject, "Runtime Message");
  }

  public void testSynStateFailedWithNoMessage() {
    mySyncState.setSyncStartedTimeStamp(1, TRIGGER_TEST_REQUESTED);
    mySyncState.syncFailed(null, null, null);

    verify(myGradleSyncListener).syncFailed(myProject, "Unknown cause");
  }
}
