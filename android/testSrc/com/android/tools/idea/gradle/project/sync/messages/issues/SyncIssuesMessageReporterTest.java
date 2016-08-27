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
package com.android.tools.idea.gradle.project.sync.messages.issues;

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessageReporterStub;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import static com.android.builder.model.SyncIssue.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SyncIssuesMessageReporter}.
 */
public class SyncIssuesMessageReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private SyncMessageReporterStub myReporterStub;
  private BaseSyncIssueMessageReporter myStrategy1;
  private BaseSyncIssueMessageReporter myStrategy2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    myReporterStub = new SyncMessageReporterStub(getProject());

    myStrategy1 = mock(BaseSyncIssueMessageReporter.class);
    when(myStrategy1.getSupportedIssueType()).thenReturn(TYPE_BUILD_TOOLS_TOO_LOW);

    myStrategy2 = mock(BaseSyncIssueMessageReporter.class);
  }

  public void testReportError() throws Exception {
    loadProject("projects/transitiveDependencies");

    int issueType = TYPE_GRADLE_TOO_OLD;
    when(mySyncIssue.getType()).thenReturn(issueType);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    when(myStrategy2.getSupportedIssueType()).thenReturn(issueType); // This is the strategy to be invoked.

    SyncIssuesMessageReporter reporter = new SyncIssuesMessageReporter(myReporterStub, myStrategy1, myStrategy2);

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    reporter.reportSyncIssues(Lists.newArrayList(mySyncIssue), appModule);

    verify(myStrategy1, never()).report(mySyncIssue, appModule, buildFile);
    verify(myStrategy2).report(mySyncIssue, appModule, buildFile);

    assertTrue(GradleSyncState.getInstance(getProject()).getSummary().hasErrors());
  }

  public void testReportWarning() throws Exception {
    loadProject("projects/transitiveDependencies");

    int issueType = TYPE_GRADLE_TOO_OLD;
    when(mySyncIssue.getType()).thenReturn(issueType);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_WARNING);

    when(myStrategy2.getSupportedIssueType()).thenReturn(issueType); // This is the strategy to be invoked.

    SyncIssuesMessageReporter reporter = new SyncIssuesMessageReporter(myReporterStub, myStrategy1, myStrategy2);

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    reporter.reportSyncIssues(Lists.newArrayList(mySyncIssue), appModule);

    verify(myStrategy1, never()).report(mySyncIssue, appModule, buildFile);
    verify(myStrategy2).report(mySyncIssue, appModule, buildFile);
  }

  public void testReportUsingDefaultStrategy() throws Exception {
    loadProject("projects/transitiveDependencies");

    when(mySyncIssue.getType()).thenReturn(TYPE_GRADLE_TOO_OLD);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    when(myStrategy2.getSupportedIssueType()).thenReturn(TYPE_UNRESOLVED_DEPENDENCY);

    SyncIssuesMessageReporter reporter = new SyncIssuesMessageReporter(myReporterStub, myStrategy1, myStrategy2);

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    reporter.reportSyncIssues(Lists.newArrayList(mySyncIssue), appModule);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    verify(myStrategy1, never()).report(mySyncIssue, appModule, buildFile);
    verify(myStrategy2, never()).report(mySyncIssue, appModule, buildFile);

    assertTrue(GradleSyncState.getInstance(getProject()).getSummary().hasErrors());
  }
}