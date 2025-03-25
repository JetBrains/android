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

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.builder.model.SyncIssue.SEVERITY_WARNING;
import static com.android.builder.model.SyncIssue.TYPE_AGP_USED_JAVA_VERSION_TOO_LOW;
import static com.android.builder.model.SyncIssue.TYPE_ANDROID_X_PROPERTY_NOT_ENABLED;
import static com.android.builder.model.SyncIssue.TYPE_BUILD_TOOLS_TOO_LOW;
import static com.android.builder.model.SyncIssue.TYPE_COMPILE_SDK_VERSION_TOO_HIGH;
import static com.android.builder.model.SyncIssue.TYPE_DEPENDENCY_INTERNAL_CONFLICT;
import static com.android.builder.model.SyncIssue.TYPE_DEPRECATED_CONFIGURATION;
import static com.android.builder.model.SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION;
import static com.android.builder.model.SyncIssue.TYPE_GRADLE_TOO_OLD;
import static com.android.builder.model.SyncIssue.TYPE_JCENTER_IS_DEPRECATED;
import static com.android.builder.model.SyncIssue.TYPE_MIN_SDK_VERSION_IN_MANIFEST;
import static com.android.builder.model.SyncIssue.TYPE_MISSING_SDK_PACKAGE;
import static com.android.builder.model.SyncIssue.TYPE_SDK_NOT_SET;
import static com.android.builder.model.SyncIssue.TYPE_TARGET_SDK_VERSION_IN_MANIFEST;
import static com.android.builder.model.SyncIssue.TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD;
import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.build.events.GradleErrorQuickFixProvider;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.snapshots.LightGradleSyncTestProjects;
import com.android.tools.idea.project.hyperlink.SyncMessageHyperlink;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.project.messages.SyncMessageWithContext;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;

/**
 * Tests for {@link SyncIssuesReporter}.
 */
public class SyncIssuesReporterTest {
  @Rule public AndroidProjectRule projectRule = AndroidProjectRule.testProject(LightGradleSyncTestProjects.SIMPLE_APPLICATION);

  private IdeSyncIssue mySyncIssue;
  private BaseSyncIssuesReporter myStrategy1;
  private BaseSyncIssuesReporter myStrategy2;

  @Before
  public void setUp() throws Exception {
    mySyncIssue = mock(IdeSyncIssue.class);

    myStrategy1 = mock(BaseSyncIssuesReporter.class);
    when(myStrategy1.getSupportedIssueType()).thenReturn(TYPE_BUILD_TOOLS_TOO_LOW);

    myStrategy2 = mock(BaseSyncIssuesReporter.class);
  }

  @Test
  public void testReportError() throws Exception {
    Project project = projectRule.getProject();

    int issueType = TYPE_GRADLE_TOO_OLD;
    when(mySyncIssue.getType()).thenReturn(issueType);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    Module appModule = TestModuleUtil.findAppModule(project);
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertThat(buildFile).isNotNull();

    when(myStrategy2.getSupportedIssueType()).thenReturn(issueType); // This is the strategy to be invoked.
    when(myStrategy2.reportAll(anyList(), anyMap(), anyMap())).thenReturn(List.of(new SyncMessageWithContext(
      new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR, "text"),
      List.of(appModule)
    )));

    SyncIssueUsageReporter usageReporter = spy(SyncIssueUsageReporter.Companion.getInstance(project));
    projectRule.replaceProjectService(SyncIssueUsageReporter.class, usageReporter);

    // Act
    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);
    reporter.report(ImmutableMap.of(appModule, Lists.newArrayList(mySyncIssue)), "/");

    // Verify passed messages to GradleSyncMessages
    assertThat(GradleSyncMessages.getInstance(project).getReportedMessages()).hasSize(1);
    final var message = GradleSyncMessages.getInstance(project).getReportedMessages().get(0);
    assertThat(message).isNotNull();
    assertThat(message.getType()).isEqualTo(MessageType.ERROR);

    // Verify invoked strategies
    verify(myStrategy1, never())
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));
    verify(myStrategy2)
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));

    // Verify interaction with usage tracker
    verify(usageReporter).collect(ArgumentMatchers.assertArg(
      arg -> assertThat(arg.getType()).isEqualTo(AndroidStudioEvent.GradleSyncIssueType.TYPE_GRADLE_TOO_OLD)
    ));
    verify(usageReporter).reportToUsageTracker("/");
  }

  @Test
  public void testReportWarning() throws Exception {
    Project project = projectRule.getProject();

    int issueType = TYPE_GRADLE_TOO_OLD;
    when(mySyncIssue.getType()).thenReturn(issueType);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_WARNING);

    Module appModule = TestModuleUtil.findAppModule(project);
    when(myStrategy2.getSupportedIssueType()).thenReturn(issueType); // This is the strategy to be invoked.
    when(myStrategy2.reportAll(anyList(), anyMap(), anyMap())).thenReturn(List.of(new SyncMessageWithContext(
      new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.WARNING, "text"),
      List.of(appModule)
    )));

    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);
    SyncIssueUsageReporter usageReporter = spy(SyncIssueUsageReporter.Companion.getInstance(project));
    projectRule.replaceProjectService(SyncIssueUsageReporter.class, usageReporter);

    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertThat(buildFile).isNotNull();
    reporter.report(ImmutableMap.of(appModule, Lists.newArrayList(mySyncIssue)), "/");

    // Verify passed messages to GradleSyncMessages
    assertThat(GradleSyncMessages.getInstance(project).getReportedMessages()).hasSize(1);
    final var message = GradleSyncMessages.getInstance(project).getReportedMessages().get(0);
    assertThat(message).isNotNull();
    assertThat(message.getType()).isEqualTo(MessageType.WARNING);

    // Verify invoked strategies
    verify(myStrategy1, never())
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));
    verify(myStrategy2)
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));

    // Verify interaction with usage tracker
    verify(usageReporter).collect(ArgumentMatchers.assertArg(
      arg -> assertThat(arg.getType()).isEqualTo(AndroidStudioEvent.GradleSyncIssueType.TYPE_GRADLE_TOO_OLD)
    ));
    verify(usageReporter).reportToUsageTracker("/");
  }

  @Test
  public void testReportWithAdditionalLinks() {
    Project project = projectRule.getProject();

    int issueType = TYPE_GRADLE_TOO_OLD;
    when(mySyncIssue.getType()).thenReturn(issueType);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    Module appModule = TestModuleUtil.findAppModule(project);
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertThat(buildFile).isNotNull();

    when(myStrategy2.getSupportedIssueType()).thenReturn(issueType); // This is the strategy to be invoked.
    when(myStrategy2.reportAll(anyList(), anyMap(), anyMap())).thenReturn(List.of(new SyncMessageWithContext(
      new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR, "text"),
      List.of(appModule)
    )));

    SyncIssueUsageReporter usageReporter = spy(SyncIssueUsageReporter.Companion.getInstance(project));
    projectRule.replaceProjectService(SyncIssueUsageReporter.class, usageReporter);
    GradleErrorQuickFixProvider quickFixProvider = mock(GradleErrorQuickFixProvider.class);
    ApplicationManager.getApplication().getExtensionArea().getExtensionPoint(GradleErrorQuickFixProvider.Companion.getEP_NAME())
      .registerExtension(quickFixProvider, project);
    //projectRule.registerExtension(GradleErrorQuickFixProvider.Companion.getEP_NAME(), quickFixProvider);
    when(quickFixProvider.createSyncMessageAdditionalLink(any(), anyList(), anyMap(), anyString())).thenReturn(
      new SyncMessageHyperlink("link.id", "Link Text") {
        @Override
        public @NotNull List<AndroidStudioEvent.GradleSyncQuickFix> getQuickFixIds() {
          return List.of();
        }

        @Override
        protected void execute(@NotNull Project project) {}
      }
    );

    // Act
    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);
    reporter.report(ImmutableMap.of(appModule, Lists.newArrayList(mySyncIssue)), "/");

    // Verify passed messages to GradleSyncMessages
    assertThat(GradleSyncMessages.getInstance(project).getReportedMessages()).hasSize(1);
    final var message = GradleSyncMessages.getInstance(project).getReportedMessages().get(0);
    assertThat(message).isNotNull();
    assertThat(message.getType()).isEqualTo(MessageType.ERROR);
    assertThat(message.getMessage()).endsWith("<a href=\"link.id\">Link Text</a>");

    // Verify invoked strategies
    verify(myStrategy1, never())
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));
    verify(myStrategy2)
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));

    // Verify invoked additional link provider
    verify(quickFixProvider).createSyncMessageAdditionalLink(any(), eq(ImmutableList.of(appModule)), eq(ImmutableMap.of(appModule, buildFile)), eq("/"));
    verifyNoMoreInteractions(quickFixProvider);

    // Verify interaction with usage tracker
    verify(usageReporter).collect(ArgumentMatchers.assertArg(
      arg -> assertThat(arg.getType()).isEqualTo(AndroidStudioEvent.GradleSyncIssueType.TYPE_GRADLE_TOO_OLD)
    ));
    verify(usageReporter).reportToUsageTracker("/");
  }

  @Test
  public void testReportUsingDefaultStrategy() throws Exception {

    // This issue is created to be equal to mySyncIssue, in practice issues with the same fields will be classed as equal.
    IdeSyncIssue syncIssue2 = new IdeSyncIssue() {
      @Override
      public int getSeverity() {
        return SEVERITY_ERROR;
      }

      @Override
      public int getType() {
        return TYPE_GRADLE_TOO_OLD;
      }

      @Override
      public @NotNull String getData() {
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
        return o instanceof IdeSyncIssue;
      }
    };

    when(mySyncIssue.getType()).thenReturn(TYPE_GRADLE_TOO_OLD);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);
    when(mySyncIssue.getMessage()).thenReturn("");
    when(mySyncIssue.getData()).thenReturn("");

    when(myStrategy2.getSupportedIssueType()).thenReturn(TYPE_UNRESOLVED_DEPENDENCY);

    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);
    Project project = projectRule.getProject();
    SyncIssueUsageReporter usageReporter = spy(SyncIssueUsageReporter.Companion.getInstance(project));
    projectRule.replaceProjectService(SyncIssueUsageReporter.class, usageReporter);

    Module appModule = TestModuleUtil.findAppModule(project);
    //Module libModule = TestModuleUtil.findModule(project, "lib");
    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertThat(buildFile).isNotNull();
    reporter.report(ImmutableMap.of(appModule, Lists.newArrayList(mySyncIssue, syncIssue2)), "/");

    assertThat(GradleSyncMessages.getInstance(project).getReportedMessages()).hasSize(1);
    final var message = GradleSyncMessages.getInstance(project).getReportedMessages().get(0);
    assertThat(message).isNotNull();
    assertThat(message.getType()).isEqualTo(MessageType.WARNING);

    verify(myStrategy1, never())
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));
    verify(myStrategy2, never())
      .reportAll(eq(ImmutableList.of(mySyncIssue)), eq(ImmutableMap.of(mySyncIssue, appModule)), eq(ImmutableMap.of(appModule, buildFile)));
    verify(usageReporter).collect(
      GradleSyncIssue.newBuilder()
        .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_GRADLE_TOO_OLD)
        .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.OPEN_FILE_HYPERLINK)
        .build());
    verify(usageReporter).reportToUsageTracker("/");
    verifyNoMoreInteractions(usageReporter);
  }

  @Test
  public void testStrategiesSetInConstructor() throws Exception {
    SyncIssuesReporter reporter = SyncIssuesReporter.getInstance();
    Module appModule = TestModuleUtil.findAppModule(projectRule.getProject());

    BaseSyncIssuesReporter strategy = reporter.getDefaultMessageFactory();
    assertThat(strategy).isInstanceOf(UnhandledIssuesReporter.class);

    Map<Integer, BaseSyncIssuesReporter> strategies = reporter.getStrategies();
    assertThat(strategies).hasSize(16);

    strategy = strategies.get(TYPE_COMPILE_SDK_VERSION_TOO_HIGH);
    assertThat(strategy).isInstanceOf(CompileSdkVersionTooHighReporter.class);

    strategy = strategies.get(TYPE_UNRESOLVED_DEPENDENCY);
    assertThat(strategy).isInstanceOf(UnresolvedDependenciesReporter.class);

    strategy = strategies.get(TYPE_GRADLE_TOO_OLD);
    assertThat(strategy).isInstanceOf(UnsupportedGradleReporter.class);

    strategy = strategies.get(TYPE_BUILD_TOOLS_TOO_LOW);
    assertThat(strategy).isInstanceOf(BuildToolsTooLowReporter.class);

    strategy = strategies.get(TYPE_MISSING_SDK_PACKAGE);
    assertThat(strategy).isInstanceOf(MissingSdkPackageSyncIssuesReporter.class);

    strategy = strategies.get(TYPE_MIN_SDK_VERSION_IN_MANIFEST);
    assertThat(strategy).isInstanceOf(MinSdkInManifestIssuesReporter.class);

    strategy = strategies.get(TYPE_TARGET_SDK_VERSION_IN_MANIFEST);
    assertThat(strategy).isInstanceOf(TargetSdkInManifestIssuesReporter.class);

    strategy = strategies.get(TYPE_DEPRECATED_CONFIGURATION);
    assertThat(strategy).isInstanceOf(DeprecatedConfigurationReporter.class);

    strategy = strategies.get(TYPE_SDK_NOT_SET);
    assertThat(strategy).isInstanceOf(MissingSdkIssueReporter.class);

    strategy = strategies.get(TYPE_THIRD_PARTY_GRADLE_PLUGIN_TOO_OLD);
    assertThat(strategy).isInstanceOf(OutOfDateThirdPartyPluginIssueReporter.class);

    strategy = strategies.get(TYPE_EXTERNAL_NATIVE_BUILD_CONFIGURATION);
    assertThat(strategy).isInstanceOf(CxxConfigurationIssuesReporter.class);

    strategy = strategies.get(TYPE_ANDROID_X_PROPERTY_NOT_ENABLED);
    assertThat(strategy).isInstanceOf(AndroidXUsedReporter.class);

    strategy = strategies.get(TYPE_JCENTER_IS_DEPRECATED);
    assertThat(strategy).isInstanceOf(JcenterDeprecatedReporter.class);

    strategy = strategies.get(TYPE_AGP_USED_JAVA_VERSION_TOO_LOW);
    assertThat(strategy).isInstanceOf(AgpUsedJavaTooLowReporter.class);

    strategy = strategies.get(IdeSyncIssue.TYPE_EXCEPTION);
    assertThat(strategy).isInstanceOf(ExceptionSyncIssuesReporter.class);

    strategy = strategies.get(IdeSyncIssue.TYPE_MISSING_COMPOSE_COMPILER_GRADLE_PLUGIN);
    assertThat(strategy).isInstanceOf(MissingComposeCompilerGradlePluginReporter.class);
  }

  @Test
  public void testReportErrorBeforeWarning() throws Exception {
    IdeSyncIssue syncIssue2 = mock(IdeSyncIssue.class);
    IdeSyncIssue syncIssue3 = mock(IdeSyncIssue.class);

    when(mySyncIssue.getMessage()).thenReturn("Warning message!");
    when(mySyncIssue.getData()).thenReturn("key1");
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_WARNING);
    when(mySyncIssue.getType()).thenReturn(TYPE_BUILD_TOOLS_TOO_LOW);
    when(syncIssue2.getMessage()).thenReturn("Error message!");
    when(syncIssue2.getData()).thenReturn("key");
    when(syncIssue2.getSeverity()).thenReturn(SEVERITY_ERROR);
    when(syncIssue2.getType()).thenReturn(TYPE_DEPENDENCY_INTERNAL_CONFLICT);
    when(syncIssue3.getMessage()).thenReturn("Warning message!");
    when(syncIssue3.getData()).thenReturn("key2");
    when(syncIssue3.getSeverity()).thenReturn(SEVERITY_WARNING);
    when(syncIssue3.getType()).thenReturn(TYPE_BUILD_TOOLS_TOO_LOW);
    when(myStrategy2.getSupportedIssueType()).thenReturn(TYPE_DEPENDENCY_INTERNAL_CONFLICT);

    SyncIssuesReporter reporter = new SyncIssuesReporter(myStrategy1, myStrategy2);

    Module appModule = TestModuleUtil.findAppModule(projectRule.getProject());
    reporter.report(ImmutableMap.of(appModule, ImmutableList.of(mySyncIssue, syncIssue2, syncIssue3)), "/");

    InOrder inOrder = inOrder(myStrategy1, myStrategy2);

    inOrder.verify(myStrategy2).reportAll(eq(ImmutableList.of(syncIssue2)), anyMap(), anyMap());
    inOrder.verify(myStrategy1).reportAll(eq(ImmutableList.of(mySyncIssue, syncIssue3)), anyMap(), anyMap());
  }
}