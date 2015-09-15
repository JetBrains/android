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
package com.android.tools.idea.profiling.capture;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A package-private delegate to perform the actual write operation synchronously on a separate thread.
 */
class AsyncWriterDelegate implements Runnable {
  private static final Logger LOG = Logger.getInstance(AsyncWriterDelegate.class);

  @NotNull private BlockingQueue<Message> myWriteQueue = new LinkedBlockingDeque<Message>();
  private boolean myShouldContinue = true;

  private static abstract class Message {
    protected abstract void performMessageRequest();
  }

  private class ExitMessage extends Message {
    private ExitMessage() {
      super();
    }

    @Override
    protected void performMessageRequest() {
      myShouldContinue = false;
    }
  }

  private static class WriteMessage extends Message {
    @NotNull private CaptureHandle myCaptureHandle;
    @NotNull private byte[] myData;

    private WriteMessage(@NotNull CaptureHandle captureHandle, @NotNull byte[] data) {
      super();
      myCaptureHandle = captureHandle;
      myData = data;
    }

    @Override
    protected void performMessageRequest() {
      try {
        CaptureService.appendDataSynchronous(myCaptureHandle, myData);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private static class FinalizeMessage extends Message {
    @NotNull private CaptureHandle myCaptureHandle;
    @Nullable private Runnable myTask;

    private FinalizeMessage(@NotNull CaptureHandle captureHandle, @Nullable Runnable task) {
      super();
      myCaptureHandle = captureHandle;
      myTask = task;
    }

    @Override
    protected void performMessageRequest() {
      myCaptureHandle.closeFileOutputStream();
      if (myTask != null) {
        myTask.run();
      }
    }
  }

  void queueWrite(@NotNull CaptureHandle captureHandle, @NotNull byte[] data) throws InterruptedException {
    myWriteQueue.put(new WriteMessage(captureHandle, data));
  }

  void closeFileAndRunTaskAsynchronously(@NotNull CaptureHandle captureHandle, @Nullable Runnable task) throws InterruptedException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myWriteQueue.put(new FinalizeMessage(captureHandle, task));
  }

  void queueExit() throws InterruptedException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myWriteQueue.put(new ExitMessage());
  }

  @Override
  public void run() {
    try {
      //noinspection WhileLoopSpinsOnField
      while (myShouldContinue) { // myShouldContinue is set on the same thread as the one running this loop.
        Message message = myWriteQueue.take();
        message.performMessageRequest();
      }

      if (!myWriteQueue.isEmpty()) {
        LOG.warn("AsyncWriterDelegate was stopped before all messages in flight were handled.");
      }
    }
    catch (InterruptedException ignored) {
    }

    myWriteQueue.clear();
  }
}
