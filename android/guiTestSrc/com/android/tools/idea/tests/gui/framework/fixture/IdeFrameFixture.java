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

import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.util.messages.MessageBusConnection;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.gradle.GradleSyncState.GRADLE_SYNC_TOPIC;
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
  public IdeFrameFixture waitForProjectSyncToFinish() {
    Project project = getProject();
    Disposable disposable = new NoOpDisposable();

    try {
      MessageBusConnection connection = project.getMessageBus().connect(disposable);
      final ProjectSyncListener listener = new ProjectSyncListener();
      connection.subscribe(GRADLE_SYNC_TOPIC, listener);

      pause(new Condition("'Sync project \"" + project.getName() + "\"'") {
        @Override
        public boolean test() {
          return listener.mySyncEnded;
        }
      }, LONG_TIMEOUT);

      if (listener.mySyncError != null) {
        throw listener.mySyncError;
      }
    }
    finally {
      Disposer.dispose(disposable);
    }

    return this;
  }

  @NotNull
  public Project getProject() {
    Project project = target.getProject();
    assertNotNull(project);
    return project;
  }

  private static class ProjectSyncListener implements GradleSyncListener {
    AssertionError mySyncError;
    boolean mySyncEnded;

    @Override
    public void syncStarted(@NotNull Project project) {
    }

    @Override
    public void syncEnded(@NotNull Project project) {
      mySyncEnded = true;
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
      mySyncError = new AssertionError("Project sync for \"" + project.getName() + "\" failed: " + errorMessage);
      mySyncEnded = true;
    }
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }
}
