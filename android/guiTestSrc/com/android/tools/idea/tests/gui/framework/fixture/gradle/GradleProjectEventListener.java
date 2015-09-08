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

import com.android.tools.idea.gradle.project.GradleBuildListener;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleProjectEventListener extends GradleSyncListener.Adapter implements GradleBuildListener {
  private boolean mySyncStarted;
  private boolean mySyncFinished;
  private boolean mySyncSkipped;
  @Nullable private RuntimeException mySyncError;

  private BuildMode myBuildMode;
  private boolean myBuildFinished;

  private final Object lock = new Object();

  @Override
  public void syncStarted(@NotNull Project project) {
    reset();
    synchronized (lock) {
      mySyncStarted = true;
    }
  }

  @Override
  public void syncSucceeded(@NotNull Project project) {
    synchronized (lock) {
      mySyncStarted = false;
      mySyncFinished = true;
    }
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
    synchronized (lock) {
      mySyncStarted = false;
      mySyncFinished = true;
      mySyncError = new RuntimeException(errorMessage);
    }
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
    synchronized (lock) {
      mySyncStarted = false;
      mySyncSkipped = mySyncFinished = true;
    }
  }

  @Override
  public void buildFinished(@NotNull Project project, @Nullable BuildMode mode) {
    synchronized (lock) {
      myBuildMode = mode;
      myBuildFinished = true;
    }
  }

  public void reset() {
    synchronized (lock) {
      mySyncError = null;
      mySyncStarted = mySyncSkipped = mySyncFinished = false;
      myBuildMode = null;
      myBuildFinished = false;
    }
  }

  public boolean isSyncStarted() {
    synchronized (lock) {
      return mySyncStarted;
    }
  }

  public boolean isSyncFinished() {
    synchronized (lock) {
      return mySyncFinished;
    }
  }

  public boolean isSyncSkipped() {
    synchronized (lock) {
      return mySyncSkipped;
    }
  }

  public boolean hasSyncError() {
    synchronized (lock) {
      return mySyncError != null;
    }
  }

  @Nullable
  public RuntimeException getSyncError() {
    synchronized (lock) {
      return mySyncError;
    }
  }

  public boolean isBuildFinished(@NotNull BuildMode mode) {
    synchronized (lock) {
      return myBuildFinished && myBuildMode == mode;
    }
  }
}
