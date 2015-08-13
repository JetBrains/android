/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.invoker;

import com.android.ide.common.blame.Message;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GradleInvocationResult {
  @NotNull private final List<String> myTasks;
  @NotNull private final ListMultimap<Message.Kind, Message> myCompilerMessagesByKind = ArrayListMultimap.create();
  private final boolean myBuildSuccessful;

  GradleInvocationResult(@NotNull List<String> tasks, @NotNull List<Message> compilerMessages, boolean buildSuccessful) {
    myTasks = tasks;
    myBuildSuccessful = buildSuccessful;
    for (Message msg : compilerMessages) {
      myCompilerMessagesByKind.put(msg.getKind(), msg);
    }
  }

  @NotNull
  public List<String> getTasks() {
    return myTasks;
  }

  @NotNull
  public List<Message> getCompilerMessages(@NotNull Message.Kind kind) {
    return myCompilerMessagesByKind.get(kind);
  }

  public boolean isBuildSuccessful() {
    return myBuildSuccessful;
  }
}
