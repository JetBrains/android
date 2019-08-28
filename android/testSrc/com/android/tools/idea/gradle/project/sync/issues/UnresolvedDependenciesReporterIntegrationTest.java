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
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallRepositoryHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowDependencyInProjectStructureHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.ide.common.repository.SdkMavenRepository.GOOGLE;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
public class UnresolvedDependenciesReporterIntegrationTest extends AndroidGradleTestCase {
  private IdeComponents myIdeComponents;
  private SyncIssue mySyncIssue;
  private GradleSyncMessagesStub mySyncMessagesStub;
  private UnresolvedDependenciesReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    // getMessage() is NotNull but message is unused for dependencies.
    when(mySyncIssue.getMessage()).thenReturn("");
    myIdeComponents = new IdeComponents(getProject());
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
    myReporter = new UnresolvedDependenciesReporter();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents = null;
      mySyncMessagesStub = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetSupportedIssueType() {
    assertEquals(TYPE_UNRESOLVED_DEPENDENCY, myReporter.getSupportedIssueType());
  }

  public void testReportWithRegularJavaLibrary() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    when(mySyncIssue.getData()).thenReturn("com.google.guava:guava:19.0");

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    myReporter.report(mySyncIssue, appModule, buildFile);

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
  }

  public void testReportWithConstraintLayout() throws Exception {
    IdeInfo ideInfo = myIdeComponents.mockApplicationService(IdeInfo.class);
    when(ideInfo.isAndroidStudio()).thenReturn(true);

    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.android.support.constraint:constraint-layout:+");

    myReporter.report(mySyncIssue, appModule, null);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertEquals("Failed to resolve: com.android.support.constraint:constraint-layout:+\n" +
                 "Affected Modules: app",
                 message.getMessage());

    List<NotificationHyperlink> quickFixes = mySyncMessagesStub.getNotificationUpdate().getFixes();
    assertNotEmpty(quickFixes);
  }

  public void testReportWithAppCompat() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.report(mySyncIssue, appModule, getGradleBuildFile(appModule));

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
  }

  public void testReportWithAppCompatAndGoogle() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();
    // Add Google repository
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    Project project = getProject();
    buildModel.repositories().addGoogleMavenRepository(project);
    runWriteCommandAction(project, buildModel::applyChanges);

    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.report(mySyncIssue, appModule, null);

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
  }

  /**
   * Verify that an {@link AddGoogleMavenRepositoryHyperlink} is generated when the project is not initialized
   *
   * @throws Exception
   */
  public void testReportNotInitialized() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();
    Module spyAppModule = spy(appModule);
    Project spyProject = spy(spyAppModule.getProject());
    when(spyAppModule.getProject()).thenReturn(spyProject);
    when(spyProject.isInitialized()).thenReturn(false);

    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.report(mySyncIssue, spyAppModule, null);

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
  }

  public void testReportWithPlayServices() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.google.android.gms:play-services:9.4.0");

    myReporter.report(mySyncIssue, appModule, null);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertEquals("Failed to resolve: com.google.android.gms:play-services:9.4.0\nAffected Modules: app", message.getMessage());

    List<NotificationHyperlink> quickFixes = mySyncMessagesStub.getNotificationUpdate().getFixes();
    int expectedSize = IdeInfo.getInstance().isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallRepositoryHyperlink.class);

    InstallRepositoryHyperlink hyperlink = (InstallRepositoryHyperlink)quickFix;
    assertSame(GOOGLE, hyperlink.getRepository());
    assertEquals("com.google.android.gms:play-services:9.4.0", hyperlink.getDependency());

    if (IdeInfo.getInstance().isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }

  public void testDeduplicateAcrossModules() throws Exception {
    loadProject(DEPENDENT_MODULES);
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();
    Module libModule = myModules.getModule("lib");

    List<SyncIssue> issues = ImmutableList.of(1, 2).stream().map((i) -> new SyncIssue() {
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
    }).collect(Collectors.toList());

    IdentityHashMap<SyncIssue, Module> moduleMap = new IdentityHashMap<>();
    moduleMap.put(issues.get(0), appModule);
    moduleMap.put(issues.get(1), libModule);
    myReporter
      .reportAll(issues, moduleMap, ImmutableMap.of(appModule, getGradleBuildFile(appModule), libModule, getGradleBuildFile(libModule)));

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    NotificationData message = messages.get(0);
    assertEquals("Unresolved dependencies", message.getTitle());
    assertThat(message.getMessage()).contains("Failed to resolve: com.google.android.gms.play-services:9.4.0\nAffected Modules:");
  }

  public void testGetModuleLink() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();
    VirtualFile appFile = getGradleBuildFile(appModule);

    Project project = getProject();
    ProjectBuildModel buildModel = ProjectBuildModel.get(project);

    when(mySyncIssue.getData()).thenReturn("com.google.guava:guava:19.0");

    List<SyncIssue> syncIssues = ImmutableList.of(mySyncIssue);
    OpenFileHyperlink link = myReporter.createModuleLink(getProject(), appModule, buildModel, syncIssues, appFile);
    assertThat(link.getLineNumber()).isEqualTo(28);
    assertThat(link.getFilePath()).isEqualTo(appFile.getPath());
  }

  public void testAndroidXGoogleHyperlink() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();
    VirtualFile appFile = getGradleBuildFile(appModule);

    when(mySyncIssue.getData()).thenReturn("androidx.room:room-compiler:2.0.0-alpha1");

    myReporter.report(mySyncIssue, appModule, appFile);

    List<NotificationData> messages = mySyncMessagesStub.getNotifications();
    assertSize(1, messages);

    List<NotificationHyperlink> links = mySyncMessagesStub.getNotificationUpdate().getFixes();
    boolean studio = IdeInfo.getInstance().isAndroidStudio();
    assertSize(studio ? 2 : 1, links);
    assertThat(links.get(0)).isInstanceOf(AddGoogleMavenRepositoryHyperlink.class);
    if (studio) {
      assertThat(links.get(1)).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }
}
