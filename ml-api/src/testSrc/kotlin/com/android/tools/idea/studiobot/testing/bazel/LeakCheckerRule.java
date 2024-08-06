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
package com.android.tools.idea.studiobot.testing.bazel;

import com.android.testutils.MockitoThreadLocalsCleaner;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexEx;
import com.intellij.testFramework.TestApplicationManager;
import org.junit.rules.ExternalResource;

/**
 * Copied from com.android.tools.tests, to make LastInAipluginTestSuite work
 */
public class LeakCheckerRule extends ExternalResource {

  @Override
  protected void after() {
    checkForLeaks();
  }

  public static void checkForLeaks() {
    Application app = ApplicationManager.getApplication();
    if (app == null || app.isDisposed()) {
      // If the app was already disposed, then the leak checker does not work properly.
      // This can happen when multiple LeakCheckerRules are "nested", and the inner LeakCheckerRule has already run.
      return;
    }
    ensureFileUpdatesProcessedByModificationTracker();
    clearMockitoThreadLocals();
    TestApplicationManager.disposeApplicationAndCheckForLeaks();
  }

  /**
   * {@code PerFileElementTypeStubModificationTracker} keeps a queue of file operations, items of this
   * queue contain references to the Project. Normally this queue is processed reasonably fast and items for disposed projects are just
   * skipped.
   * However, in test, if there are heavy file operations and not enough time to process them in the end it can happen that leak  checker
   * runs before all the file updates are processed resulting in a disposed project leak detection.
   * We need to force-run this queue processing before running leak check to avoid this problem.
   */
  private static void ensureFileUpdatesProcessedByModificationTracker() {
    StubIndex stubIndex = StubIndex.getInstance();
    if (stubIndex instanceof StubIndexEx) {
      ((StubIndexEx) stubIndex).getPerFileElementTypeModificationTrackerUpdateProcessor().endUpdatesBatch();
    }
  }

  private static void clearMockitoThreadLocals() {
    // Note: just before LeakHunter runs, IntelliJ will close all open projects — including projects
    // from "light" test fixtures which tend to remain open for potential reuse. Unfortunately, project
    // disposal can lead to new Mockito interactions. For example, if a mock was registered as a child
    // disposable of a test project, then disposing the project will invoke 'dispose' on the mock,
    // and this will repopulate Mockito's thread-local state (ThreadSafeMockingProgress). So, in order
    // to clear Mockito state _after_ all Mockito interactions have finished, we do it inside the
    // 'appWillBeClosed' callback.
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        try {
          new MockitoThreadLocalsCleaner().cleanupAndTearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
