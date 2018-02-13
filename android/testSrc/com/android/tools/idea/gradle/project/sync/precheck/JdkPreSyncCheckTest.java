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

import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JdkPreSyncCheck}.
 */
public class JdkPreSyncCheckTest extends AndroidGradleTestCase {
  private IdeComponents myIdeComponents;
  private IdeSdks myMockIdeSdks;

  private JdkPreSyncCheck myJdkPreSyncCheck;
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myIdeComponents = new IdeComponents(getProject());

    loadSimpleApplication();

    myMockIdeSdks = myIdeComponents.mockService(IdeSdks.class);
    assertSame(myMockIdeSdks, IdeSdks.getInstance());

    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    myJdkPreSyncCheck = new JdkPreSyncCheck();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testDoCheckCanSyncWithNullJdk() throws Exception {
    when(myMockIdeSdks.getJdk()).thenReturn(null);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSync(getProject());
    verifyCheckFailure(result);
  }

  public void testDoCheckWithJdkWithoutHomePath() throws Exception {
    Sdk jdk = mock(Sdk.class);

    when(myMockIdeSdks.getJdk()).thenReturn(jdk);
    when(jdk.getHomePath()).thenReturn(null);

    PreSyncCheckResult result = myJdkPreSyncCheck.doCheckCanSync(getProject());
    verifyCheckFailure(result);
  }

  private void verifyCheckFailure(@NotNull PreSyncCheckResult result) {
    assertFalse(result.isSuccess());

    String expectedText = "Could not run JVM from the selected JDK.\n" +
                          "Please ensure JDK installation is valid and compatible with the current OS ";
    assertThat(result.getFailureCause()).startsWith(expectedText);

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(1);
    assertEquals(SyncMessage.DEFAULT_GROUP, message.getGroup());

    assertAbout(syncMessage()).that(message).hasMessageLineStartingWith(expectedText, 0);
  }
}