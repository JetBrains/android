/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.android.tools.idea.gradle.project.sync.setup.post.ProjectSetupStep;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.project.AndroidKtsSupportNotification;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleKtsBuildFilesWarningStep extends ProjectSetupStep {

  @NotNull
  public static final Key<Boolean> HAS_KTS_BUILD_FILES = new Key<>("gradle.has.kts.files");

  @Override
  public void setUpProject(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    doSetUpProject(project, GradleUtil.hasKtsBuildFiles(project));
  }

  @VisibleForTesting
  void doSetUpProject(@NotNull Project project, boolean hasKts) {
    project.putUserData(HAS_KTS_BUILD_FILES, hasKts);
    if (hasKts) {
      AndroidKtsSupportNotification.getInstance(project).showWarningIfNotShown();
    }
  }
}
