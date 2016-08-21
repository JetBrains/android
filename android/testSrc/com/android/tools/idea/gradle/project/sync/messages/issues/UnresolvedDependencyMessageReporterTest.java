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
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessageReporterStub;
import com.android.tools.idea.gradle.service.notification.hyperlink.InstallArtifactHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.InstallRepositoryHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.ShowDependencyInProjectStructureHyperlink;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_UNRESOLVED_DEPENDENCY;
import static com.android.ide.common.repository.SdkMavenRepository.ANDROID;
import static com.android.ide.common.repository.SdkMavenRepository.GOOGLE;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UnresolvedDependencyMessageReporter}.
 */
public class UnresolvedDependencyMessageReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private SyncMessageReporterStub myReporterStub;
  private UnresolvedDependencyMessageReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    myReporterStub = new SyncMessageReporterStub(getProject());
    myReporter = new UnresolvedDependencyMessageReporter(myReporterStub);
  }

  public void testGetSupportedIssueType() {
    assertEquals(TYPE_UNRESOLVED_DEPENDENCY, myReporter.getSupportedIssueType());
  }

  public void testReportWithRegularJavaLibrary() throws Exception {
    loadProject("projects/transitiveDependencies");

    when(mySyncIssue.getData()).thenReturn("com.google.guava:guava:19.0");

    Module appModule = myModules.getAppModule();
    VirtualFile buildFile = getGradleBuildFile(appModule);
    myReporter.report(mySyncIssue, appModule, buildFile);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    assertEquals("Unresolved dependencies", message.getGroup());

    String[] text = message.getText();
    assertThat(text).hasLength(1);
    assertEquals("Failed to resolve: com.google.guava:guava:19.0", text[0]);

    assertThat(message.getNavigatable()).isInstanceOf(OpenFileDescriptor.class);
    OpenFileDescriptor navigatable = (OpenFileDescriptor)message.getNavigatable();
    assertEquals(buildFile, navigatable.getFile());

    PositionInFile position = message.getPosition();
    assertNotNull(position);
    assertSame(buildFile, position.file);
  }

  public void testReportWithConstraintLayout() throws Exception {
    loadProject("projects/transitiveDependencies");
    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.android.support.constraint:constraint-layout:+");

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    assertEquals("Unresolved Android dependencies", message.getGroup());
    assertEquals("Failed to resolve: com.android.support.constraint:constraint-layout:+", message.getText()[0]);

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    int expectedSize = isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallArtifactHyperlink.class);

    if (isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }

  public void testReportWithAppCompat() throws Exception {
    loadProject("projects/transitiveDependencies");
    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.android.support:appcompat-v7:24.1.1");

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    assertEquals("Unresolved Android dependencies", message.getGroup());
    assertEquals("Failed to resolve: com.android.support:appcompat-v7:24.1.1", message.getText()[0]);

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    int expectedSize = isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallRepositoryHyperlink.class);

    InstallRepositoryHyperlink hyperlink = (InstallRepositoryHyperlink)quickFix;
    assertSame(ANDROID, hyperlink.getRepository());

    if (isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }

  public void testReportWithPlayServices() throws Exception {
    loadProject("projects/transitiveDependencies");
    Module appModule = myModules.getAppModule();

    when(mySyncIssue.getData()).thenReturn("com.google.android.gms:play-services:9.4.0");

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    assertEquals("Unresolved Android dependencies", message.getGroup());
    assertEquals("Failed to resolve: com.google.android.gms:play-services:9.4.0", message.getText()[0]);

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    int expectedSize = isAndroidStudio() ? 2 : 1;
    assertThat(quickFixes).hasSize(expectedSize);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(InstallRepositoryHyperlink.class);

    InstallRepositoryHyperlink hyperlink = (InstallRepositoryHyperlink)quickFix;
    assertSame(GOOGLE, hyperlink.getRepository());

    if (isAndroidStudio()) {
      quickFix = quickFixes.get(1);
      assertThat(quickFix).isInstanceOf(ShowDependencyInProjectStructureHyperlink.class);
    }
  }
}