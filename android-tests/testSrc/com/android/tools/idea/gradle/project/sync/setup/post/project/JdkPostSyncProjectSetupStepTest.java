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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.mockito.Mock;

import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessage.DEFAULT_GROUP;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link JdkPostSyncProjectSetupStep}.
 */
public class JdkPostSyncProjectSetupStepTest extends AndroidGradleTestCase {
  @Mock private IdeSdks myIdeSdks;
  @Mock private Jdks myJdks;
  private SyncMessagesStub mySyncMessages;

  private JdkPostSyncProjectSetupStep mySetupStep;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySetupStep = new JdkPostSyncProjectSetupStep(myIdeSdks, myJdks);

    mySyncMessages = SyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testDoSetUpProjectWithAndroidStudio() {
    Sdk jdk = mock(Sdk.class);
    when(jdk.getHomePath()).thenReturn("somePath");

    when(myIdeSdks.getJdk()).thenReturn(jdk);

    Project project = getProject();
    mySetupStep.doSetUpProject(project, true /* Android Studio */);

    verify(myJdks, times(1)).setJdk(project, jdk);
  }

  public void testDoSetUpProjectWithAndroidStudioAndNoJdk() {
    when(myIdeSdks.getJdk()).thenReturn(null);

    Project project = getProject();
    mySetupStep.doSetUpProject(project, true /* Android Studio */);

    SyncMessage message = mySyncMessages.getFirstReportedMessage();
    assertNotNull(message);

    // @formatter:off
    assertAbout(SyncMessageSubject.syncMessage()).that(message).hasMessageLine("Unable to find a JDK", 0)
                                                               .hasType(ERROR)
                                                               .hasGroup(DEFAULT_GROUP);
    // @formatter:on

    assertTrue(GradleSyncState.getInstance(project).getSummary().hasSyncErrors());
  }

  public void testDoSetUpProjectWithIdea() {
    Sdk jdk = mock(Sdk.class);
    when(jdk.getHomePath()).thenReturn("somePath");

    when(myJdks.isApplicableJdk(jdk, JDK_1_8)).thenReturn(false);
    when(myJdks.chooseOrCreateJavaSdk(JDK_1_8)).thenReturn(jdk);

    Project project = getProject();
    mySetupStep.doSetUpProject(project, false /* IDEA */);

    verify(myJdks, times(1)).setJdk(project, jdk);
  }
}