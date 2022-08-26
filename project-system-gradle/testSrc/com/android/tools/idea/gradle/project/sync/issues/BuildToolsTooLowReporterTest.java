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
package com.android.tools.idea.gradle.project.sync.issues;

import static com.android.tools.idea.gradle.model.IdeSyncIssue.SEVERITY_ERROR;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.project.messages.MessageType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import java.util.ArrayList;
import java.util.List;
import org.mockito.Mock;

/**
 * Tests for {@link BuildToolsTooLowReporter}.
 */
public class BuildToolsTooLowReporterTest extends PlatformTestCase {
  @Mock private IdeSyncIssue mySyncIssue;
  private BuildToolsTooLowReporter myIssueReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    initMocks(this);
    myIssueReporter = new BuildToolsTooLowReporter();
  }

  public void testGetSupportedIssueType() {
    assertSame(IdeSyncIssue.TYPE_BUILD_TOOLS_TOO_LOW, myIssueReporter.getSupportedIssueType());
  }

  public void testReport() {
    String text = "Upgrade Build Tools!";
    when(mySyncIssue.getMessage()).thenReturn(text);

    String minVersion = "25";
    when(mySyncIssue.getData()).thenReturn(minVersion);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);
    when(mySyncIssue.getType()).thenReturn(IdeSyncIssue.TYPE_BUILD_TOOLS_TOO_LOW);

    Module module = getModule();
    List<SyncIssueNotificationHyperlink> quickFixes = new ArrayList<>();
    quickFixes.add(new TestSyncIssueNotificationHyperlink("url", "", AndroidStudioEvent.GradleSyncQuickFix.UNKNOWN_GRADLE_SYNC_QUICK_FIX));

    BuildToolsTooLowReporter spiedReporter = spy(myIssueReporter);

    when(spiedReporter.getQuickFixHyperlinks(minVersion, ImmutableList.of(module), ImmutableMap.of()))
      .thenReturn(quickFixes);



    final var messages = spiedReporter.report(mySyncIssue, module, null);
    assertThat(messages).hasSize(1);

    final var message = messages.get(0);

    assertEquals(MessageType.WARNING, message.getType());
    assertEquals("Upgrade Build Tools!\nAffected Modules: testReport", message.getMessage());

    final var actualQuickFixes = message.getQuickFixes();
    assertEquals(quickFixes,
                 actualQuickFixes.subList(0, actualQuickFixes.size() - 1));
    assertEquals(
        GradleSyncIssue.newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_BUILD_TOOLS_TOO_LOW)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.UNKNOWN_GRADLE_SYNC_QUICK_FIX)
          .build(),
      SyncIssueUsageReporter.createGradleSyncIssue(mySyncIssue.getType(), message));
  }
}
