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

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;

import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.testFramework.HeavyPlatformTestCase;

/**
 * Tests for {@link GradleSyncMessages}.
 */
public class GradleSyncMessagesTest extends HeavyPlatformTestCase {
  private GradleSyncMessages mySyncMessages;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySyncMessages = new GradleSyncMessages(getProject());
  }

  public void testGetProjectSystemId() {
    assertSame(GRADLE_SYSTEM_ID, mySyncMessages.getProjectSystemId());
  }

  public void testRemoveProjectMessages() {
    mySyncMessages.report(new SyncMessage("Project Structure Issues", MessageType.ERROR, "Message for test PSI"));
    mySyncMessages.report(new SyncMessage("Missing Dependencies", MessageType.ERROR, "Message for test MD"));
    mySyncMessages.report(new SyncMessage("Variant Selection Conflicts", MessageType.ERROR, "Message for test VSC"));
    mySyncMessages.report(new SyncMessage("Generated Sources", MessageType.ERROR, "Message for test GS"));
    mySyncMessages.report(new SyncMessage("Gradle Sync Issues", MessageType.ERROR, "Message for test GSI"));
    mySyncMessages.report(new SyncMessage("Version Compatibility Issues", MessageType.ERROR, "Message for test VCI"));

    assertFalse(mySyncMessages.isEmpty());
    mySyncMessages.removeAllMessages();
    assertTrue(mySyncMessages.isEmpty());
  }
}