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

import static com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT;

import com.android.tools.idea.IdeInfo;
import com.intellij.execution.RunConfigurationProducerService;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer;
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer;

// TODO: Bad class name. Don't rename it now in order to avoid complicated merge IJ<=>AOSP
public final class AndroidGradleProjectComponent {
  @NotNull private final Project myProject;
  private boolean isConfigured = false;

  @NotNull
  public static AndroidGradleProjectComponent getInstance(@NotNull Project project) {
    AndroidGradleProjectComponent component = project.getService(AndroidGradleProjectComponent.class);
    assert component != null;
    return component;
  }

  public AndroidGradleProjectComponent(@NotNull Project project) {
    myProject = project;
  }

  public void configureGradleProject() {
    if (isConfigured) {
      return;
    }
    isConfigured = true;

    // Prevent IDEA from refreshing project. We will do it ourselves in AndroidGradleProjectStartupActivity.
    if (IdeInfo.getInstance().isAndroidStudio()) {
      myProject.putUserData(NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
    }

    List<Class<? extends RunConfigurationProducer<?>>> runConfigurationProducerTypes = new ArrayList<>();
    runConfigurationProducerTypes.add(AllInPackageGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestClassGradleConfigurationProducer.class);
    runConfigurationProducerTypes.add(TestMethodGradleConfigurationProducer.class);

    RunConfigurationProducerService runConfigurationProducerManager = RunConfigurationProducerService.getInstance(myProject);
    if (IdeInfo.getInstance().isAndroidStudio()) {
      // Make sure the gradle test configurations are ignored in this project. This will modify .idea/runConfigurations.xml
      for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
        runConfigurationProducerManager.getState().ignoredProducers.add(type.getName());
      }
    }
    else {
      // Make sure the gradle test configurations are not ignored in this project, since they already work in Android gradle projects. This
      // will modify .idea/runConfigurations.xml
      for (Class<? extends RunConfigurationProducer<?>> type : runConfigurationProducerTypes) {
        runConfigurationProducerManager.getState().ignoredProducers.remove(type.getName());
      }
    }
  }
}
