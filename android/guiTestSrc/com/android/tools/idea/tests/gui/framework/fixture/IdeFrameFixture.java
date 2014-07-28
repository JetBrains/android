/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.gradle.project.GradleBuildListener;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.messages.MessageBusConnection;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.tools.idea.gradle.GradleSyncState.GRADLE_SYNC_TOPIC;
import static com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor.GRADLE_BUILD_TOPIC;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.tests.gui.framework.GuiTestConstants.LONG_TIMEOUT;
import static junit.framework.Assert.assertNotNull;
import static org.fest.swing.timing.Pause.pause;

public class IdeFrameFixture extends ComponentFixture<IdeFrameImpl> {
  @NotNull
  public static IdeFrameFixture find(@NotNull Robot robot, @NotNull final String projectName, @NotNull final File projectPath) {
    IdeFrameImpl ideFrame = robot.finder().find(new GenericTypeMatcher<IdeFrameImpl>(IdeFrameImpl.class) {
      @Override
      protected boolean isMatching(IdeFrameImpl frame) {
        Project project = frame.getProject();
        return project != null && projectPath.getPath().equals(project.getBasePath()) && projectName.equals(project.getName());
      }
    });
    return new IdeFrameFixture(robot, ideFrame);
  }

  public IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target) {
    super(robot, target);
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectToBeOpened() {
    Project project = getProject();
    Disposable disposable = new NoOpDisposable();

    final ProjectSyncListener listener = new ProjectSyncListener();
    try {
      MessageBusConnection connection = project.getMessageBus().connect(disposable);
      connection.subscribe(GRADLE_SYNC_TOPIC, listener);

      pause(new Condition("'Sync project \"" + project.getName() + "\"'") {
        @Override
        public boolean test() {
          return listener.mySyncFinished;
        }
      }, LONG_TIMEOUT);

      if (listener.mySyncError != null) {
        throw listener.mySyncError;
      }
    }
    finally {
      Disposer.dispose(disposable);
    }

    if (!listener.mySyncWasSkipped) {
      waitForSourceGenerationToFinish();
    }

    return waitForBackgroundTasksToFinish();
  }

  private void waitForSourceGenerationToFinish() {
    Project project = getProject();
    Disposable disposable = new NoOpDisposable();

    try {
      MessageBusConnection connection = project.getMessageBus().connect(disposable);
      final ProjectBuildListener listener = new ProjectBuildListener(SOURCE_GEN);
      connection.subscribe(GRADLE_BUILD_TOPIC, listener);

      pause(new Condition("'Source generation for project \"" + project.getName() + "\"'") {
        @Override
        public boolean test() {
          return listener.myBuildFinished;
        }
      }, LONG_TIMEOUT);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  @NotNull
  public IdeFrameFixture waitForBackgroundTasksToFinish() {
    pause(new Condition("'Background tasks to finish'") {
      @Override
      public boolean test() {
        ProgressManager progressManager = ProgressManager.getInstance();
        return !progressManager.hasModalProgressIndicator() &&
               !progressManager.hasProgressIndicator() &&
               !progressManager.hasUnsafeProgressIndicator();
      }
    }, LONG_TIMEOUT);
    return this;
  }

  @NotNull
  public Project getProject() {
    Project project = target.getProject();
    assertNotNull(project);
    return project;
  }

  public void close() {
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ProjectManager.getInstance().closeProject(getProject());
      }
    });
  }

  private static class ProjectSyncListener extends GradleSyncListener.Adapter {
    AssertionError mySyncError;
    boolean mySyncFinished;
    boolean mySyncWasSkipped;

    @Override
    public void syncSucceeded(@NotNull Project project) {
      mySyncFinished = true;
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
      mySyncError = new AssertionError("Project sync for \"" + project.getName() + "\" failed: " + errorMessage);
      mySyncFinished = true;
    }

    @Override
    public void syncSkipped(@NotNull Project project) {
      mySyncFinished = true;
      mySyncWasSkipped = true;
    }
  }

  private static class ProjectBuildListener implements GradleBuildListener {
    @NotNull private final BuildMode myExpectedBuildMode;

    boolean myBuildFinished;

    ProjectBuildListener(@NotNull BuildMode expectedBuildMode) {
      myExpectedBuildMode = expectedBuildMode;
    }

    @Override
    public void buildFinished(@NotNull Project project, @Nullable BuildMode mode) {
      if (myExpectedBuildMode == mode) {
        myBuildFinished = true;
      }
    }
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }
}
