/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class GradleSyncSummary {
  @NotNull private final Project myProject;

  /**
   * The version of Gradle used by the project.
   */
  @Nullable private GradleVersion myGradleVersion;
  private long mySyncTimestamp;
  private boolean mySyncErrorsFound;
  private boolean myWrongJdkFound;

  public GradleSyncSummary(@NotNull Project project) {
    myProject = project;
    reset();
  }

  @Nullable
  public GradleVersion getGradleVersion() {
    return myGradleVersion;
  }

  public void setGradleVersion(@NotNull GradleVersion gradleVersion) {
    myGradleVersion = gradleVersion;
  }

  public long getSyncTimestamp() {
    return mySyncTimestamp;
  }

  void setSyncTimestamp(long syncTimestamp) {
    mySyncTimestamp = syncTimestamp;
  }

  public void setSyncErrorsFound(boolean syncErrorsFound) {
    mySyncErrorsFound = syncErrorsFound;
  }

  public void setWrongJdkFound(boolean wrongJdkFound) {
    myWrongJdkFound = wrongJdkFound;
  }

  public boolean hasSyncErrors() {
    if (mySyncErrorsFound || myWrongJdkFound) {
      return true;
    }
    GradleSyncMessages messages = GradleSyncMessages.getInstance(myProject);
    return messages.getErrorCount() > 0;
  }

  void reset() {
    myGradleVersion = null;
    mySyncTimestamp = -1;
    mySyncErrorsFound = false;
    myWrongJdkFound = false;
  }
}
