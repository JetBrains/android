// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.dsl.UpToDateChecker;
import com.intellij.openapi.project.Project;

public class GradleDslUpToDateChecker implements UpToDateChecker {

  public static class GradleDslUpToDateCheckerService{
    private final Project myProject;
    private long myModelSyncTime = -1L;

    public GradleDslUpToDateCheckerService(Project project) {myProject = project;}

    public boolean isUpToDate() {
      long lastKnownSyncTime = GradleSyncState.getInstance(myProject).getLastSyncFinishedTimeStamp();
      return !GradleFiles.getInstance(myProject).areGradleFilesModified() && myModelSyncTime == lastKnownSyncTime;
    }

    public void setModelUpToDate() {
      myModelSyncTime = GradleSyncState.getInstance(myProject).getLastSyncFinishedTimeStamp();
    }
  }

  @Override
  public boolean checkUpToDate(Project project) {
    return project.getService(GradleDslUpToDateCheckerService.class).isUpToDate();
  }

  @Override
  public void setUpToDate(Project project) {
    project.getService(GradleDslUpToDateCheckerService.class).setModelUpToDate();
  }
}

