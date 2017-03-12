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

import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.project.messages.MessageType.INFO;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link MissingPlatformsSetupStep}.
 */
public class MissingPlatformsSetupStepTest extends IdeaTestCase {
  private MySyncMessages mySyncMessages;
  private MissingPlatformsSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    mySyncMessages = new MySyncMessages(project);
    IdeComponents.replaceService(project, GradleSyncMessages.class, mySyncMessages);

    mySetupStep = new MissingPlatformsSetupStep();
  }

  public void testSetUpProjectWithMissingPlatforms() {
    mySyncMessages.setMessageCount("SDK Setup Issues", 1);

    mySetupStep.setUpProject(getProject(), null);

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).hasSize(1);

    SyncMessage message = messages.get(0);
    // @formatter:off
    assertAbout(syncMessage()).that(message).hasMessageLine("Open Android SDK Manager and install all missing platforms.", 0)
                                            .hasType(INFO);
    // @formatter:on
  }

  public void testSetUpProjectWithoutMissingPlatforms() {
    mySyncMessages.setMessageCount("SDK Setup Issues", 0);

    mySetupStep.setUpProject(getProject(), null);

    List<SyncMessage> messages = mySyncMessages.getReportedMessages();
    assertThat(messages).isEmpty();
  }

  public void testInvokeOnFailedSync() {
    assertTrue(mySetupStep.invokeOnFailedSync());
  }

  private static class MySyncMessages extends GradleSyncMessagesStub {
    private Map<String, Integer> myMessageCountByGroup = new HashMap<>();

    public MySyncMessages(@NotNull Project project) {
      super(project);
    }

    void setMessageCount(@NotNull String groupName, int count) {
      myMessageCountByGroup.put(groupName, count);
    }

    @Override
    public int getMessageCount(@NotNull String groupName) {
      Integer count = myMessageCountByGroup.get(groupName);
      return count != null ? count : 0;
    }
  }
}