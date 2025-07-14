/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.runsGradleErrors;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueUsageReporter;
import com.android.tools.idea.gradle.project.sync.issues.UnhandledIssuesReporter;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.NonNavigatable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;


/**
 * Tests for {@link UnhandledIssuesReporter}.
 */
public class UnhandledIssueMessageReporterIntegrationTest {
  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  private UnhandledIssuesReporter myReporter;

  @Before
  public void setup() throws Exception {
    projectRule.loadProject(SIMPLE_APPLICATION);
    myReporter = new UnhandledIssuesReporter();
  }

  @Test
  public void testReportWithBuildFile() throws Exception {
    Module appModule = TestModuleUtil.findAppModule(projectRule.getProject());

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
    assertThat(messages).hasSize(1);

    final var message = messages.get(0).getSyncMessage();
    assertThat(message.getType()).isEqualTo(MessageType.WARNING);
    assertThat(String.join("", message.getMessage())).contains(expectedText);

    assertThat(message.getNavigatable()).isInstanceOf(OpenFileDescriptor.class);
    OpenFileDescriptor navigatable = (OpenFileDescriptor)message.getNavigatable();
    assertThat(navigatable.getFile()).isEqualTo(buildFile);

    VirtualFile file = ((OpenFileDescriptor)message.getNavigatable()).getFile();
    assertThat(file).isSameAs(buildFile);

    assertThat(messages.get(0).getAffectedModules()).isEqualTo(ImmutableList.of(appModule));

    assertThat(SyncIssueUsageReporter.createGradleSyncIssues(0, ImmutableList.of(message)))
      .isEqualTo(ImmutableList.of(
                   GradleSyncIssue.newBuilder()
                     .setType(AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE)
                     .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
                     .build()));
  }

  @Test
  public void testReportWithoutBuildFile() throws Exception {
    Module appModule = TestModuleUtil.findAppModule(projectRule.getProject());

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
    assertThat(messages).hasSize(1);

    final var message = messages.get(0).getSyncMessage();
    assertThat(message.getType()).isEqualTo(MessageType.WARNING);
    assertThat(message.getMessage()).isEqualTo(expectedText);
    assertThat(message.getNavigatable()).isEqualTo(NonNavigatable.INSTANCE);
    assertThat(messages.get(0).getAffectedModules()).isEqualTo(ImmutableList.of(appModule));

    assertThat(SyncIssueUsageReporter.createGradleSyncIssues(0, ImmutableList.of(message)))
      .isEqualTo(
        ImmutableList.of(
          GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE).build()));
  }
}
