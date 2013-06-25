/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.startup;

import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.options.ExternalBuildOptionListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

/**
 * Sets up any Gradle-related state when the IDE starts.
 */
public class GradleStartupActivity implements StartupActivity, DumbAware {
  private static final Logger LOG = Logger.getInstance(GradleStartupActivity.class);

  @Override
  public void runActivity(Project project) {
    if (project != null) {
      if (Projects.isGradleProject(project)) {
        GradleImportNotificationListener.attachToManager();
        Projects.setBuildAction(project, Projects.BuildAction.COMPILE);
        CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
        boolean wasUsingExternalMake = workspaceConfiguration.USE_COMPILE_SERVER;
        if (!wasUsingExternalMake) {
          String format = "Enabled 'External Build' for Android project '%1$s'. Otherwise, the project will not be built with Gradle";
          String msg = String.format(format, project.getName());
          LOG.info(msg);
          workspaceConfiguration.USE_COMPILE_SERVER = true;
          notifyBuildOptionChanged(project, workspaceConfiguration);
        }
      }
      else {
        CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
        boolean wasUsingExternalMake = workspaceConfiguration.USE_COMPILE_SERVER;
        if (wasUsingExternalMake) {
          String format = "Disabled 'External Build' for non-Gradle Android project '%1$s' until issue '%2$s' is fixed.";
          String msg = String.format(format, project.getName(), "https://code.google.com/p/android/issues/detail?id=56843");
          LOG.info(msg);
          workspaceConfiguration.USE_COMPILE_SERVER = false;
          notifyBuildOptionChanged(project, workspaceConfiguration);
        }
      }
    }
  }

  private static void notifyBuildOptionChanged(@NotNull Project project, @NotNull CompilerWorkspaceConfiguration workspaceConfiguration) {
    MessageBus messageBus = project.getMessageBus();
    messageBus.syncPublisher(ExternalBuildOptionListener.TOPIC).externalBuildOptionChanged(workspaceConfiguration.USE_COMPILE_SERVER);
  }
}
