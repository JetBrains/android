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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.gradle.project.build.BuildContext;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.gradle.project.build.GradleBuildListener;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;

public class GradleProjectEventListener extends GradleSyncListener.Adapter implements GradleBuildListener {
  private boolean mySyncStarted;
  private boolean mySyncFinished;
  private boolean mySyncSkipped;
  @Nullable private RuntimeException mySyncError;

  @GuardedBy("myLock")
  private BuildMode myBuildMode;

  @GuardedBy("myLock")
  private boolean myBuildFinished;

  private final Object myLock = new Object();

  @Override
  public void syncStarted(@NotNull Project project, boolean skipped) {
    reset();
    synchronized (myLock) {
      mySyncStarted = true;
    }
  }

  @Override
  public void syncSucceeded(@NotNull Project project) {
    synchronized (myLock) {
      mySyncStarted = false;
      mySyncFinished = true;
    }
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    synchronized (myLock) {
      mySyncStarted = false;
      mySyncFinished = true;
      mySyncError = new RuntimeException(errorMessage);
    }
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
    synchronized (myLock) {
      mySyncStarted = false;
      mySyncSkipped = mySyncFinished = true;
    }
  }

  @Override
  public void buildExecutorCreated(@NotNull GradleBuildInvoker.Request request) {
  }

  @Override
  public void buildStarted(@NotNull BuildContext context) {
  }

  @Override
  public void buildFinished(@NotNull BuildStatus status, @Nullable BuildContext context) {
    if (status == BuildStatus.SUCCESS) {
      synchronized (myLock) {
        myBuildMode = context != null ? context.getBuildMode() : null;
        myBuildFinished = true;
      }
    }
  }

  public void reset() {
    synchronized (myLock) {
      mySyncError = null;
      mySyncStarted = mySyncSkipped = mySyncFinished = false;
      myBuildMode = null;
      myBuildFinished = false;
    }
  }

  public boolean isSyncStarted() {
    synchronized (myLock) {
      return mySyncStarted;
    }
  }

  public boolean isSyncFinished() {
    synchronized (myLock) {
      return mySyncFinished;
    }
  }

  public boolean isSyncSkipped() {
    synchronized (myLock) {
      return mySyncSkipped;
    }
  }

  public boolean hasSyncError() {
    synchronized (myLock) {
      return mySyncError != null;
    }
  }

  @Nullable
  public RuntimeException getSyncError() {
    synchronized (myLock) {
      return mySyncError;
    }
  }

  public boolean isBuildFinished(@NotNull BuildMode mode) {
    synchronized (myLock) {
      return myBuildFinished && myBuildMode == mode;
    }
  }
}
