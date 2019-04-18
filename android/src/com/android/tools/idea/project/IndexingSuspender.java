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

import static com.android.tools.idea.gradle.util.BatchUpdatesUtil.finishBatchUpdate;
import static com.android.tools.idea.gradle.util.BatchUpdatesUtil.startBatchUpdate;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.gradle.project.build.GradleBuildListener;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.npw.model.MultiTemplateRenderer;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The project-level service to prevent IDE indexing from being started until a certain set of conditions is met.
 */
public class IndexingSuspender {
  private static final Logger LOG = Logger.getInstance(IndexingSuspender.class);

  private boolean myTestingIndexingSuspender;

  @NotNull private final Project myProject;

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
    // The switch below represents a state machine, so check very carefully all cases whenever changing one, as there
    // may be logical dependencies. The two intended work flows to cover are:
    //
    // 1) Sync -> Build
    // 2) Template Rendering -> Sync -> Build
    //
    // In both cases the objective is to suspend indexing in one batch, however in cases when project initialisation
    // is part of the workflow, we have to resume indexing and split the suspension into two batches, or otherwise
    // there is a risk of IDE deadlock when project initialisation can't proceed without initial refresh and the latter
    // can't proceed with indexing being suspended.

    LOG.info("Consuming IndexingSuspender activation event: " + event.toString());
    switch (event) {
      case SYNC_STARTED:
        if (!myProject.isInitialized()) {
          if (myActivated) {
            // Always mandatory to resume indexing during project initialisation, otherwise risking IDE deadlock
            // during the initial refresh.
            ensureDeactivated();
          }
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
          if (!myActivated) {
            activate(ActivationEvent.SYNC_STARTED, DeactivationEvent.SYNC_FINISHED);
          }
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
        break;
      case BUILD_EXECUTOR_CREATED:
        if (myDeactivationEvent == DeactivationEvent.SYNC_FINISHED) {
          myDeactivationEvent = DeactivationEvent.BUILD_FINISHED;
        }
        break;
      case BUILD_STARTED:
        if (!myActivated) {
          activate(ActivationEvent.BUILD_STARTED, DeactivationEvent.BUILD_FINISHED);
        }
        break;
      case TEMPLATE_RENDERING_STARTED:
        if (!myActivated) {
          activate(ActivationEvent.TEMPLATE_RENDERING_STARTED, DeactivationEvent.TEMPLATE_RENDERING_FINISHED);
        }
        break;
      case SYNC_TASK_CREATED:
        if (myDeactivationEvent == DeactivationEvent.TEMPLATE_RENDERING_FINISHED) {
          myDeactivationEvent = DeactivationEvent.SYNC_FINISHED;
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
      public void syncTaskCreated(@NotNull Project project, @NotNull GradleSyncInvoker.Request request) {
        consumeActivationEvent(ActivationEvent.SYNC_TASK_CREATED);
      }

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

    MultiTemplateRenderer.subscribe(myProject, new MultiTemplateRenderer.TemplateRendererListener() {
      @Override
      public void multiRenderingStarted() {
        consumeActivationEvent(ActivationEvent.TEMPLATE_RENDERING_STARTED);
      }

      @Override
      public void multiRenderingFinished() {
        consumeDeactivationEvent(DeactivationEvent.TEMPLATE_RENDERING_FINISHED);
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

    // Allow indexing suspension in case of template rendering regardless of project initialisation state:
    // - if it's triggered during New Project workflow, then suspension will have a wider scope than just gradle sync,
    // so it will not interfere.
    // - if it's triggered during other NPW work flows, then project is already initialised anyway and there is no concern.
    if (activationEvent != ActivationEvent.TEMPLATE_RENDERING_STARTED && !myProject.isInitialized()) {
      reportStateError("Attempt to suspend indexing when project is not yet initialised. Ignoring.");
      return;
    }

    myActivated = true;
    myDeactivationEvent = deactivationEvent;
    startBatchUpdate(myProject);
  }

  private void ensureDeactivated() {
    if (!canActivate() || !myActivated) {
      return;
    }

    finishBatchUpdate(myProject);
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

  private enum ActivationEvent {
    SYNC_TASK_CREATED,
    SYNC_STARTED,
    SETUP_STARTED,
    BUILD_EXECUTOR_CREATED,
    BUILD_STARTED,
    TEMPLATE_RENDERING_STARTED,
  }

  private enum DeactivationEvent {
    SYNC_FINISHED,
    BUILD_FINISHED,
    TEMPLATE_RENDERING_FINISHED,
  }
}
