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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.Map;

import static com.android.builder.model.SyncIssue.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
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
    mySyncIssue =  mock(SyncIssue.class);
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
    reporter.report(ImmutableMap.of(appModule, Lists.newArrayList(mySyncIssue)));

    verify(myStrategy1, never())
      .reportAll(ImmutableList.of(mySyncIssue), ImmutableMap.of(mySyncIssue, appModule), ImmutableMap.of(appModule, buildFile));
    verify(myStrategy2)
      .reportAll(ImmutableList.of(mySyncIssue), ImmutableMap.of(mySyncIssue, appModule), ImmutableMap.of(appModule, buildFile));

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
    reporter.report(ImmutableMap.of(appModule, Lists.newArrayList(mySyncIssue)));

    verify(myStrategy1, never())
      .reportAll(ImmutableList.of(mySyncIssue), ImmutableMap.of(mySyncIssue, appModule), ImmutableMap.of(appModule, buildFile));
    verify(myStrategy2)
      .reportAll(ImmutableList.of(mySyncIssue), ImmutableMap.of(mySyncIssue, appModule), ImmutableMap.of(appModule, buildFile));
  }

  public void testReportUsingDefaultStrategy() throws Exception {
    loadProject(DEPENDENT_MODULES);
    mySyncMessagesStub.clearReportedMessages();

    // This issue is created to be equal to mySyncIssue, in practice issues with the same fields will be classed as equal.
    SyncIssue syncIssue2 = new SyncIssue() {
      @Override
      public int getSeverity() {
        return SEVERITY_ERROR;
      }

      @Override
      public int getType() {
        return TYPE_GRADLE_TOO_OLD;
      }

      @Nullable
      @Override
      public String getData() {
        return "";
      }

      @NonNull
      @Override
      public String getMessage() {
        return "";
      }

      @Nullable
      @Override
      public List<String> getMultiLineMessage() {
        return null;
      }

      @Override
      public int hashCode() {
        return mySyncIssue.hashCode();
      }

      @Override
      public boolean equals(@NonNull Object o) {
        return o instanceof SyncIssue;
      }
    };

    when(mySyncIssue.getType()).thenReturn(TYPE_GRADLE_TOO_OLD);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    when(myStrategy2.getSupportedIssueType()).thenReturn(TYPE_UNRESOLVED_DEPENDENCY);

    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);

    Module appModule = myModules.getModule("app");
    Module libModule = myModules.getModule("lib");
    VirtualFile buildFile = getGradleBuildFile(appModule);
    reporter.report(ImmutableMap.of(appModule, Lists.newArrayList(mySyncIssue), libModule, Lists.newArrayList(syncIssue2)));

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertSize(1, mySyncMessagesStub.getReportedMessages());

    verify(myStrategy1, never())
      .reportAll(ImmutableList.of(mySyncIssue), ImmutableMap.of(mySyncIssue, appModule), ImmutableMap.of(appModule, buildFile));
    verify(myStrategy2, never())
      .reportAll(ImmutableList.of(mySyncIssue), ImmutableMap.of(mySyncIssue, appModule), ImmutableMap.of(appModule, buildFile));

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
    assertThat(strategies).hasSize(7);

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

    strategy = strategies.get(TYPE_MIN_SDK_VERSION_IN_MANIFEST);
    assertThat(strategy).isInstanceOf(SdkInManifestIssuesReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));

    strategy = strategies.get(TYPE_DEPRECATED_CONFIGURATION);
    assertThat(strategy).isInstanceOf(DeprecatedConfigurationReporter.class);
    assertSame(mySyncMessagesStub, strategy.getSyncMessages(appModule));
  }
}