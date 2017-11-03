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

import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.project.sync.hyperlink.ShowSyncIssuesDetailsHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link UnresolvedDependenciesReporter}.
 */
public class UnresolvedDependenciesReporterTest extends IdeaTestCase {
  @Mock private SyncIssue mySyncIssue;

  private GradleSyncMessagesStub mySyncMessages;

  private UnresolvedDependenciesReporter myReporter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySyncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
    myReporter = new UnresolvedDependenciesReporter();
  }

  public void testReportWithoutDependencyAndExtraInfo() {
    String text = "Hello!";
    when(mySyncIssue.getMessage()).thenReturn(text);

    List<String> extraInfo = Arrays.asList("line1", "line2");
    when(mySyncIssue.getMultiLineMessage()).thenReturn(extraInfo);

    myReporter.report(mySyncIssue, getModule(), null);

    SyncMessage message = mySyncMessages.getFirstReportedMessage();
    assertEquals(text, message.getText()[0]);

    List<NotificationHyperlink> quickFixes = message.getQuickFixes();
    assertThat(quickFixes).hasSize(1);

    NotificationHyperlink quickFix = quickFixes.get(0);
    assertThat(quickFix).isInstanceOf(ShowSyncIssuesDetailsHyperlink.class);

    ShowSyncIssuesDetailsHyperlink showDetailsQuickFix = (ShowSyncIssuesDetailsHyperlink)quickFix;
    assertEquals(text, showDetailsQuickFix.getMessage());
    assertEquals(extraInfo, showDetailsQuickFix.getDetails());
  }
}