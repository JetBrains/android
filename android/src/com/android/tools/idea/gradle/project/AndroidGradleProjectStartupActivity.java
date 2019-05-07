/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_NEW;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Syncs Android Gradle project with the persisted project data on startup.
 */
public class AndroidGradleProjectStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(project);
    if ((
      // We only request sync if we know this is an Android project.

      // Opening an IDEA project with Android modules (AS and IDEA - i.e. previously synced).
      !gradleProjectInfo.getAndroidModules().isEmpty()
      // Opening a Gradle project with .idea but no .iml files or facets (Typical for AS but not in IDEA)
      || IdeInfo.getInstance().isAndroidStudio() && gradleProjectInfo.isBuildWithGradle()
      // Opening a project without .idea directory (including a newly created).
      || gradleProjectInfo.isImportedProject()
        ) &&
        !gradleProjectInfo.isSkipStartupActivity()) {

      GradleSyncStats.Trigger trigger =
        gradleProjectInfo.isNewProject() ? TRIGGER_PROJECT_NEW : TRIGGER_PROJECT_REOPEN;
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(trigger);
      request.useCachedGradleModels = true;

      GradleSyncInvoker.getInstance().requestProjectSync(project, request);
    }
    gradleProjectInfo.setSkipStartupActivity(false);
  }
}
