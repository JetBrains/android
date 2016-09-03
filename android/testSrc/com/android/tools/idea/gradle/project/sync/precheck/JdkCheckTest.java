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
package com.android.tools.idea.gradle.project.sync.precheck;

import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessageReporterStub;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessages;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JdkCheck}.
 */
public class JdkCheckTest extends AndroidGradleTestCase {
  private IdeSdks myIdeSdks;
  private Project myProject;
  private SyncMessageReporterStub myMessageReporterStub;

  private JdkCheck myJdkCheck;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    myIdeSdks = mock(IdeSdks.class);
    myProject = mock(Project.class);
    myMessageReporterStub = new SyncMessageReporterStub(myProject);

    SyncMessagesStub syncMessages = new SyncMessagesStub(myProject, myMessageReporterStub);

    myJdkCheck = new JdkCheck(myIdeSdks) {
      @Override
      @NotNull
      SyncMessages getSyncMessages(@NotNull Project project) {
        return syncMessages;
      }
    };
  }

  public void testDoCheckCanSyncWithNullJdk() {
    when(myIdeSdks.getJdk()).thenReturn(null);

    PreSyncCheckResult result = myJdkCheck.doCheckCanSync(myProject);
    verifyCheckFailure(result);
  }

  public void testDoCheckWithJdkWithoutHomePath() {
    Sdk jdk = mock(Sdk.class);

    when(myIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn(null);

    PreSyncCheckResult result = myJdkCheck.doCheckCanSync(myProject);
    verifyCheckFailure(result);
  }

  private void verifyCheckFailure(@NotNull PreSyncCheckResult result) {
    assertFalse(result.isSuccess());

    String expectedMsg = "Please use JDK 8 or newer.";
    assertEquals(expectedMsg, result.getFailureCause());

    SyncMessage message = myMessageReporterStub.getReportedMessage();
    assertNotNull(message);

    String[] actual = message.getText();
    assertThat(actual).hasLength(1);

    assertEquals(expectedMsg, actual[0]);
  }

  private static class SyncMessagesStub extends SyncMessages {
    public SyncMessagesStub(@NotNull Project project, @NotNull SyncMessageReporterStub messageReporter) {
      super(project, mock(ExternalSystemNotificationManager.class), messageReporter);
    }
  }
}