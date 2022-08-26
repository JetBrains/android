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
package com.android.tools.idea.project.messages;

import static com.intellij.openapi.externalSystem.model.ProjectSystemId.IDE;
import static org.mockito.MockitoAnnotations.initMocks;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link AbstractSyncMessages}.
 */
public class AbstractSyncMessagesTest extends PlatformTestCase {
  private static final String TEST_GROUP = "Test";
  private SyncMessages mySyncMessages;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    initMocks(this);

    mySyncMessages = new SyncMessages(myProject);
  }

  public void testIsEmptyWithMessages() {
    generateMessages(1, MessageType.ERROR);
    assertFalse(mySyncMessages.isEmpty());
  }

  public void testGetErrorDescription() {
    // Verify error description is empty.
    assertEmpty(mySyncMessages.getErrorDescription());

    // Verify error description contains single error.
    mySyncMessages.report(new SyncMessage("Error1", MessageType.ERROR, "Error1 text"));
    assertEquals("Error1", mySyncMessages.getErrorDescription());

    // Verify error description contains multiple errors.
    mySyncMessages.report(new SyncMessage("Error2", MessageType.ERROR, "Error2 text"));
    assertEquals("Error1, Error2", mySyncMessages.getErrorDescription());

    // Verify error description doesn't contain duplicated error group.
    mySyncMessages.report(new SyncMessage("Error2", MessageType.ERROR, "Another Error2 text"));
    assertEquals("Error1, Error2", mySyncMessages.getErrorDescription());

    // Verify error description doesn't contain warnings.
    mySyncMessages.report(new SyncMessage("Warning", MessageType.WARNING, "Warning text"));
    assertEquals("Error1, Error2", mySyncMessages.getErrorDescription());
  }

  public void testIsEmptyWithoutMessages() {
    assertTrue(mySyncMessages.isEmpty());
  }

  public void testRemoveAllMessages() {
    int numErrors = 7;
    int numWarning = 13;
    generateMessages(numWarning, MessageType.WARNING);
    generateMessages(numErrors, MessageType.ERROR);
    assertFalse(mySyncMessages.isEmpty());
    mySyncMessages.removeAllMessages();
    assertTrue(mySyncMessages.isEmpty());
  }

  private void generateMessages(int count, MessageType type) {
    for (int i = 0; i < count; i++) {
      mySyncMessages.report(new SyncMessage("Test", type, "Message for test"));
    }
  }

  private static class SyncMessages extends AbstractSyncMessages {
    SyncMessages(@NotNull Project project) {
      super(project);
    }

    @Override
    @NotNull
    protected ProjectSystemId getProjectSystemId() {
      return IDE;
    }
  }
}