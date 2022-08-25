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

import static org.junit.Assert.assertSame;

import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleSyncMessagesStub extends GradleSyncMessages {
  @NotNull private final List<SyncMessage> myMessages = new ArrayList<>();

  @NotNull
  public static GradleSyncMessagesStub replaceSyncMessagesService(@NotNull Project project) {
    GradleSyncMessagesStub syncMessages = new GradleSyncMessagesStub(project);
    new IdeComponents(project).replaceProjectService(GradleSyncMessages.class, syncMessages);
    assertSame(syncMessages, GradleSyncMessages.getInstance(project));
    return syncMessages;
  }

  public GradleSyncMessagesStub(@NotNull Project project) {
    super(project);
    Disposer.register(project, this);
  }

  @Override
  public void report(@NotNull SyncMessage message) {
    myMessages.add(message);
    super.report(message);
  }

  @Nullable
  public SyncMessage getFirstReportedMessage() {
    return myMessages.isEmpty() ? null : myMessages.get(0);
  }

  @NotNull
  public List<SyncMessage> getReportedMessages() {
    return ImmutableList.copyOf(myMessages);
  }

  @Override
  public void removeAllMessages() {
    myMessages.clear();
  }
}
