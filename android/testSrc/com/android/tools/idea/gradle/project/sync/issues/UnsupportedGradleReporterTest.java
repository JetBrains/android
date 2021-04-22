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

import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link UnsupportedGradleReporter}.
 */
public class UnsupportedGradleReporterTest extends AndroidGradleTestCase {
  private IdeSyncIssue mySyncIssue;
  private GradleSyncMessagesStub mySyncMessagesStub;
  private UnsupportedGradleReporter myReporter;
  private TestSyncIssueUsageReporter myUsageReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(IdeSyncIssue.class);
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
    myReporter = new UnsupportedGradleReporter();
    myUsageReporter = new TestSyncIssueUsageReporter();
  }

  public void testGetSupportedIssueType() {
    assertEquals(IdeSyncIssue.TYPE_GRADLE_TOO_OLD, myReporter.getSupportedIssueType());
  }

  public void testReport() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    String expectedText = "Hello World!";
    when(mySyncIssue.getMessage()).thenReturn(expectedText);
    when(mySyncIssue.getData()).thenReturn("2.14.1");

    myReporter.report(mySyncIssue, appModule, null, myUsageReporter);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(1);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup("Gradle Sync Issues")
                                            .hasMessageLine(expectedText, 0);
    // @formatter:on

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    assertThat(quickFixes).hasSize(3);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(FixGradleVersionInWrapperHyperlink.class);
    FixGradleVersionInWrapperHyperlink hyperlink = (FixGradleVersionInWrapperHyperlink)quickFix;
    assertEquals("2.14.1", hyperlink.getGradleVersion());

    verifyOpenGradleWrapperPropertiesFile(getProject(), quickFixes.get(1));

    quickFix = quickFixes.get(2);
    assertThat(quickFix).isInstanceOf(OpenGradleSettingsHyperlink.class);

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.FIX_GRADLE_VERSION_IN_WRAPPER_HYPERLINK)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_GRADLE_SETTINGS_HYPERLINK)
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  private static void verifyOpenGradleWrapperPropertiesFile(@NotNull Project project, @NotNull NotificationHyperlink link) {
    assertThat(link).isInstanceOf(OpenFileHyperlink.class);
    OpenFileHyperlink openFileHyperlink = (OpenFileHyperlink)link;
    assertTrue(openFileHyperlink.toHtml().contains("Open Gradle wrapper properties"));
    assertThat(openFileHyperlink.getFilePath()).isEqualTo(
      FileUtils.toSystemIndependentPath(GradleWrapper.find(project).getPropertiesFilePath().getAbsolutePath()));
  }
}