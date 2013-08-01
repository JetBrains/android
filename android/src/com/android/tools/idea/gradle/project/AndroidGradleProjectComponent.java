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
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.intellij.ProjectTopics;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.options.ExternalBuildOptionListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AndroidGradleProjectComponent extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance(AndroidGradleProjectComponent.class);

  @Nullable private Disposable myDisposable;

  public AndroidGradleProjectComponent(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    if (!Projects.isGradleProject(myProject)) {
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
      return;
    }

    myDisposable = new Disposable() {
      @Override
      public void dispose() {
      }
    };

    listenForChangesInModules(myProject, myDisposable);

    GradleImportNotificationListener.attachToManager();
    Projects.setProjectBuildAction(myProject, Projects.BuildAction.SOURCE_GEN);
    Projects.ensureExternalBuildIsEnabledForGradleProject(myProject);

    try {
      // Prevent IDEA from refreshing project. We want to do it ourselves.
      myProject.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);

      GradleProjectImporter.getInstance().reImportProject(myProject);
    }
    catch (ConfigurationException e) {
      Messages.showErrorDialog(e.getMessage(), e.getTitle());
      LOG.info(e);
    }
  }

  private static void listenForChangesInModules(@NotNull Project project, @NotNull Disposable disposable) {
    MessageBusConnection connection = project.getMessageBus().connect(disposable);
    connection.subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void moduleAdded(Project project, Module module) {
        updateBuildVariantView(project);
      }

      @Override
      public void modulesRenamed(Project project, List<Module> modules) {
        updateBuildVariantView(project);
      }

      @Override
      public void moduleRemoved(Project project, Module module) {
        updateBuildVariantView(project);
      }

      private void updateBuildVariantView(@NotNull Project project) {
        BuildVariantView.getInstance(project).updateContents();
      }
    });
    connection = project.getMessageBus().connect(disposable);
    connection.subscribe(ProjectTopics.MODULES, new GradleBuildFileUpdater(project));
  }

  @Override
  public void projectClosed() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }
}
