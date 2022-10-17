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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.android.ide.common.blame.Message;
import com.android.tools.idea.project.messages.MessageType;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import org.junit.Test;

/**
 * Tests for {@link MessageType}.
 */
public class MessageTypeTest {
  @Test
  public void findByNameWithValidName() {
    assertSame(MessageType.ERROR, MessageType.findByName("ERROR"));
    assertSame(MessageType.WARNING, MessageType.findByName("WARNING"));
    assertSame(MessageType.INFO, MessageType.findByName("INFO"));
    assertSame(MessageType.SIMPLE, MessageType.findByName("SIMPLE"));
  }

  @Test
  public void findByNameWithInvalidName() {
    assertNull(MessageType.findByName("Hello"));
  }

  @Test
  public void findMatchingWithValidKind() {
    assertSame(MessageType.ERROR, MessageType.findMatching(Message.Kind.ERROR));
    assertSame(MessageType.WARNING, MessageType.findMatching(Message.Kind.WARNING));
    assertSame(MessageType.INFO, MessageType.findMatching(Message.Kind.INFO));
    assertSame(MessageType.SIMPLE, MessageType.findMatching(Message.Kind.SIMPLE));
  }

  @Test
  public void findMatchingWithInvalidKind() {
    assertSame(MessageType.INFO, MessageType.findMatching(Message.Kind.STATISTICS));
    assertSame(MessageType.INFO, MessageType.findMatching(Message.Kind.UNKNOWN));
  }

  @Test
  public void convertToCategory() {
    assertSame(NotificationCategory.ERROR, MessageType.ERROR.convertToCategory());
    assertSame(NotificationCategory.WARNING, MessageType.WARNING.convertToCategory());
    assertSame(NotificationCategory.INFO, MessageType.INFO.convertToCategory());
    assertSame(NotificationCategory.SIMPLE, MessageType.SIMPLE.convertToCategory());
  }
}