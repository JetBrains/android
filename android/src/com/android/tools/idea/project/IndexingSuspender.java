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
package com.android.tools.idea.project;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;


/**
 * Dumb mode task which can suspend the dumb queue execution until notified.
 *
 * The code which wants to avoid IDE indexing to be executed concurrently, has to
 * follow these steps:
 *
 * (1) queue this task up via DumbService.queueTask()
 * (2) proceed with excuting the logic which is supposed to be indexing-free
 * (3) call notifyAll() on the passed in lock once done
 *
 * No action is taken on the indexing which is already in progress. The calling code
 * has to take care about it depending on the context it is executing in, for example
 * by calling DumbService.waitForSmartMode() or DumbService.runWhenSmart().
 */
public class IndexingSuspender extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance(GradleBuildState.class);

  @NotNull private final Object myIndexingLock;
  @NotNull private final String myContextDescription;
  @NotNull private final Supplier<Boolean> myShouldWait;
  private final int myWaitTimeoutMillis;

  public static void queue(@NotNull Project project,
                           @NotNull String contextDescription,
                           @NotNull Object indexingLock,
                           @NotNull Supplier<Boolean> shouldWait,
                           int waitTimeoutMillis) {
    DumbService.getInstance(project).queueTask(
      new IndexingSuspender(contextDescription, indexingLock, shouldWait, waitTimeoutMillis)
    );
  }

  private IndexingSuspender(@NotNull String contextDescription,
                            @NotNull Object indexingLock,
                            @NotNull Supplier<Boolean> shouldWait,
                            int waitTimeoutMillis) {
    myContextDescription = contextDescription;
    myIndexingLock = indexingLock;
    myShouldWait = shouldWait;
    myWaitTimeoutMillis = waitTimeoutMillis;
  }

  @Override
  public void performInDumbMode(@NotNull ProgressIndicator indicator) {
    String message = String.format("Indexing suspended (context: %1$s)", myContextDescription);
    LOG.info(message);
    indicator.setText(message);
    synchronized (myIndexingLock) {
      while (myShouldWait.get()) {
        try {
          myIndexingLock.wait(myWaitTimeoutMillis);
        }
        catch (InterruptedException ignored) {
        }
      }
    }
    LOG.info(String.format("Indexing released (context: %1$s)", myContextDescription));
  }
}
