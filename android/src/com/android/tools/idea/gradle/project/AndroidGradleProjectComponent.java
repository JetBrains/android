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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.GradleProjectImporter;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.intellij.ProjectTopics;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.options.ExternalBuildOptionListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectComponent.class);

  private Disposable myDisposable;

  public AndroidGradleProjectComponent(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    MessageBusConnection connection = myProject.getMessageBus().connect(myDisposable);
    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleAdded(Project project, Module module) {
        updateBuildVariantView();
      }

      @Override
      public void moduleRemoved(Project project, Module module) {
        updateBuildVariantView();
      }

      private void updateBuildVariantView() {
        BuildVariantView.getInstance(myProject).updateContents();
      }
    });

    if (Projects.isGradleProject(myProject)) {
      GradleImportNotificationListener.attachToManager();
      Projects.setProjectBuildAction(myProject, Projects.BuildAction.COMPILE);
      Projects.ensureExternalBuildIsEnabledForGradleProject(myProject);
    }
    else {
      CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
      boolean wasUsingExternalMake = workspaceConfiguration.USE_COMPILE_SERVER;
      if (wasUsingExternalMake) {
        String format = "Disabled 'External Build' for non-Gradle Android project '%1$s' until issue '%2$s' is fixed.";
        String msg = String.format(format, myProject.getName(), "https://code.google.com/p/android/issues/detail?id=56843");
        LOG.info(msg);
        workspaceConfiguration.USE_COMPILE_SERVER = false;
        MessageBus messageBus = myProject.getMessageBus();
        messageBus.syncPublisher(ExternalBuildOptionListener.TOPIC).externalBuildOptionChanged(workspaceConfiguration.USE_COMPILE_SERVER);
      }
    }

    GradleProjectImporter.getInstance().reImportProject(myProject);
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }
}
