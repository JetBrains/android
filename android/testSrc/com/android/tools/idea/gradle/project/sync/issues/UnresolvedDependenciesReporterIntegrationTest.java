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

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowDependencyInProjectStructureHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleSyncIssue;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
public class UnresolvedDependenciesReporterIntegrationTest extends AndroidGradleTestCase {
  private IdeSyncIssue mySyncIssue;
  private GradleSyncMessagesStub mySyncMessagesStub;
  private UnresolvedDependenciesReporter myReporter;
  private TestSyncIssueUsageReporter myUsageReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(IdeSyncIssue.class);
    // getMessage() is NotNull but message is unused for dependencies.
    when(mySyncIssue.getMessage()).thenReturn("");
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
    myReporter = new UnresolvedDependenciesReporter();
    myUsageReporter = new TestSyncIssueUsageReporter();
    when(mySyncIssue.getType()).thenReturn(TYPE_UNRESOLVED_DEPENDENCY);
  }

  @Override
  protected void tearDown() throws Exception {
    mySyncMessagesStub = null;
    super.tearDown();
  }

  public void testGetSupportedIssueType() {
    assertEquals(TYPE_UNRESOLVED_DEPENDENCY, myReporter.getSupportedIssueType());
  }

  public void testReportWithRegularJavaLibrary() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    when(mySyncIssue.getData()).thenReturn("com.google.guava:guava:19.0");

    Module appModule = TestModuleUtil.findAppModule(getProject());
    VirtualFile buildFile = getGradleBuildFile(appModule);
    myReporter.report(mySyncIssue, appModule, buildFile, myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertThat(message.getMessage()).contains("Failed to resolve: com.google.guava:guava:19.0\nAffected Modules:");

    assertThat(message.getNavigatable()).isInstanceOf(OpenFileDescriptor.class);
    OpenFileDescriptor navigatable = (OpenFileDescriptor)message.getNavigatable();
    assertEquals(buildFile, navigatable.getFile());

    VirtualFile file = ((OpenFileDescriptor)message.getNavigatable()).getFile();
    assertSame(buildFile, file);

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  public void testReportWithConstraintLayout() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    when(mySyncIssue.getData()).thenReturn("com.android.support.constraint:constraint-layout:+");

    myReporter.report(mySyncIssue, appModule, getGradleBuildFile(appModule), myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertThat(message.getMessage()).contains("Failed to resolve: com.android.support.constraint:constraint-layout:+\nAffected Modules:");

    List<NotificationHyperlink> quickFixes = mySyncMessagesStub.getNotificationUpdate().getFixes();
    assertNotEmpty(quickFixes);

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  public void testReportWithAppCompat() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.report(mySyncIssue, appModule, getGradleBuildFile(appModule), myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertThat(message.getMessage()).contains("Failed to resolve: com.android.support:appcompat-v7:24.1.1\nAffected Modules:");

    List<NotificationHyperlink> quickFixes = mySyncMessagesStub.getNotificationUpdate().getFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
    AddGoogleMavenRepositoryHyperlink addQuickFix = (AddGoogleMavenRepositoryHyperlink)quickFix;
    // Confirm that the repository will be added to project build file (b/68657672)
    assertSize(1, addQuickFix.getBuildFiles());

    if (IdeInfo.getInstance().isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  public void testReportWithAppCompatAndGoogle() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Project project = getProject();
    Module appModule = TestModuleUtil.findAppModule(project);

    // Add Google repository
    GradleBuildModel buildModel = ProjectBuildModel.get(project).getModuleBuildModel(appModule);
    buildModel.repositories().addGoogleMavenRepository(new GradleVersion(4, 0));
    runWriteCommandAction(project, buildModel::applyChanges);

    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.report(mySyncIssue, appModule, null, myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);


    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertEquals("Failed to resolve: com.android.support:appcompat-v7:24.1.1\nAffected Modules: app",
                 message.getMessage());

    GradleSyncMessagesStub.NotificationUpdate update = mySyncMessagesStub.getNotificationUpdate();
    List<NotificationHyperlink> quickFixes = update.getFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 1 : 0;
    assertThat(quickFixes).hasSize(expectedSize);

    if (IdeInfo.getInstance().isAndroidStudio()) {
      assertThat(quickFixes.get(0)).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }

    assertEquals(
      ImmutableList.of(
        maybeAddShowDependencyInProjectStructureHyperLink(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY))
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  /**
   * Verify that an {@link AddGoogleMavenRepositoryHyperlink} is generated when the project is not initialized
   *
   * @throws Exception
   */
  public void testReportNotInitialized() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.assumeProjectNotInitialized(true);
    myReporter.report(mySyncIssue, appModule, null, myUsageReporter);
    myReporter.assumeProjectNotInitialized(false);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertEquals("Failed to resolve: com.android.support:appcompat-v7:24.1.1\nAffected Modules: app", message.getMessage());

    List<NotificationHyperlink> quickFixes = mySyncMessagesStub.getNotificationUpdate().getFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
    AddGoogleMavenRepositoryHyperlink addQuickFix = (AddGoogleMavenRepositoryHyperlink)quickFix;
    // Confirm that the build file was found
    assertSize(1, addQuickFix.getBuildFiles());

    if (IdeInfo.getInstance().isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }

    assertEquals(
      ImmutableList.of(
        maybeAddShowDependencyInProjectStructureHyperLink(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK))
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  public void testReportWithPlayServices() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());

    when(mySyncIssue.getData()).thenReturn("com.google.android.gms:play-services:9.4.0");

    myReporter.report(mySyncIssue, appModule, getGradleBuildFile(appModule), myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertThat(message.getMessage()).contains("Failed to resolve: com.google.android.gms:play-services:9.4.0\nAffected Modules:");

    List<NotificationHyperlink> quickFixes = mySyncMessagesStub.getNotificationUpdate().getFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);

    if (IdeInfo.getInstance().isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }

    assertEquals(
      ImmutableList.of(
        maybeAddShowDependencyInProjectStructureHyperLink(
          GradleSyncIssue
            .newBuilder()
            .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
            .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK))
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  public void testDeduplicateAcrossModules() throws Exception {
    loadProject(DEPENDENT_MODULES);
    mySyncMessagesStub.removeAllMessages();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    Module libModule = TestModuleUtil.findModule(getProject(), "lib");

    List<IdeSyncIssue> issues = ContainerUtil.map(ImmutableList.of(1, 2), (i) -> new IdeSyncIssue() {
      @Override
      public int hashCode() {
        return 7;
      }

      @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
      @Override
      public boolean equals(Object obj) {
        if (obj.hashCode() == hashCode()) {
          return true;
        }
        return false;
      }

      @Override
      public int getSeverity() {
        return SEVERITY_ERROR;
      }

      @Override
      public int getType() {
        return TYPE_UNRESOLVED_DEPENDENCY;
      }

      @Nullable
      @Override
      public String getData() {
        return "com.google.android.gms.play-services:9.4.0";
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
    });

    IdentityHashMap<IdeSyncIssue, Module> moduleMap = new IdentityHashMap<>();
    moduleMap.put(issues.get(0), appModule);
    moduleMap.put(issues.get(1), libModule);
    myReporter
      .reportAll(issues, moduleMap, ImmutableMap.of(appModule, getGradleBuildFile(appModule), libModule, getGradleBuildFile(libModule)), myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertThat(message.getMessage()).contains("Failed to resolve: com.google.android.gms.play-services:9.4.0\nAffected Modules:");

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  public void testGetModuleLink() throws Exception {
    loadSimpleApplication();
    Module appModule = TestModuleUtil.findAppModule(getProject());
    VirtualFile appFile = getGradleBuildFile(appModule);

    when(mySyncIssue.getData()).thenReturn("com.google.guava:guava:19.0");

    List<IdeSyncIssue> syncIssues = ImmutableList.of(mySyncIssue);
    OpenFileHyperlink link = myReporter.createModuleLink(getProject(), appModule, syncIssues, appFile);
    assertThat(link.getLineNumber()).isEqualTo(-1);
    assertThat(link.getFilePath()).isEqualTo(appFile.getPath());
  }

  public void testAndroidXGoogleHyperlink() throws Exception {
    loadSimpleApplication();
    Module appModule = TestModuleUtil.findAppModule(getProject());
    VirtualFile appFile = getGradleBuildFile(appModule);

    when(mySyncIssue.getData()).thenReturn("androidx.room:room-compiler:2.0.0-alpha1");

    myReporter.report(mySyncIssue, appModule, appFile, myUsageReporter);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    List<NotificationHyperlink> links = mySyncMessagesStub.getNotificationUpdate().getFixes();
    boolean studio = IdeInfo.getInstance().isAndroidStudio();
    assertSize(studio ? 2 : 1, links);
    assertThat(links.get(0)).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
    if (studio) {
      assertThat(links.get(1)).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }

    assertEquals(
      ImmutableList.of(
        GradleSyncIssue
          .newBuilder()
          .setType(AndroidStudioEvent.GradleSyncIssueType.TYPE_UNRESOLVED_DEPENDENCY)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.ADD_GOOGLE_MAVEN_REPOSITORY_HYPERLINK)
          .addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK)
          .build()),
      myUsageReporter.getCollectedIssue());
  }

  private static GradleSyncIssue.Builder maybeAddShowDependencyInProjectStructureHyperLink(GradleSyncIssue.Builder builder) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      builder.addOfferedQuickFixes(AndroidStudioEvent.GradleSyncQuickFix.SHOW_DEPENDENCY_IN_PROJECT_STRUCTURE_HYPERLINK);
    }
    return builder;
  }
}
