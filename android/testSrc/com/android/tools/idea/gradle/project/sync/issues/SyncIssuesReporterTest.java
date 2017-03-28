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

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;

import static com.android.builder.model.SyncIssue.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SyncIssuesReporter}.
 */
public class SyncIssuesReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private GradleSyncMessagesStub mySyncMessagesStub;
  private BaseSyncIssuesReporter myStrategy1;
  private BaseSyncIssuesReporter myStrategy2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    myStrategy1 = mock(BaseSyncIssuesReporter.class);
    when(myStrategy1.getSupportedIssueType()).thenReturn(TYPE_BUILD_TOOLS_TOO_LOW);

    myStrategy2 = mock(BaseSyncIssuesReporter.class);
  }

  public void testReportError() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    int issueType = TYPE_GRADLE_TOO_OLD;
    when(mySyncIssue.getType()).thenReturn(issueType);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    when(myStrategy2.getSupportedIssueType()).thenReturn(issueType); // This is the strategy to be invoked.

    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    reporter.report(Lists.newArrayList(mySyncIssue), appModule);

    verify(myStrategy1, never()).report(mySyncIssue, appModule, buildFile);
    verify(myStrategy2).report(mySyncIssue, appModule, buildFile);

    assertTrue(GradleSyncState.getInstance(getProject()).getSummary().hasSyncErrors());
  }

  public void testReportWarning() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    int issueType = TYPE_GRADLE_TOO_OLD;
    when(mySyncIssue.getType()).thenReturn(issueType);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_WARNING);

    when(myStrategy2.getSupportedIssueType()).thenReturn(issueType); // This is the strategy to be invoked.

    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    reporter.report(Lists.newArrayList(mySyncIssue), appModule);

    verify(myStrategy1, never()).report(mySyncIssue, appModule, buildFile);
    verify(myStrategy2).report(mySyncIssue, appModule, buildFile);
  }

  public void testReportUsingDefaultStrategy() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    when(mySyncIssue.getType()).thenReturn(TYPE_GRADLE_TOO_OLD);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    when(myStrategy2.getSupportedIssueType()).thenReturn(TYPE_UNRESOLVED_DEPENDENCY);

    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    reporter.report(Lists.newArrayList(mySyncIssue), appModule);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);

    verify(myStrategy1, never()).report(mySyncIssue, appModule, buildFile);
    verify(myStrategy2, never()).report(mySyncIssue, appModule, buildFile);

    assertTrue(GradleSyncState.getInstance(getProject()).getSummary().hasSyncErrors());
  }

  public void testStrategiesSetInConstructor() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    SyncIssuesReporter reporter = SyncIssuesReporter.getInstance();
    Module appModule = myModules.getAppModule();

    BaseSyncIssuesReporter strategy = reporter.getDefaultMessageFactory();
    assertThat(strategy).isInstanceOf(UnhandledIssuesReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));

    Map<Integer, BaseSyncIssuesReporter> strategies = reporter.getStrategies();
    assertThat(strategies).hasSize(5);

    strategy = strategies.get(TYPE_UNRESOLVED_DEPENDENCY);
    assertThat(strategy).isInstanceOf(UnresolvedDependenciesReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));

    strategy = strategies.get(TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION);
    assertThat(strategy).isInstanceOf(ExternalNdkBuildIssuesReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));

    strategy = strategies.get(TYPE_GRADLE_TOO_OLD);
    assertThat(strategy).isInstanceOf(UnsupportedGradleReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));

    strategy = strategies.get(TYPE_BUILD_TOOLS_TOO_LOW);
    assertThat(strategy).isInstanceOf(BuildToolsTooLowReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));

    strategy = strategies.get(TYPE_MISSING_SDK_PACKAGE);
    assertThat(strategy).isInstanceOf(MissingSdkPackageSyncIssuesReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));
  }
}