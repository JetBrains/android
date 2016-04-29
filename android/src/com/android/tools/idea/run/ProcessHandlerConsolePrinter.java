/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.annotations.concurrency.GuardedBy;
import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A simple logger which outputs to a process handler. If a process handler is not initially present, stores output until one is set.
 * Repeatedly setting the process handler will update the handler that messages are printed to, but stored messages are printed only once.
 */
public final class ProcessHandlerConsolePrinter implements ConsolePrinter {

  private static class Message {
    @NotNull final String text;
    @NotNull final Key outputType;

    Message(@NotNull String text, @NotNull Key outputType) {
      this.text = text;
      this.outputType = outputType;
    }
  }

  @NotNull private final Object myLock = new Object();

  @GuardedBy("myLock")
  @NotNull private final List<Message> myStoredMessages = Lists.newArrayList();
  @GuardedBy("myLock")
  @Nullable private ProcessHandler myProcessHandler;

  public ProcessHandlerConsolePrinter(@Nullable ProcessHandler processHandler) {
    myProcessHandler = processHandler;
  }

  @Override
  public void stdout(@NotNull String text) {
    print(text, ProcessOutputTypes.STDOUT);
  }

  @Override
  public void stderr(@NotNull String text) {
    print(text, ProcessOutputTypes.STDERR);
  }

  public void setProcessHandler(@NotNull ProcessHandler processHandler) {
    final List<Message> storedMessages;
    synchronized (myLock) {
      myProcessHandler = processHandler;
      storedMessages = Lists.newArrayList(myStoredMessages);
      myStoredMessages.clear();
    }
    for (Message message : storedMessages) {
      print(message.text, message.outputType);
    }
  }

  private void print(@NotNull String text, @NotNull Key outputType) {
    @NotNull final ProcessHandler processHandler;
    synchronized (myLock) {
      if (myProcessHandler == null) {
        myStoredMessages.add(new Message(text, outputType));
        return;
      } else {
        processHandler = myProcessHandler;
      }
    }
    // We DO NOT call notifyTextAvailable under a lock, because it could execute arbitrary code
    // and opens up the (remote) possibility of deadlock.
    assert !Thread.holdsLock(myLock);
    processHandler.notifyTextAvailable(text + '\n', outputType);
  }
}
