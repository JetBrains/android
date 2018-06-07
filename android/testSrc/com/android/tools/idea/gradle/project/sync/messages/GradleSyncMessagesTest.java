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
package com.android.tools.idea.gradle.project.sync.messages;

import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.testFramework.IdeaTestCase;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.EXTERNAL_SYSTEM_TASK_ID_KEY;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleSyncMessages}.
 */
public class GradleSyncMessagesTest extends IdeaTestCase {
  private GradleSyncMessages mySyncMessages;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    mySyncMessages = new GradleSyncMessages(getProject());
    ExternalSystemTaskId taskId = ExternalSystemTaskId.create(mySyncMessages.getProjectSystemId(), EXECUTE_TASK, myProject);
    myProject.putUserData(EXTERNAL_SYSTEM_TASK_ID_KEY, taskId);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myProject.putUserData(EXTERNAL_SYSTEM_TASK_ID_KEY, null);
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetProjectSystemId() {
    assertSame(GRADLE_SYSTEM_ID, mySyncMessages.getProjectSystemId());
  }

  public void testRemoveProjectMessages() {
    mySyncMessages.report(new SyncMessage("Test", ERROR, "Message for test"));
    assertFalse(mySyncMessages.isEmpty());
    mySyncMessages.removeProjectMessages();
    assertTrue(mySyncMessages.isEmpty());
  }
}