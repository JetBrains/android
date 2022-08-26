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

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;

/**
 * Tests for {@link UnhandledIssuesReporter}.
 */
public class UnhandledIssueMessageReporterTest extends AndroidGradleTestCase {
  private UnhandledIssuesReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myReporter = new UnhandledIssuesReporter();
  }

  public void testGetSupportedIssueType() {
    assertEquals(-1, myReporter.getSupportedIssueType());
  }

  public void testReportWithBuildFile() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    String text = "Hello World!";
    String expectedText =
      text + "\nAffected Modules:";
    final var syncIssue = new IdeSyncIssueImpl(
      IdeSyncIssue.SEVERITY_ERROR,
      SyncIssue.TYPE_GENERIC,
      null,
      text,
      null
    );

    VirtualFile buildFile = getGradleBuildFile(appModule);
    final var messages = myReporter.report(syncIssue, appModule, buildFile);

    assertSize(1, messages);

    final var message = messages.get(0);
    assertEquals(MessageType.WARNING, message.getType());
    assertThat(String.join("", message.getMessage())).contains(expectedText);

    assertThat(message.getNavigatable()).isInstanceOf(OpenFileDescriptor.class);
    OpenFileDescriptor navigatable = (OpenFileDescriptor)message.getNavigatable();
    assertEquals(buildFile, navigatable.getFile());

    VirtualFile file = ((OpenFileDescriptor)message.getNavigatable()).getFile();
    assertSame(buildFile, file);

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue.newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
          .build()),
      SyncIssueUsageReporter.createGradleSyncIssues(0, messages));
  }

  public void testReportWithoutBuildFile() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    String text = "Hello World!";
    String expectedText = text + "\nAffected Modules: app";
    final var syncIssue = new IdeSyncIssueImpl(
      IdeSyncIssue.SEVERITY_ERROR,
      SyncIssue.TYPE_GENERIC,
      null,
      text,
      null
    );

    final var messages = myReporter.report(syncIssue, appModule, null);

    assertSize(1, messages);

    final var message = messages.get(0);
    assertEquals(MessageType.WARNING, message.getType());
    assertEquals(expectedText, message.getMessage());

    assertEquals(NonNavigatable.INSTANCE, message.getNavigatable());

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE).build()),
      SyncIssueUsageReporter.createGradleSyncIssues(0, messages));
  }
}
