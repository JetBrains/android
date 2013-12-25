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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.*;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemIdeNotificationManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidProjectDataService implements ProjectDataService<IdeaAndroidProject, Void> {
  private final ModuleCustomizer[] myCustomizers;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  public AndroidProjectDataService() {
    //noinspection TestOnlyProblems
    this(new AndroidSdkModuleCustomizer(), new AndroidFacetModuleCustomizer(), new RunConfigModuleCustomizer(),
         new CompilerOutputPathModuleCustomizer());
  }

  @VisibleForTesting
  AndroidProjectDataService(@NotNull ModuleCustomizer... customizers) {
    myCustomizers = customizers;
  }

  @NotNull
  @Override
  public Key<IdeaAndroidProject> getTargetDataKey() {
    return AndroidProjectKeys.IDE_ANDROID_PROJECT;
  }

  /**
   * Sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
   *
   * @param toImport    contains the Android-Gradle project.
   * @param project     IDEA project to configure.
   * @param synchronous indicates whether this operation is synchronous.
   */
  @Override
  public void importData(@NotNull final Collection<DataNode<IdeaAndroidProject>> toImport,
                         @NotNull final Project project,
                         boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }

    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        LanguageLevel javaLangVersion = null;

        Map<String, IdeaAndroidProject> androidProjectsByModuleName = indexByModuleName(toImport);
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          IdeaAndroidProject androidProject = androidProjectsByModuleName.get(module.getName());
          customizeModule(module, project, androidProject);
          if (androidProject != null && javaLangVersion == null) {
            javaLangVersion = androidProject.getJavaLanguageLevel();
          }
        }

        Sdk jdk = Jdks.chooseOrCreateJavaSdk(javaLangVersion);
        if (jdk == null) {
          ExternalSystemIdeNotificationManager notification = ServiceManager.getService(ExternalSystemIdeNotificationManager.class);
          if (notification != null) {
            String title = String.format("Problems importing/refreshing Gradle project '%1$s':\n", project.getName());
            LanguageLevel level = javaLangVersion != null ? javaLangVersion : LanguageLevel.JDK_1_6;
            String msg = String.format("Unable to find a JDK %1$s installed.\n", level.getPresentableText());
            msg += "After configuring a suitable JDK in the Project Structure dialog, sync the Gradle project again.";
            notification.showNotification(title, msg, NotificationType.ERROR, project, GradleConstants.SYSTEM_ID, null);
          }
        }
        else {
          String homePath = jdk.getHomePath();
          if (homePath != null) {
            NewProjectUtil.applyJdkToProject(project, jdk);
            homePath = FileUtil.toSystemDependentName(homePath);
            DefaultSdks.setDefaultJavaHome(new File(homePath));
            PostProjectBuildTasksExecutor.getInstance(project).updateJavaLangLevelAfterBuild();
          }
        }
      }
    });
  }

  @NotNull
  private static Map<String, IdeaAndroidProject> indexByModuleName(@NotNull Collection<DataNode<IdeaAndroidProject>> dataNodes) {
    Map<String, IdeaAndroidProject> index = Maps.newHashMap();
    for (DataNode<IdeaAndroidProject> d : dataNodes) {
      IdeaAndroidProject androidProject = d.getData();
      index.put(androidProject.getModuleName(), androidProject);
    }
    return index;
  }

  private void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    for (ModuleCustomizer customizer : myCustomizers) {
      customizer.customizeModule(module, project, ideaAndroidProject);
    }
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
