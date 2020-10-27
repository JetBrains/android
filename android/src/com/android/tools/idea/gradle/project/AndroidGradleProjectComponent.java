/*
 * Copyright (C) 2013-2020 The Android Open Source Project
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

import static com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.SelectJdkFromFileSystemHyperlink;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

// TODO: Bad class name. Don't rename it now in order to avoid complicated merge IJ<=>AOSP
public final class AndroidGradleProjectComponent {
  @NotNull private final Project myProject;
  @NotNull private final GradleProjectInfo myGradleProjectInfo;
  @NotNull private final LegacyAndroidProjects myLegacyAndroidProjects;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    AndroidGradleProjectComponent component = project.getService(AndroidGradleProjectComponent.class);
    assert component != null;
    return component;
  }

  public AndroidGradleProjectComponent(@NotNull Project project) {
    myProject = project;
    myLegacyAndroidProjects = legacyAndroidProjects;

    // FIXME-ank4: revise the method after merge (Contents of constructor AndroidGradleProjectComponent(...) was moved to AndroidGradleProjectDumbStartupActivity)
  }

  /**
   * This method is called when a project is created and when it is opened.
   */
  @Override
  public void projectOpened() {
    // FIXME-ank4: revise the method after merge (Contents of the method AndroidGradleProjectComponent#projectOpened was moved to AndroidGradleProjectStartupActivity)
    IdeInfo ideInfo = IdeInfo.getInstance();
    if (ideInfo.isAndroidStudio()) {
      ExternalSystemProjectTracker.getInstance(myProject).setAutoReloadExternalChanges(false);
    }
    AndroidProjectInfo androidProjectInfo = AndroidProjectInfo.getInstance(myProject);
    if (ideInfo.isAndroidStudio() && androidProjectInfo.isLegacyIdeaAndroidProject() && !androidProjectInfo.isApkProject()) {
      myLegacyAndroidProjects.trackProject();
      if (!myGradleProjectInfo.isBuildWithGradle()) {
        // Suggest that Android Studio users use Gradle instead of IDEA project builder.
        myLegacyAndroidProjects.showMigrateToGradleWarning();
      }
    }
    // Check if the Gradle JDK environment variable is valid
    if (ideInfo.isAndroidStudio()) {
      IdeSdks ideSdks = IdeSdks.getInstance();
      if (ideSdks.isJdkEnvVariableDefined() && !ideSdks.isJdkEnvVariableValid()) {
        String msg = JDK_LOCATION_ENV_VARIABLE_NAME + " is being ignored since it is set to an invalid JDK Location:\n"
                     + ideSdks.getEnvVariableJdkValue();
        AndroidNotification.getInstance(myProject).showBalloon("", msg, NotificationType.WARNING,
                                                               SelectJdkFromFileSystemHyperlink.create(myProject));
      }
    }
  }
}
