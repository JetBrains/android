/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.model.impl.IdeSyncIssueImpl;
import com.android.tools.idea.gradle.project.sync.hyperlink.DisableOfflineModeHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowSyncIssuesDetailsHyperlink;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.testFramework.PlatformTestCase;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.mockito.Mock;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
public class UnresolvedDependenciesReporterTest extends PlatformTestCase {
  @Mock private GradleSettings myGradleSettings;

  private UnresolvedDependenciesReporter myReporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    new IdeComponents(getProject()).replaceProjectService(GradleSettings.class, myGradleSettings);
    myReporter = new UnresolvedDependenciesReporter();
  }

  public void testReportWithoutDependencyAndExtraInfo() {
    String text = "Hello!";
    String expected = text +
                      "\n<a href=\"Hello%21\">Show Details</a>" +
                      "\nAffected Modules: testReportWithoutDependencyAndExtraInfo";
    List<String> extraInfo = Arrays.asList("line1", "line2");
    final var syncIssue = new IdeSyncIssueImpl(
      IdeSyncIssue.SEVERITY_WARNING,
      TYPE_UNRESOLVED_DEPENDENCY,
      null,
      text,
      extraInfo
    );

    final var messages = myReporter.report(syncIssue, getModule(), null);

    assertSize(1, messages);
    final var message = messages.get(0);
    assertEquals(expected, message.getMessage());


    assertSize(1 + 1 /* affected modules */, messages.get(0).getQuickFixes());
    assertInstanceOf(messages.get(0).getQuickFixes().get(0), ShowSyncIssuesDetailsHyperlink.class);

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_SYNC_ISSUES_DETAILS_HYPERLINK)
          .build()),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
  }

  /**
   * Disable offline mode quickfix should be offered when missing dependencies on offline mode
   */
  public void testReportOfflineMode() {
    String text = "Hello!";
    String expected = text +
                      "\n<a href=\"disable.gradle.offline.mode\">Disable offline mode and sync project</a>" +
                      "\nAffected Modules: testReportOfflineMode";
    when(myGradleSettings.isOfflineWork()).thenReturn(true);
    final var syncIssue = new IdeSyncIssueImpl(
      IdeSyncIssue.SEVERITY_WARNING,
      TYPE_UNRESOLVED_DEPENDENCY,
      null,
      text,
      null
    );

    final var messages = myReporter.report(syncIssue, getModule(), null);

    assertSize(1, messages);
    final var message = messages.get(0);
    assertEquals(expected, message.getMessage());

    assertSize(1 + 1 /* affected modules */, messages.get(0).getQuickFixes());
    assertInstanceOf(messages.get(0).getQuickFixes().get(0), DisableOfflineModeHyperlink.class);

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.DISABLE_OFFLINE_MODE_HYPERLINK)
          .build()),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
  }

  /**
   * Disable offline mode quickfix should *NOT* be offered when offline mode is not enabled
   */
  public void testReportNoOfflineMode() {
    String text = "Hello!";
    String expected = text + "\nAffected Modules: testReportNoOfflineMode";
    final var syncIssue = new IdeSyncIssueImpl(
      IdeSyncIssue.SEVERITY_WARNING,
      TYPE_UNRESOLVED_DEPENDENCY,
      null,
      text,
      null
    );

    when(myGradleSettings.isOfflineWork()).thenReturn(false);
    final var messages = myReporter.report(syncIssue, getModule(), null);

    assertSize(1, messages);
    final var message = messages.get(0);
    assertEquals(expected, message.getMessage());

    assertSize(1 /* go to module */ + 0, message.getQuickFixes());

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue.newBuilder().setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY).build()),
      SyncIssueUsageReporter.createGradleSyncIssues(IdeSyncIssue.TYPE_UNRESOLVED_DEPENDENCY, messages));
  }
}
