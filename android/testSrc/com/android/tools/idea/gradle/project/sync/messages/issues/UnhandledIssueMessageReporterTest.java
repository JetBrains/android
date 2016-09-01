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
import com.android.tools.idea.gradle.project.sync.messages.MessageType;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessageReporterStub;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UnhandledIssueMessageReporter}.
 */
public class UnhandledIssueMessageReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private SyncMessageReporterStub myReporterStub;
  private UnhandledIssueMessageReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    myReporterStub = new SyncMessageReporterStub(getProject());
    myReporter = new UnhandledIssueMessageReporter(myReporterStub);
  }

  public void testGetSupportedIssueType() {
    assertEquals(-1, myReporter.getSupportedIssueType());
  }

  public void testReportWithBuildFile() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    String expectedText = "Hello World!";
    when(mySyncIssue.getMessage()).thenReturn(expectedText);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    VirtualFile buildFile = getGradleBuildFile(appModule);
    myReporter.report(mySyncIssue, appModule, buildFile);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    assertEquals(MessageType.ERROR, message.getType());

    String[] text = message.getText();
    assertThat(text).hasLength(1);
    assertEquals(expectedText, text[0]);

    PositionInFile position = message.getPosition();
    assertNotNull(position);
    assertSame(buildFile, position.file);
  }

  public void testReportWithoutBuildFile() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    String expectedText = "Hello World!";
    when(mySyncIssue.getMessage()).thenReturn(expectedText);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    assertEquals(MessageType.ERROR, message.getType());

    String[] text = message.getText();
    assertThat(text).hasLength(1);
    assertEquals(expectedText, text[0]);

    PositionInFile position = message.getPosition();
    assertNull(position);
  }
}