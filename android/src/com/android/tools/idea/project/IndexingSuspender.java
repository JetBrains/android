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

import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.gradle.project.build.GradleBuildListener;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.BatchUpdateListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction;


/**
 * The project-level service to prevent IDE indexing from being started until a certain set of conditions is met.
 */
public class IndexingSuspender {
  private static final Logger LOG = Logger.getInstance(IndexingSuspender.class);

  @VisibleForTesting
  static final int INDEXING_WAIT_TIMEOUT_MILLIS = 5000;

  private boolean myTestingIndexingSuspender;

  @NotNull private final Project myProject;
  @NotNull private final Object myIndexingLock = new Object();
  @GuardedBy("myIndexingLock")
  private boolean myShouldWait;

  private boolean myScheduledAfterProjectInitialisation;
  @Nullable private DeactivationEvent myDeactivationEvent;
  private volatile boolean myActivated;

  public static void ensureInitialised(@NotNull Project project) {
    ServiceManager.getService(project, IndexingSuspender.class);
  }

  // This is a service constructor and is called implicitly by the platform's service management core.
  @SuppressWarnings("unused")
  public IndexingSuspender(@NotNull Project project) {
    this(project, false);
  }

  @VisibleForTesting
  IndexingSuspender(@NotNull Project project, boolean testing) {
    myTestingIndexingSuspender = testing;
    myProject = project;

    if (!canActivate()) {
      // Don't even perform subscriptions if can't activate. The activation conditions for this service are expected to be
      // invariant during the Application instance lifetime.
      return;
    }

    subscribeToSyncAndBuildEvents();
  }

  private void consumeActivationEvent(@NotNull ActivationEvent event) {
    LOG.info("Consuming IndexingSuspender activation event: " + event.toString());
    switch (event) {
      case SYNC_STARTED:
        if (!myProject.isInitialized()) {
          myScheduledAfterProjectInitialisation = true;
          StartupManager.getInstance(myProject).runWhenProjectIsInitialized(
            () -> {
              if (myScheduledAfterProjectInitialisation) {
                // This is expected to be executed on project setup once the project is initialised.
                activate(ActivationEvent.SYNC_STARTED, DeactivationEvent.SYNC_FINISHED);
                myScheduledAfterProjectInitialisation = false;
              }
            }
          );
        }
        else {
          activate(ActivationEvent.SYNC_STARTED, DeactivationEvent.SYNC_FINISHED);
        }
        break;
      case SETUP_STARTED:
        if (!myProject.isInitialized()) {
          clearStateConditions();
          reportStateError("Project is expected to be initialised before project setup starts.");
          break;
        }
        if (!myActivated) {
          activate(ActivationEvent.SETUP_STARTED, DeactivationEvent.SYNC_FINISHED);
        }
        // The sentinel dumb mode will be ended either when build starts or when suspender gets deactivated with a
        // DeactivationEvent
        startSentinelDumbMode("Project Setup");
        break;
      case BUILD_EXECUTOR_CREATED:
        if (myDeactivationEvent == DeactivationEvent.SYNC_FINISHED) {
          myDeactivationEvent = DeactivationEvent.BUILD_FINISHED;
        }
        break;
      case BUILD_STARTED:
        ensureNoSentinelDumbMode();
        if (!myActivated) {
          activate(ActivationEvent.BUILD_STARTED, DeactivationEvent.BUILD_FINISHED);
        }
        break;
    }
  }

  private void consumeDeactivationEvent(@NotNull DeactivationEvent event) {
    LOG.info("Consuming IndexingSuspender deactivation event: " + event.toString());
    // The guard below is against the situation when project initialisation completes after gradle sync.
    // In practice this won't happen, but for the sanity reasons it makes sense to render the deferred indexing suspender
    // activation into a no-op, if there is any.
    myScheduledAfterProjectInitialisation = false;
    if (event == myDeactivationEvent) {
      ensureDeactivated();
    }
    else {
      LOG.info("IndexingSuspender deactivation event received and ignored: " + event.toString());
    }
  }

  private static void reportStateError(@NotNull String message) {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      throw new IllegalStateException(message);
    }
    else {
      LOG.warn(message);
    }
  }

  private void subscribeToSyncAndBuildEvents() {
    LOG.info(String.format("Subscribing project '%1$s' to indexing suspender events (%2$s)", myProject.toString(), toString()));
    GradleSyncState.subscribe(myProject, new GradleSyncListener() {
      @Override
      public void syncStarted(@NotNull Project project, boolean skipped, boolean sourceGenerationRequested) {
        consumeActivationEvent(ActivationEvent.SYNC_STARTED);
      }

      @Override
      public void setupStarted(@NotNull Project project) {
        consumeActivationEvent(ActivationEvent.SETUP_STARTED);
      }

      @Override
      public void syncSucceeded(@NotNull Project project) {
        consumeDeactivationEvent(DeactivationEvent.SYNC_FINISHED);
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        consumeDeactivationEvent(DeactivationEvent.SYNC_FINISHED);
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        consumeDeactivationEvent(DeactivationEvent.SYNC_FINISHED);
      }
    });

    GradleBuildState.subscribe(myProject, new GradleBuildListener() {
      @Override
      public void buildExecutorCreated(@NotNull GradleBuildInvoker.Request request) {
        consumeActivationEvent(ActivationEvent.BUILD_EXECUTOR_CREATED);
      }

      @Override
      public void buildStarted(@NotNull BuildContext context) {
        consumeActivationEvent(ActivationEvent.BUILD_STARTED);
      }

      @Override
      public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
        consumeDeactivationEvent(DeactivationEvent.BUILD_FINISHED);
      }
    });
  }

  private void activate(@NotNull ActivationEvent activationEvent, @NotNull DeactivationEvent deactivationEvent) {
    if (myActivated) {
      reportStateError("Must not attempt to activate IndexingSuspender when it is already activated. Ignored.");
      return;
    }

    if (!canActivate()) {
      LOG.info(String.format("Indexing suspension not activated (context: '%1$s')", activationEvent.name()));
      return;
    }

    if (!myProject.isInitialized()) {
      reportStateError("Attempt to suspend indexing when project is not yet initialised. Ignoring.");
      return;
    }

    myActivated = true;
    myDeactivationEvent = deactivationEvent;
    startBatchUpdate();
  }

  private void ensureDeactivated() {
    if (!canActivate() || !myActivated) {
      return;
    }

    finishBatchUpdate();
    ensureNoSentinelDumbMode();
    clearStateConditions();
    myActivated = false;
  }

  private boolean canActivate() {
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      // IntelliJ won't necessarily have the same events workflow related to Gradle build & sync, so render IndexingSuspender
      // a no-op in that case.
      return false;
    }

    if (!StudioFlags.GRADLE_INVOCATIONS_INDEXING_AWARE.get()) {
      // Do not perform any subscriptions.
      // Since the service is designed to be event-driven only, it will just do nothing without
      // receiving project messages.
      return false;
    }

    Application application = ApplicationManager.getApplication();
    if ((application.isUnitTestMode() || application.isHeadlessEnvironment()) && !myTestingIndexingSuspender) {
      // Do nothing if in a unit test not dedicated to this service.
      // In a headless environment, don't suspend indexing either, since in that case DumbServiceImpl
      // executes indexing tasks synchronously anyway.
      return false;
    }

    return true;
  }

  private void clearStateConditions() {
    myDeactivationEvent = null;
  }

  /**
   * Start batch update, thus ensuring that any VFS/root model changes do not immediately cause associated indexing. This
   * helps avoid redundant indexing operations and contention during a massive model update (for example,
   * the one happenning during the project setup phase of gradle sync).
   */
  private void startBatchUpdate() {
    LOG.info("Starting batch update for project: " + myProject.toString());
    executeProjectChangeAction(true, new DisposeAwareProjectChange(myProject) {
      @Override
      public void execute() {
        myProject.getMessageBus().syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted();
      }
    });
  }

  /**
   * Finish batch update, thus allowing any model changes which happened along the way to trigger indexing.
   */
  private void finishBatchUpdate() {
    LOG.info("Finishing batch update for project: " + myProject.toString());
    executeProjectChangeAction(true, new DisposeAwareProjectChange(myProject) {
      @Override
      public void execute() {
        myProject.getMessageBus().syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateFinished();
      }
    });
  }

  /**
   * Queue IndexingSuspenderTask, thus ensuring that sentinel dumb mode is entered.
   *
   * Note: In unit test mode and when {@link #myTestingIndexingSuspender} is true, the project's DumbService must be prepared
   * accordingly before the control flow reaches this method. Namely, it should not execute the queued task straight away on the event
   * dispatch thread like the default DumbServiceImpl does in unit test mode, because otherwise the test will deadlock.
   * A sensible way to achieve that would be to mock DumbService in advance somewhere in the test setUp() method, using the corresponding
   * facilities in com.android.tools.idea.testing.IdeComponents.
   *
   * @param contextDescription Human-readable description of the context where the sentinel dumb mode was requested.
   * @see #canActivate()
   */
  @SuppressWarnings("SameParameterValue")
  private void startSentinelDumbMode(@NotNull String contextDescription) {
    synchronized (myIndexingLock) {
      myShouldWait = true;
    }
    DumbService.getInstance(myProject).queueTask(new IndexingSuspenderTask(contextDescription));
  }

  /**
   * If there is a sentinel dumb mode in progress (the one caused by IndexingSuspenderTask), this call will unblock
   * IndexingSuspenderTask and hence release the sentinel dumb mode.
   *
   * If there is no sentinel dumb mode in progress, this call will be a no-op.
   */
  private void ensureNoSentinelDumbMode() {
    synchronized (myIndexingLock) {
      myShouldWait = false;
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
  @VisibleForTesting
  class IndexingSuspenderTask extends DumbModeTask {
    @NotNull private final String myContextDescription;

    IndexingSuspenderTask(@NotNull String contextDescription) {
      myContextDescription = contextDescription;
    }

    @Override
    public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      String message = String.format("Indexing suspended (context: %1$s)", myContextDescription);
      LOG.info(message);
      indicator.setText(message);
      synchronized (myIndexingLock) {
        while (myShouldWait) {
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

  private enum ActivationEvent {
    SYNC_STARTED,
    SETUP_STARTED,
    BUILD_EXECUTOR_CREATED,
    BUILD_STARTED,
  }

  private enum DeactivationEvent {
    SYNC_FINISHED,
    BUILD_FINISHED,
  }
}
