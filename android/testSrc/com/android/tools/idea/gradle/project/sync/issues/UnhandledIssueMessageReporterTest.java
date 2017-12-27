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
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import static com.android.builder.model.SyncIssue.SEVERITY_ERROR;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link UnhandledIssuesReporter}.
 */
public class UnhandledIssueMessageReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private GradleSyncMessagesStub mySyncMessagesStub;
  private UnhandledIssuesReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
    myReporter = new UnhandledIssuesReporter();
  }

  public void testGetSupportedIssueType() {
    assertEquals(-1, myReporter.getSupportedIssueType());
  }

  public void testReportWithBuildFile() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    String expectedText = "Hello World!";
    when(mySyncIssue.getMessage()).thenReturn(expectedText);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    VirtualFile buildFile = getGradleBuildFile(appModule);
    myReporter.report(mySyncIssue, appModule, buildFile);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(1);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasType(ERROR)
                                            .hasMessageLine(expectedText, 0);
    // @formatter:on

    PositionInFile position = message.getPosition();
    assertNotNull(position);
    assertSame(buildFile, position.file);
  }

  public void testReportWithoutBuildFile() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    String expectedText = "Hello World!";
    when(mySyncIssue.getMessage()).thenReturn(expectedText);
    when(mySyncIssue.getSeverity()).thenReturn(SEVERITY_ERROR);

    myReporter.report(mySyncIssue, appModule, null);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(1);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasType(ERROR)
                                            .hasMessageLine(expectedText, 0);
    // @formatter:on

    PositionInFile position = message.getPosition();
    assertNull(position);
  }
}