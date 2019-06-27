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
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.util.messages.MessageBus;
import org.mockito.Mock;

/**
 * Tests for {@link GradleSyncState}.
 */
public class GradleSyncStateIntegrationTest extends AndroidGradleTestCase {
  @Mock private GradleSyncListener myGradleSyncListener;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    MessageBus messageBus = mock(MessageBus.class);
    when(messageBus.syncPublisher(GRADLE_SYNC_TOPIC)).thenReturn(myGradleSyncListener);
  }

  public void testCompoundSyncEnabled() throws Exception {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
      StudioFlags.COMPOUND_SYNC_ENABLED.override(true);

      loadSimpleApplication();

      // Project imported with no source generation
      verify(myGradleSyncListener, times(0)).sourceGenerationFinished(eq(getProject()));

      // Sync with source generation
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(getProject(), TRIGGER_TEST_REQUESTED, myGradleSyncListener);

      verify(myGradleSyncListener).sourceGenerationFinished(eq(getProject()));
    }
    finally {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
    }
  }
}