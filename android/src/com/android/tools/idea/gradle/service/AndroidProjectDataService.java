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
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.customizer.android.*;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.project.PostProjectSyncTasksExecutor;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.EXTRA_GENERATED_SOURCES;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidProjectDataService implements ProjectDataService<IdeaAndroidProject, Void> {
  private static final Logger LOG = Logger.getInstance(AndroidProjectDataService.class);

  private final List<ModuleCustomizer<IdeaAndroidProject>> myCustomizers;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  public AndroidProjectDataService() {
    //noinspection TestOnlyProblems
    this(ImmutableList.of(new AndroidSdkModuleCustomizer(), new AndroidFacetModuleCustomizer(), new ContentRootModuleCustomizer(),
                          new RunConfigModuleCustomizer(), new DependenciesModuleCustomizer(), new CompilerOutputModuleCustomizer()));
  }

  @VisibleForTesting
  AndroidProjectDataService(@NotNull List<ModuleCustomizer<IdeaAndroidProject>> customizers) {
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
  public void importData(@NotNull Collection<DataNode<IdeaAndroidProject>> toImport,
                         @NotNull Project project,
                         boolean synchronous) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project, synchronous);
      }
      catch (RuntimeException e) {
        LOG.info(String.format("Failed to set up Android modules in project '%1$s'", project.getName()), e);
        GradleSyncState.getInstance(project).syncFailed(e.getMessage());
        return;
      }
    }

    PostProjectSyncTasksExecutor.getInstance(project).onProjectSetupCompletion();
  }

  private void doImport(final Collection<DataNode<IdeaAndroidProject>> toImport, final Project project, boolean synchronous) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        LanguageLevel javaLangVersion = null;

        ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
        boolean hasExtraGeneratedFolders = false;

        Map<String, IdeaAndroidProject> androidProjectsByModuleName = indexByModuleName(toImport);
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
          IdeaAndroidProject androidProject = androidProjectsByModuleName.get(module.getName());
          customizeModule(module, project, androidProject);
          if (androidProject != null) {
            if (javaLangVersion == null) {
              javaLangVersion = androidProject.getJavaLanguageLevel();
            }

            // Warn users that there are generated source folders at the wrong location.
            File[] sourceFolders = androidProject.getExtraGeneratedSourceFolders();
            if (sourceFolders.length > 0) {
              hasExtraGeneratedFolders = true;
            }
            for (File folder : sourceFolders) {
              // Have to add a word before the path, otherwise IDEA won't show it.
              String[] text = {"Folder " + folder.getPath()};
              messages.add(new Message(EXTRA_GENERATED_SOURCES, Message.Type.WARNING, text));
            }
          }
        }

        if (hasExtraGeneratedFolders) {
          messages.add(new Message(EXTRA_GENERATED_SOURCES, Message.Type.INFO, "3rd-party Gradle plug-ins may be the cause"));
        }

        Sdk jdk = ProjectRootManager.getInstance(project).getProjectSdk();

        if (jdk == null || !Jdks.isApplicableJdk(jdk, javaLangVersion)) {
          jdk = Jdks.chooseOrCreateJavaSdk(javaLangVersion);
        }
        if (jdk == null) {
          String title = String.format("Problems importing/refreshing Gradle project '%1$s':\n", project.getName());
          LanguageLevel level = javaLangVersion != null ? javaLangVersion : LanguageLevel.JDK_1_6;
          String msg = String.format("Unable to find a JDK %1$s installed.\n", level.getPresentableText());
          msg += "After configuring a suitable JDK in the \"Project Structure\" dialog, sync the Gradle project again.";
          NotificationData notification = new NotificationData(title, msg, NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
          ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification);
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
    for (ModuleCustomizer<IdeaAndroidProject> customizer : myCustomizers) {
      customizer.customizeModule(module, project, ideaAndroidProject);
    }
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
