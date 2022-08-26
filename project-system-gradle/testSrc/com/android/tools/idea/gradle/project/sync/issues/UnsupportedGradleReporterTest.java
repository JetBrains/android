/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileSyncMessageHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.hyperlink.SyncMessageFragment;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncIssueType;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncQuickFix;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link UnsupportedGradleReporter}.
 */
public class UnsupportedGradleReporterTest extends AndroidGradleTestCase {
  private UnsupportedGradleReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myReporter = new UnsupportedGradleReporter();
  }

  public void testGetSupportedIssueType() {
    assertEquals(IdeSyncIssue.TYPE_GRADLE_TOO_OLD, myReporter.getSupportedIssueType());
  }

  public void testReport() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    String expectedText = "Hello World!";
    final var syncIssue = new IdeSyncIssueImpl(
      IdeSyncIssue.SEVERITY_WARNING,
      0 /* unspecified? */,
      "2.14.1",
      expectedText,
      null
    );

    final var messages = myReporter.report(syncIssue, appModule, null);
    final var message = messages.get(0);

    assertNotNull(message);
    assertThat(message.getText()).isEqualTo(expectedText);
    assertThat(message.getGroup()).isEqualTo("Gradle Sync Issues");

    final var quickFixes = message.getQuickFixes();
    assertThat(quickFixes).hasSize(3);

    var quickFix = quickFixes.get(0);
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
          .setType(GradleSyncIssueType.UNKNOWN_GRADLE_SYNC_ISSUE_TYPE)
          .addOfferedQuickFixes(GradleSyncQuickFix.FIX_GRADLE_VERSION_IN_WRAPPER_HYPERLINK)
          .addOfferedQuickFixes(GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
          .addOfferedQuickFixes(GradleSyncQuickFix.OPEN_GRADLE_SETTINGS_HYPERLINK)
          .build()),
      SyncIssueUsageReporter.createGradleSyncIssues(0, messages));
  }

  private static void verifyOpenGradleWrapperPropertiesFile(@NotNull Project project, @NotNull SyncMessageFragment link) {
    assertThat(link).isInstanceOf(OpenFileSyncMessageHyperlink.class);
    final var openFileHyperlink = (OpenFileSyncMessageHyperlink)link;
    assertTrue(openFileHyperlink.toHtml().contains("Open Gradle wrapper properties"));
    assertThat(openFileHyperlink.getFilePath()).isEqualTo(
      FileUtils.toSystemIndependentPath(GradleWrapper.find(project).getPropertiesFilePath().getAbsolutePath()));
  }
}
