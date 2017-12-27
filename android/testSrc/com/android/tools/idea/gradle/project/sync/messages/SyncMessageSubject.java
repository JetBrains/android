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


import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

public class SyncMessageSubject extends Subject<SyncMessageSubject, SyncMessage> {
  @NotNull
  public static SubjectFactory<SyncMessageSubject, SyncMessage> syncMessage() {
    return new SubjectFactory<SyncMessageSubject, SyncMessage>() {
      @Override
      public SyncMessageSubject getSubject(FailureStrategy failureStrategy, SyncMessage syncMessage) {
        return new SyncMessageSubject(failureStrategy, syncMessage);
      }
    };
  }

  private SyncMessageSubject(FailureStrategy failureStrategy, @Nullable SyncMessage subject) {
    super(failureStrategy, subject);
  }

  @NotNull
  public SyncMessageSubject hasType(@NotNull MessageType expected) {
    assertThat(getNotNullSubject().getType()).named("type").isEqualTo(expected);
    return this;
  }

  @NotNull
  public SyncMessageSubject hasGroup(@NotNull String expected) {
    assertThat(getNotNullSubject().getGroup()).named("group").isEqualTo(expected);
    return this;
  }

  @NotNull
  public SyncMessageSubject hasMessageLine(@NotNull String text, int index) {
    String[] textLines = getNotNullSubject().getText();
    assertThat(textLines.length).named("message line count").isGreaterThan(index);
    assertThat(textLines[index]).named("message[" + index + "]").isEqualTo(text);
    return this;
  }

  @NotNull
  private SyncMessage getNotNullSubject() {
    SyncMessage syncMessage = super.getSubject();
    assertNotNull(syncMessage);
    return syncMessage;
  }
}
