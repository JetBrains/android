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

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The project-level service to prevent IDE indexing from being started until a certain set of conditions is met.
 */
public class IndexingSuspender {
  private static final Logger LOG = Logger.getInstance(IndexingSuspender.class);

  private static final int INDEXING_WAIT_TIMEOUT_MILLIS = 5000;

  @NotNull private final Project myProject;
  @NotNull private final Object myIndexingLock = new Object();

  @NotNull
  public static IndexingSuspender getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, IndexingSuspender.class);
  }

  public IndexingSuspender(@NotNull Project project) {
    myProject = project;
  }

  public void activate(@NotNull String contextDescription, @NotNull Supplier<Boolean> shouldWait) {
    if (!StudioFlags.GRADLE_INVOCATIONS_INDEXING_AWARE.get()) {
      return;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()
        || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      // IDEA DumbService executes dumb mode tasks on the same thread when in unittest/headless
      // mode. This will lead to a deadlock during tests execution, so indexing suspender should
      // be a no-op in this case. Also indexing implementation in unit test mode uses quite a few
      // stubs, so we wouldn't get a real indexing behaviour if even threading model was the same
      // as in production. This also means that the only way to reproduce the real production
      // behaviour on CI is a UI test.
      LOG.info(String.format("Indexing suspension omitted in unittest/headless mode (context: " +
                             "'%1$s')", contextDescription));
      return;
    }

    if (!myProject.isInitialized()) {
      LOG.error("Attempt to suspend indexing when project is not yet initialised. Ignoring.");
      return;
    }

    DumbService.getInstance(myProject).queueTask(new IndexingSuspenderTask(contextDescription, shouldWait));
    synchronized (myIndexingLock) {
      myIndexingLock.notifyAll();
    }
  }

  /**
   * Call this method to signify that indexing can possibly be unlocked now. Typically this is to be called
   * from a context which has just changed the condition for the predicate passed previously to the activate()
   * method. Note, that this will not guarantee immediate indexing resuming since there may be other contexts which
   * have called activate() by this point. Indexing will only be resumed if there are no predicates indicating
   * that waiting should be performed.
   *
   * Note also that calling this method is not mandatory because in the current implementation wait() is limited
   * by a timeout and therefore it's sufficient for the predicates to change their states.
   * However, this design is mainly in place to minimize the risk of dead-lock bugs. For the sake of performance,
   * it is advisable to call this method on each indexing-free latch completion so that the service didn't have
   * to wait for timeout to pass in order to figure that out.
   *
   * If IndexingSuspender is not currently activated, this call will be a no-op.
   */
  public void deactivateIfPossible() {
    if (ApplicationManager.getApplication().isUnitTestMode()
        || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    synchronized (myIndexingLock) {
      myIndexingLock.notifyAll();
    }
  }


  /**
   * Dumb mode task which can suspend the dumb queue execution until notified and the given predicate returns true.
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
  private class IndexingSuspenderTask extends DumbModeTask {
    @NotNull private final String myContextDescription;
    @NotNull private final Supplier<Boolean> myShouldWait;

    IndexingSuspenderTask(@NotNull String contextDescription, @NotNull Supplier<Boolean> shouldWait) {
      myContextDescription = contextDescription;
      myShouldWait = shouldWait;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      String message = String.format("Indexing suspended (context: %1$s)", myContextDescription);
      LOG.info(message);
      indicator.setText(message);
      synchronized (myIndexingLock) {
        while (myShouldWait.get()) {
          try {
            myIndexingLock.wait(INDEXING_WAIT_TIMEOUT_MILLIS);
          }
          catch (InterruptedException ignored) {
          }
        }
      }
      LOG.info(String.format("Indexing released (context: %1$s)", myContextDescription));
    }
  }
}
